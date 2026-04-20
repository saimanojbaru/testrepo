"""Regime classifier — labels each bar with trending_up / trending_down / range / high_vol.

Trained via supervised learning: labels come from rule-based heuristics on
forward-looking features during backtesting (ADX + realized vol + return sign),
then a gradient-boosted classifier learns to predict them from leading features
(price structure, microstructure, time-of-day).

The classifier interface is a thin wrapper so xgboost/lightgbm can swap in
without callers changing. Default is sklearn's HistGradientBoosting — batteries
included, fast, no native deps.
"""
from __future__ import annotations

import pickle
from dataclasses import dataclass
from enum import Enum
from pathlib import Path
from typing import Iterable

import numpy as np
import pandas as pd
from sklearn.ensemble import HistGradientBoostingClassifier
from sklearn.model_selection import TimeSeriesSplit

from features.regime import realized_vol, trend_strength, vol_regime

REGIME_MODEL_PATH = Path("regime_model.pkl")
# Minimum validation accuracy to accept a trained model. Discovery rejects weak classifiers.
MIN_VALIDATION_ACCURACY = 0.55


class RegimeLabel(str, Enum):
    TRENDING_UP = "trending_up"
    TRENDING_DOWN = "trending_down"
    RANGE = "range"
    HIGH_VOL = "high_vol"


LABEL_ORDER = [RegimeLabel.TRENDING_UP, RegimeLabel.TRENDING_DOWN, RegimeLabel.RANGE, RegimeLabel.HIGH_VOL]
LABEL_TO_INT = {l: i for i, l in enumerate(LABEL_ORDER)}
INT_TO_LABEL = {i: l for l, i in LABEL_TO_INT.items()}


@dataclass(frozen=True)
class TrainingResult:
    model: HistGradientBoostingClassifier
    feature_cols: list[str]
    validation_accuracy: float
    folds_accuracy: list[float]
    passes_gate: bool


def label_bars(df: pd.DataFrame, trend_window: int = 30, vol_window: int = 30) -> pd.Series:
    """Ground-truth labels from price structure. Used as the supervisor signal."""
    adx_val = trend_strength(df, window=14)
    vol_val = realized_vol(df["close"], window=vol_window)
    ret = df["close"].pct_change(trend_window)

    labels: list[RegimeLabel] = []
    vol_hi = vol_val.quantile(0.80)
    for i in range(len(df)):
        v = vol_val.iloc[i] if i < len(vol_val) else np.nan
        a = adx_val.iloc[i] if i < len(adx_val) else np.nan
        r = ret.iloc[i] if i < len(ret) else np.nan
        if pd.isna(v) or pd.isna(a) or pd.isna(r):
            labels.append(RegimeLabel.RANGE)
            continue
        if v >= vol_hi:
            labels.append(RegimeLabel.HIGH_VOL)
        elif a > 25 and r > 0:
            labels.append(RegimeLabel.TRENDING_UP)
        elif a > 25 and r < 0:
            labels.append(RegimeLabel.TRENDING_DOWN)
        else:
            labels.append(RegimeLabel.RANGE)
    return pd.Series(labels, index=df.index, name="regime")


def build_feature_matrix(df: pd.DataFrame) -> pd.DataFrame:
    """Features available at bar-close without look-ahead."""
    out = pd.DataFrame(index=df.index)
    out["adx_14"] = trend_strength(df, window=14)
    out["vol_regime_14"] = vol_regime(df, window=14)
    out["rv_30"] = realized_vol(df["close"], window=30)
    out["ret_5"] = df["close"].pct_change(5)
    out["ret_30"] = df["close"].pct_change(30)
    # Time-of-day as integer bucket (0..4)
    bucket_map = {"opening_drive": 0, "morning": 1, "midday": 2, "afternoon": 3, "closing_auction": 4}
    from features.regime import time_of_day_bucket
    out["tod"] = time_of_day_bucket(df["ts"]).map(bucket_map).astype("float64")
    return out


def train_classifier(
    df: pd.DataFrame,
    n_splits: int = 3,
    random_state: int = 20260419,
) -> TrainingResult:
    """TimeSeriesSplit CV fit of HistGradientBoosting. Returns last model + metrics."""
    X = build_feature_matrix(df)
    y_label = label_bars(df)
    y = y_label.map(LABEL_TO_INT).astype("int64")
    mask = X.notna().all(axis=1) & y.notna()
    X = X[mask]
    y = y[mask]
    if len(X) < 50:
        raise ValueError("not enough clean rows to train classifier")

    splitter = TimeSeriesSplit(n_splits=n_splits)
    accs: list[float] = []
    final_model: HistGradientBoostingClassifier | None = None
    for tr, te in splitter.split(X):
        m = HistGradientBoostingClassifier(max_iter=100, random_state=random_state)
        m.fit(X.iloc[tr], y.iloc[tr])
        accs.append(float(m.score(X.iloc[te], y.iloc[te])))
        final_model = m
    assert final_model is not None

    val_acc = float(np.mean(accs)) if accs else 0.0
    return TrainingResult(
        model=final_model,
        feature_cols=list(X.columns),
        validation_accuracy=val_acc,
        folds_accuracy=accs,
        passes_gate=val_acc >= MIN_VALIDATION_ACCURACY,
    )


@dataclass
class RegimeClassifier:
    """Runtime wrapper used by the meta-controller."""
    model: HistGradientBoostingClassifier
    feature_cols: list[str]

    def predict(self, features: pd.DataFrame) -> pd.Series:
        X = features.reindex(columns=self.feature_cols).ffill().fillna(0.0)
        preds = self.model.predict(X)
        return pd.Series([INT_TO_LABEL[int(p)] for p in preds], index=features.index, name="regime")

    def predict_bar(self, row: pd.Series) -> RegimeLabel:
        """Single-bar inference for live loop."""
        X = pd.DataFrame([row.reindex(self.feature_cols).fillna(0.0).values], columns=self.feature_cols)
        pred = int(self.model.predict(X)[0])
        return INT_TO_LABEL[pred]

    def save(self, path: Path = REGIME_MODEL_PATH) -> None:
        path.write_bytes(pickle.dumps({"model": self.model, "feature_cols": self.feature_cols}))

    @classmethod
    def load(cls, path: Path = REGIME_MODEL_PATH) -> "RegimeClassifier":
        payload = pickle.loads(path.read_bytes())
        return cls(model=payload["model"], feature_cols=payload["feature_cols"])


def fit_from_dataframe(df: pd.DataFrame) -> RegimeClassifier:
    """Convenience: train and return a ready-to-use classifier."""
    result = train_classifier(df)
    return RegimeClassifier(model=result.model, feature_cols=result.feature_cols)
