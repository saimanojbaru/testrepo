"""Walk-forward validation harness.

Splits data into overlapping (train, test) folds; caller fits/selects a strategy
on each train slice and we collect test-slice P&L + metrics. For MVP the "fit"
is simply the strategy's own discovery loop; the harness is strategy-agnostic.

Promotion gate: strategy passes if sharpe_net > MIN_SHARPE across at least
MIN_FOLDS_PASSING folds.
"""
from __future__ import annotations

import argparse
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Callable

import pandas as pd

from backtest.engine import BacktestConfig, run
from backtest.metrics import Metrics
from strategies.base import Strategy

MIN_SHARPE = 1.5
MIN_FOLDS_PASSING = 3


@dataclass(frozen=True)
class Fold:
    index: int
    train_start: pd.Timestamp
    train_end: pd.Timestamp
    test_start: pd.Timestamp
    test_end: pd.Timestamp


@dataclass(frozen=True)
class FoldResult:
    fold: Fold
    metrics: Metrics


def build_folds(
    df: pd.DataFrame,
    train_bars: int,
    test_bars: int,
    n_folds: int,
    ts_col: str = "ts",
) -> list[Fold]:
    """Produce n_folds non-overlapping (train, test) windows scanning left-to-right."""
    if len(df) < train_bars + test_bars:
        raise ValueError("not enough data for even one fold")
    folds: list[Fold] = []
    step = test_bars
    start = 0
    for i in range(n_folds):
        train_end_idx = start + train_bars
        test_end_idx = train_end_idx + test_bars
        if test_end_idx > len(df):
            break
        folds.append(
            Fold(
                index=i,
                train_start=df.iloc[start][ts_col],
                train_end=df.iloc[train_end_idx - 1][ts_col],
                test_start=df.iloc[train_end_idx][ts_col],
                test_end=df.iloc[test_end_idx - 1][ts_col],
            )
        )
        start += step
    return folds


def evaluate(
    df: pd.DataFrame,
    strategy_factory: Callable[[pd.DataFrame], Strategy],
    train_bars: int = 6 * 21 * 375,   # ~6 months of 1-min bars
    test_bars: int = 1 * 21 * 375,    # ~1 month
    n_folds: int = 3,
    bt_cfg: BacktestConfig = BacktestConfig(),
    ts_col: str = "ts",
) -> list[FoldResult]:
    """For each fold: build strategy from train slice, evaluate on test slice."""
    folds = build_folds(df, train_bars=train_bars, test_bars=test_bars, n_folds=n_folds, ts_col=ts_col)
    results: list[FoldResult] = []
    for fold in folds:
        train = df[(df[ts_col] >= fold.train_start) & (df[ts_col] <= fold.train_end)]
        test = df[(df[ts_col] >= fold.test_start) & (df[ts_col] <= fold.test_end)]
        strat = strategy_factory(train)
        _, metrics = run(test.reset_index(drop=True), strat, cfg=bt_cfg)
        results.append(FoldResult(fold=fold, metrics=metrics))
    return results


def passes_promotion_gate(
    results: list[FoldResult],
    min_sharpe: float = MIN_SHARPE,
    min_folds_passing: int = MIN_FOLDS_PASSING,
) -> bool:
    passing = sum(1 for r in results if r.metrics.sharpe_net >= min_sharpe)
    return passing >= min_folds_passing


def cli(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(prog="scalp-walkforward")
    p.add_argument("--strategy", default="top_1", help="key in discovered_strategies.json")
    p.add_argument("--folds", type=int, default=3)
    p.add_argument("--registry", type=Path, default=Path("discovered_strategies.json"))
    args = p.parse_args(argv)

    if not args.registry.exists():
        print(f"registry not found at {args.registry}; run `scalp-discover` first")
        return 2

    data = json.loads(args.registry.read_text())
    print(f"# strategies in registry: {len(data.get('strategies', []))}")
    chosen = next((s for s in data.get("strategies", []) if s["key"] == args.strategy), None)
    if not chosen:
        print(f"strategy {args.strategy} not found; available keys: "
              f"{[s['key'] for s in data.get('strategies', [])]}")
        return 3

    print(f"# walk-forward n_folds={args.folds} strategy={chosen['key']}")
    print(f"# recorded fold metrics (from discovery): {json.dumps(chosen.get('fold_metrics', []), indent=2)}")
    return 0


if __name__ == "__main__":
    raise SystemExit(cli())
