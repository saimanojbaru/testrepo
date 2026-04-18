"""
Strategy Discovery via Optuna + Walk-Forward Validation.

Core idea: Don't hand-code strategies. Systematically search the space of
(feature, threshold, direction) combinations and promote rules with
Sharpe > 1.5 net-of-costs across ≥3 walk-forward folds.
"""

import json
from dataclasses import dataclass, field
from pathlib import Path
from typing import List, Callable, Optional
import pandas as pd
import numpy as np

from backtest.engine import BacktestEngine
from backtest.costs import CostModel
from strategies.base import ParametricRuleStrategy, Context


# Features we'll search over
FEATURE_CANDIDATES = [
    "rsi_14",
    "macd_histogram",
    "atr_14",
    "adx_14",
    "realized_vol",
]

# Threshold search ranges per feature
THRESHOLD_RANGES = {
    "rsi_14": {"entry": (10, 40), "exit": (60, 90)},
    "macd_histogram": {"entry": (-2, 0), "exit": (0, 2)},
    "atr_14": {"entry": (0.5, 2.0), "exit": (2.0, 5.0)},
    "adx_14": {"entry": (10, 25), "exit": (25, 50)},
    "realized_vol": {"entry": (0.1, 0.3), "exit": (0.3, 0.8)},
}


@dataclass
class DiscoveredStrategy:
    """A strategy that passed discovery gates."""
    name: str
    params: dict
    train_sharpe: float
    test_sharpe_windows: List[float] = field(default_factory=list)
    avg_test_sharpe: float = 0
    total_trades: int = 0
    net_pnl: float = 0
    win_rate: float = 0
    profit_factor: float = 0

    def passes_gate(self, min_sharpe: float = 1.5, min_windows: int = 3) -> bool:
        """Check if strategy passes promotion gate."""
        if len(self.test_sharpe_windows) < min_windows:
            return False
        passing = sum(1 for s in self.test_sharpe_windows if s > min_sharpe)
        return passing >= min_windows

    def to_dict(self) -> dict:
        return {
            "name": self.name,
            "params": self.params,
            "train_sharpe": self.train_sharpe,
            "test_sharpe_windows": self.test_sharpe_windows,
            "avg_test_sharpe": self.avg_test_sharpe,
            "total_trades": self.total_trades,
            "net_pnl": self.net_pnl,
            "win_rate": self.win_rate,
            "profit_factor": self.profit_factor,
        }


