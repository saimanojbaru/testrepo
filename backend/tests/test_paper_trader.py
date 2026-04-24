"""Paper trader lifecycle tests."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone

from app.domain.signals import Action, Signal
from app.domain.trades import ExitReason, Side
from app.engine.paper_trader import PaperTrader


def _sig(
    instrument: str = "NIFTY",
    action: Action = Action.BUY,
    price: float = 100.0,
    ts: datetime | None = None,
) -> Signal:
    return Signal(
        instrument=instrument,
        action=action,
        entry_price=price,
        stop_loss=price * 0.996 if action is Action.BUY else price * 1.004,
        target=price * 1.008 if action is Action.BUY else price * 0.992,
        confidence=0.6,
        timestamp=ts or datetime(2025, 1, 2, 10, 0, tzinfo=timezone.utc),
        strategy="momentum_breakout",
    )


def test_paper_trader_opens_long_and_hits_target():
    pt = PaperTrader(time_exit_seconds=300)
    sig = _sig()
    pos = pt.apply_signal(sig)
    assert pos is not None
    assert pos.side is Side.LONG

    closed = pt.on_tick("NIFTY", 110.0, sig.timestamp + timedelta(minutes=1))
    assert closed is not None
    assert closed.reason is ExitReason.TARGET
    assert closed.net_pnl != 0


def test_paper_trader_hits_stop_loss_on_long():
    pt = PaperTrader(time_exit_seconds=300)
    sig = _sig()
    pt.apply_signal(sig)
    closed = pt.on_tick("NIFTY", 95.0, sig.timestamp + timedelta(minutes=1))
    assert closed is not None
    assert closed.reason is ExitReason.STOP_LOSS
    assert closed.net_pnl < 0


def test_paper_trader_time_exit_triggers():
    pt = PaperTrader(time_exit_seconds=60)
    sig = _sig()
    pt.apply_signal(sig)
    closed = pt.on_tick("NIFTY", 100.1, sig.timestamp + timedelta(seconds=90))
    assert closed is not None
    assert closed.reason is ExitReason.TIME


def test_paper_trader_blocks_duplicate_open():
    pt = PaperTrader()
    sig = _sig()
    assert pt.apply_signal(sig) is not None
    assert pt.apply_signal(sig) is None  # already open


def test_paper_trader_short_side_math():
    pt = PaperTrader(time_exit_seconds=300)
    sig = _sig(action=Action.SELL, price=100.0)
    pt.apply_signal(sig)
    closed = pt.on_tick("NIFTY", 92.0, sig.timestamp + timedelta(minutes=1))
    assert closed is not None
    assert closed.side is Side.SHORT
    assert closed.reason is ExitReason.TARGET
    # short from 100 to 92 = 8 points gross; with ₹40 round-trip the net is
    # negative on 1 unit, but the lifecycle (side, reason, fill mechanics) is
    # what we assert here
    assert closed.gross_pnl > 0
    assert closed.exit_price < 100.0
