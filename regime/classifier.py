"""
Market Regime Classifier.

Classifies each bar into a regime label using features like:
- ADX (trend strength)
- Realized volatility
- Time-of-day bucket
- VIX level (India VIX)
- Day-of-expiry flag

Approach:
1. Unsupervised clustering (KMeans/HMM) on regime features → regime labels
2. Attach each discovered strategy's P&L per regime → "which regime favors which strategy"
3. Supervised classifier (XGBoost) learns regime-label → strategy-choice mapping
4. At inference time: regime classifier → strategy registry lookup → trade signal
"""

import json
from pathlib import Path
from typing import Optional, List, Dict
import pandas as pd
import numpy as np


REGIME_FEATURES = [
    "adx_14",
    "realized_vol",
    "atr_14",
    "time_of_day",        # 0-390 minute bucket
    "day_of_expiry",       # 0-6 (Mon-Fri)
    "vix",                 # India VIX level
]


class MarketRegimeClassifier:
    """
    Classifies market regimes for strategy selection.

    Training:
    1. Run KMeans on regime features to assign regime labels
    2. Label each backtest bar with its regime + P&L
    3. Build lookup: regime -> best strategy

    Inference:
    1. Extract regime features from current bar
    2. Classifier predicts regime label
    3. Lookup best strategy for that regime
    """

    def __init__(self, n_regimes: int = 5):
        self.n_regimes = n_regimes
        self.kmeans = None
        self.xgb_model = None
        self.regime_to_strategy: Dict[int, str] = {}
        self.feature_cols: List[str] = []
        self.scaler_mean = None
        self.scaler_std = None

    def fit(
        self,
        df: pd.DataFrame,
        feature_cols: List[str] = None,
    ):
        """
        Fit regime clustering on historical data.

        Args:
            df: DataFrame with regime features
            feature_cols: Columns to use as regime features
        """
        self.feature_cols = feature_cols or [c for c in REGIME_FEATURES if c in df.columns]

        if not self.feature_cols:
            raise ValueError("No regime features found in data")

        X = df[self.feature_cols].dropna().values

        # Standardize
        self.scaler_mean = X.mean(axis=0)
        self.scaler_std = X.std(axis=0) + 1e-8
        X_scaled = (X - self.scaler_mean) / self.scaler_std

        # Cluster
        try:
            from sklearn.cluster import KMeans
            self.kmeans = KMeans(n_clusters=self.n_regimes, random_state=42, n_init=10)
            self.kmeans.fit(X_scaled)
            print(f"  Fit {self.n_regimes} regimes on {len(X)} bars")
        except ImportError:
            print("  [Regime] sklearn not available; using rule-based stub")

    def predict(self, features: dict) -> int:
        """
        Predict regime label for a single bar.

        Args:
            features: dict with regime feature values

        Returns:
            Regime label (int)
        """
        if self.kmeans is None:
            return 0  # Default regime

        X = np.array([[features.get(c, 0) for c in self.feature_cols]])
        X_scaled = (X - self.scaler_mean) / self.scaler_std
        return int(self.kmeans.predict(X_scaled)[0])

    def predict_batch(self, df: pd.DataFrame) -> pd.Series:
        """Predict regime for all bars in a DataFrame."""
        if self.kmeans is None:
            return pd.Series(np.zeros(len(df)), index=df.index)

        X = df[self.feature_cols].fillna(0).values
        X_scaled = (X - self.scaler_mean) / self.scaler_std
        return pd.Series(self.kmeans.predict(X_scaled), index=df.index)

    def assign_best_strategy(
        self,
        regime_pnl_map: Dict[int, Dict[str, float]],
    ):
        """
        Given {regime: {strategy_name: total_pnl}}, pick best strategy per regime.
        """
        for regime_label, strategy_pnls in regime_pnl_map.items():
            if not strategy_pnls:
                continue
            best_strategy = max(strategy_pnls.items(), key=lambda x: x[1])[0]
            self.regime_to_strategy[regime_label] = best_strategy
            print(f"  Regime {regime_label} → {best_strategy} (P&L: ₹{strategy_pnls[best_strategy]:.0f})")

    def select_strategy(self, features: dict) -> Optional[str]:
        """Select strategy based on regime classification."""
        regime = self.predict(features)
        return self.regime_to_strategy.get(regime)

    def save(self, path: str):
        """Save classifier state to JSON (simple) or pickle (full)."""
        state = {
            "n_regimes": self.n_regimes,
            "feature_cols": self.feature_cols,
            "regime_to_strategy": {str(k): v for k, v in self.regime_to_strategy.items()},
            "scaler_mean": self.scaler_mean.tolist() if self.scaler_mean is not None else None,
            "scaler_std": self.scaler_std.tolist() if self.scaler_std is not None else None,
        }
        Path(path).write_text(json.dumps(state, indent=2))

    def load(self, path: str):
        """Load classifier state."""
        state = json.loads(Path(path).read_text())
        self.n_regimes = state["n_regimes"]
        self.feature_cols = state["feature_cols"]
        self.regime_to_strategy = {int(k): v for k, v in state["regime_to_strategy"].items()}
        if state.get("scaler_mean"):
            self.scaler_mean = np.array(state["scaler_mean"])
            self.scaler_std = np.array(state["scaler_std"])
