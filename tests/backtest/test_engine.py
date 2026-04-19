from __future__ import annotations

from pathlib import Path

import pandas as pd
import pytest

from backtest.engine import BacktestConfig, run
from strategies.base import Signal, SignalDirection, Strategy

FIXTURES = Path(__file__).resolve().parents[2] / "data" / "fixtures"


class _AlwaysLongStrategy(Strategy):
    """Fires a LONG signal on every 20th bar — useful for deterministic engine test."""
    name = "always_long_every_20"

    def on_bar(self, bar, history):
        if len(history) % 20 == 0:
            return Signal(direction=SignalDirection.LONG, stop_loss_pct=0.01, take_profit_pct=0.02)
        return None


def _sample_df() -> pd.DataFrame:
    df = pd.read_csv(FIXTURES / "ohlcv_1m_sample.csv", parse_dates=["ts"])
    return df[df["symbol"] == "NIFTY"].head(500).reset_index(drop=True)


def test_engine_is_deterministic():
    df = _sample_df()
    t1, m1 = run(df.copy(), _AlwaysLongStrategy(), cfg=BacktestConfig(max_bars_in_trade=10))
    t2, m2 = run(df.copy(), _AlwaysLongStrategy(), cfg=BacktestConfig(max_bars_in_trade=10))
    pd.testing.assert_frame_equal(t1, t2)
    assert m1 == m2


def test_engine_records_costs_on_every_trade():
    df = _sample_df()
    trades, _ = run(df, _AlwaysLongStrategy(), cfg=BacktestConfig(max_bars_in_trade=10))
    assert not trades.empty
    assert (trades["costs"] > 0).all()
    # net_pnl = gross - costs (within rounding)
    assert (
        (trades["gross_pnl"] - trades["costs"] - trades["net_pnl"]).abs() < 0.02
    ).all()


def test_engine_requires_ts_and_ohlc():
    with pytest.raises(ValueError):
        run(pd.DataFrame({"foo": [1, 2, 3]}), _AlwaysLongStrategy())
