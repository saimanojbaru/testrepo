"""Backtester + metrics tests."""

from __future__ import annotations

from pathlib import Path

from app.backtest.costs import CostModel
from app.backtest.data_loader import load_ohlc_csv, split_by_day
from app.backtest.engine import Backtester
from app.backtest.metrics import compute
from app.domain.strategies import MomentumBreakout
from app.domain.trades import ClosedTrade, ExitReason, Side
from datetime import datetime, timezone


def _trade(pnl: float, costs: float = 40.0) -> ClosedTrade:
    ts = datetime(2025, 1, 2, 10, 0, tzinfo=timezone.utc)
    return ClosedTrade(
        trade_id=1,
        instrument="NIFTY",
        side=Side.LONG,
        entry=100.0,
        exit_price=100.0 + pnl,
        qty=1,
        gross_pnl=pnl,
        costs=costs,
        reason=ExitReason.TARGET if pnl > 0 else ExitReason.STOP_LOSS,
        strategy="test",
        opened_at=ts,
        closed_at=ts,
    )


def test_metrics_empty_returns_zeros():
    m = compute([])
    assert m.total_trades == 0
    assert m.win_rate == 0
    assert m.net_pnl == 0


def test_metrics_computed_correctly():
    trades = [_trade(100, 20), _trade(-50, 20), _trade(80, 20)]
    m = compute(trades, strategy="test")
    assert m.total_trades == 3
    assert m.gross_pnl == 130
    assert m.costs == 60
    assert m.net_pnl == 70
    assert m.win_rate == round(2 / 3, 4)


def test_metrics_drawdown_tracks_peak():
    # equity: +80, -70, +10 → peak 80, dd 10+30=40 when -70 then +10? Wait recompute
    # net_pnls: 80, -70, 10. running: 80, 10, 20. peak=80 after t1. DD t2 = 70. DD t3 = 60.
    trades = [_trade(100, 20), _trade(-50, 20), _trade(30, 20)]
    m = compute(trades)
    assert m.max_drawdown == 70


def test_data_loader_reads_sample_csv():
    path = Path(__file__).parent.parent / "data" / "sample_ohlcv.csv"
    candles = load_ohlc_csv(path)
    assert len(candles) > 30
    assert candles[0].instrument == "NIFTY"
    assert candles[0].open > 0


def test_split_by_day_single_day_sample():
    path = Path(__file__).parent.parent / "data" / "sample_ohlcv.csv"
    candles = load_ohlc_csv(path)
    slices = list(split_by_day(candles))
    assert len(slices) == 1
    assert slices[0].candles == candles


def test_backtester_produces_deterministic_results():
    path = Path(__file__).parent.parent / "data" / "sample_ohlcv.csv"
    candles = load_ohlc_csv(path)
    strat = MomentumBreakout(momentum_threshold=0.0005, lookback=5)
    eng = Backtester(cost=CostModel(brokerage_per_trade=20.0, slippage_bps=2.0))
    r1 = eng.run(strat, candles)
    strat.reset()
    r2 = eng.run(strat, candles)
    assert [t.to_dict() for t in r1.trades] == [t.to_dict() for t in r2.trades]
