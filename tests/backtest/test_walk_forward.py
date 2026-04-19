from __future__ import annotations

from dataclasses import replace

import pandas as pd

from backtest.metrics import Metrics
from backtest.walk_forward import (
    MIN_FOLDS_PASSING,
    MIN_SHARPE,
    Fold,
    FoldResult,
    build_folds,
    passes_promotion_gate,
)


def _stub_metrics(sharpe: float) -> Metrics:
    return Metrics(
        trades=10,
        gross_pnl=100.0,
        net_pnl=100.0,
        win_rate=0.5,
        expectancy=10.0,
        profit_factor=1.2,
        sharpe_net=sharpe,
        sortino=1.0,
        max_drawdown=-20.0,
        calmar=1.0,
    )


def test_build_folds_count_and_shape():
    df = pd.DataFrame(
        {"ts": pd.date_range("2026-01-01", periods=1000, freq="min", tz="Asia/Kolkata")}
    )
    folds = build_folds(df, train_bars=400, test_bars=100, n_folds=5)
    assert len(folds) == 5
    assert all(isinstance(f, Fold) for f in folds)
    # Non-overlapping test windows
    for i in range(1, len(folds)):
        assert folds[i].test_start > folds[i - 1].test_end


def test_promotion_gate_passes_with_enough_folds():
    fake = [
        FoldResult(
            fold=Fold(index=i, train_start=pd.Timestamp(0), train_end=pd.Timestamp(0),
                      test_start=pd.Timestamp(0), test_end=pd.Timestamp(0)),
            metrics=_stub_metrics(MIN_SHARPE + 0.1),
        )
        for i in range(MIN_FOLDS_PASSING)
    ]
    assert passes_promotion_gate(fake)


def test_promotion_gate_rejects_too_few_passing():
    fake = [
        FoldResult(
            fold=Fold(index=i, train_start=pd.Timestamp(0), train_end=pd.Timestamp(0),
                      test_start=pd.Timestamp(0), test_end=pd.Timestamp(0)),
            metrics=_stub_metrics(MIN_SHARPE - 0.1),
        )
        for i in range(MIN_FOLDS_PASSING)
    ]
    assert not passes_promotion_gate(fake)
