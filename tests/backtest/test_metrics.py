from __future__ import annotations

import pandas as pd

from backtest.metrics import compute_metrics


def test_empty_metrics():
    m = compute_metrics(pd.Series(dtype=float))
    assert m.trades == 0
    assert m.sharpe_net == 0.0


def test_profit_factor_and_win_rate():
    # 3 wins of 10, 2 losses of 5
    m = compute_metrics(pd.Series([10.0, 10.0, 10.0, -5.0, -5.0]))
    assert m.trades == 5
    assert m.win_rate == 0.6
    assert m.profit_factor == 30.0 / 10.0


def test_drawdown_nonpositive():
    m = compute_metrics(pd.Series([10.0, -20.0, 5.0, -30.0, 40.0]))
    assert m.max_drawdown <= 0