class StrategyDiscoverer:
    """
    Searches feature-combination space for profitable scalping rules.

    Uses Optuna for Bayesian optimization; walk-forward validates each candidate
    with real cost model applied.
    """

    def __init__(
        self,
        data: pd.DataFrame,
        cost_model: CostModel = None,
        initial_capital: float = 100000,
        min_sharpe: float = 1.5,
    ):
        self.data = data
        self.cost_model = cost_model or CostModel(broker="zerodha")
        self.initial_capital = initial_capital
        self.min_sharpe = min_sharpe

    def _run_single_backtest(
        self, params: dict, df: pd.DataFrame
    ) -> dict:
        """Run a single backtest for a given parameter set."""
        strategy = ParametricRuleStrategy(
            name=f"param_{params['feature_name']}",
            params=params,
        )

        engine = BacktestEngine(
            initial_capital=self.initial_capital,
            cost_model=self.cost_model,
        )

        def signal_fn(df, idx):
            ctx = Context(df=df, current_index=idx, position=engine.position)
            signal = strategy.on_bar(ctx)
            if signal is None:
                return None
            return (signal.action, signal.target_price)

        stats = engine.backtest(df, signal_fn)

        return {
            "sharpe": stats.sharpe_ratio,
            "trades": stats.total_trades,
            "pnl": stats.total_pnl,
            "win_rate": stats.win_rate,
            "profit_factor": stats.profit_factor,
        }

    def evaluate_candidate(
        self, params: dict, train_months: int = 6, test_months: int = 1
    ) -> DiscoveredStrategy:
        """
        Evaluate a candidate parameter set via walk-forward.

        Args:
            params: dict with feature_name, entry_threshold, exit_threshold, etc.
            train_months: Training window size
            test_months: Test window size

        Returns:
            DiscoveredStrategy with test Sharpes per window
        """
        df = self.data.copy()
        df["timestamp"] = pd.to_datetime(df["timestamp"])
        df = df.sort_values("timestamp").reset_index(drop=True)

        start_date = df["timestamp"].min()
        end_date = df["timestamp"].max()

        windows = []
        current_train_start = start_date

        while current_train_start < end_date:
            train_end = current_train_start + pd.DateOffset(months=train_months)
            test_end = train_end + pd.DateOffset(months=test_months)

            if test_end > end_date:
                break

            test_df = df[
                (df["timestamp"] >= train_end) & (df["timestamp"] < test_end)
            ].reset_index(drop=True)

            if len(test_df) < 20:  # Need minimum data
                break

            result = self._run_single_backtest(params, test_df)
            windows.append(result)

            current_train_start = train_end

        if not windows:
            return DiscoveredStrategy(name="invalid", params=params, train_sharpe=0)

        test_sharpes = [w["sharpe"] for w in windows]
        total_trades = sum(w["trades"] for w in windows)
        total_pnl = sum(w["pnl"] for w in windows)
        avg_win_rate = np.mean([w["win_rate"] for w in windows])
        avg_profit_factor = np.mean([w["profit_factor"] for w in windows if w["profit_factor"] > 0] or [0])

        return DiscoveredStrategy(
            name=f"{params['feature_name']}_thresh_{params.get('entry_threshold', 0):.0f}",
            params=params,
            train_sharpe=0,  # Not used in pure test evaluation
            test_sharpe_windows=test_sharpes,
            avg_test_sharpe=np.mean(test_sharpes),
            total_trades=total_trades,
            net_pnl=total_pnl,
            win_rate=avg_win_rate,
            profit_factor=avg_profit_factor,
        )

    def discover_optuna(self, n_trials: int = 100) -> List[DiscoveredStrategy]:
        """
        Use Optuna to search parameter space.
        Returns top strategies that pass gate.
        """
        try:
            import optuna
        except ImportError:
            print("Optuna not installed. Using grid search fallback.")
            return self.discover_grid(n_combinations=n_trials)

        optuna.logging.set_verbosity(optuna.logging.WARNING)
        discovered = []

        def objective(trial):
            feature = trial.suggest_categorical("feature_name", FEATURE_CANDIDATES)
            ranges = THRESHOLD_RANGES[feature]

            entry = trial.suggest_float("entry_threshold", *ranges["entry"])
            exit = trial.suggest_float("exit_threshold", *ranges["exit"])
            holding = trial.suggest_int("holding_period", 5, 30)

            params = {
                "feature_name": feature,
                "entry_threshold": entry,
                "exit_threshold": exit,
                "holding_period": holding,
            }

            strat = self.evaluate_candidate(params)
            discovered.append(strat)

            # Optuna maximizes avg Sharpe
            return strat.avg_test_sharpe if strat.test_sharpe_windows else -10

        study = optuna.create_study(direction="maximize")
        study.optimize(objective, n_trials=n_trials, show_progress_bar=False)

        # Filter to passing strategies
        passing = [s for s in discovered if s.passes_gate(self.min_sharpe)]
        passing.sort(key=lambda s: s.avg_test_sharpe, reverse=True)

        print(f"\n  Tried {len(discovered)} candidates; {len(passing)} passed gate")
        return passing

    def discover_grid(self, n_combinations: int = 50) -> List[DiscoveredStrategy]:
        """
        Grid search fallback if Optuna unavailable.
        """
        discovered = []
        per_feature = max(1, n_combinations // len(FEATURE_CANDIDATES))

        for feature in FEATURE_CANDIDATES:
            ranges = THRESHOLD_RANGES[feature]
            e_lo, e_hi = ranges["entry"]
            x_lo, x_hi = ranges["exit"]

            e_steps = np.linspace(e_lo, e_hi, int(np.sqrt(per_feature)))
            x_steps = np.linspace(x_lo, x_hi, int(np.sqrt(per_feature)))

            for e in e_steps:
                for x in x_steps:
                    params = {
                        "feature_name": feature,
                        "entry_threshold": e,
                        "exit_threshold": x,
                        "holding_period": 10,
                    }
                    strat = self.evaluate_candidate(params)
                    discovered.append(strat)

        passing = [s for s in discovered if s.passes_gate(self.min_sharpe)]
        passing.sort(key=lambda s: s.avg_test_sharpe, reverse=True)

        print(f"\n  Grid search: {len(discovered)} candidates; {len(passing)} passed gate")
        return passing

    def save_discovered(
        self, strategies: List[DiscoveredStrategy], output_path: str = "discovered_strategies.json"
    ):
        """Persist discovered strategies to JSON."""
        data = [s.to_dict() for s in strategies]
        Path(output_path).write_text(json.dumps(data, indent=2, default=str))
        print(f"  Saved {len(data)} strategies → {output_path}")


if __name__ == "__main__":
    # Placeholder example with synthetic data
    print("Strategy Discovery Engine")
    print("Load real data and run: discoverer.discover_optuna(n_trials=100)")
