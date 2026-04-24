"""Signal engine + tick processor tests."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone

from app.domain.signals import Tick
from app.domain.strategies import MomentumBreakout
from app.engine.paper_trader import PaperTrader
from app.engine.risk_manager import RiskManager
from app.engine.signal_engine import SignalEngine
from app.engine.tick_processor import TickProcessor


def _t(sec: int, px: float, inst="NIFTY") -> Tick:
    return Tick(
        instrument=inst,
        price=px,
        ts=datetime(2025, 1, 2, 9, 15, tzinfo=timezone.utc) + timedelta(seconds=sec),
    )


def test_tick_processor_emits_on_bucket_boundary():
    proc = TickProcessor(bucket_seconds=60)
    emitted = []
    proc.on_candle = emitted.append
    proc.on_tick(_t(5, 100))
    proc.on_tick(_t(30, 102))
    proc.on_tick(_t(58, 101))
    proc.on_tick(_t(61, 103))  # crosses boundary → previous closes
    assert len(emitted) == 1
    c = emitted[0]
    assert c.open == 100
    assert c.high == 102
    assert c.low == 100
    assert c.close == 101


def test_tick_processor_per_instrument_isolation():
    proc = TickProcessor(bucket_seconds=60)
    proc.on_tick(_t(10, 100, "NIFTY"))
    proc.on_tick(_t(10, 52000, "BANKNIFTY"))
    assert proc.snapshot("NIFTY").close == 100
    assert proc.snapshot("BANKNIFTY").close == 52000


def test_signal_engine_opens_paper_trade_on_breakout():
    # engine with a hair-trigger momentum strategy
    strat = MomentumBreakout(momentum_threshold=0.0005, lookback=5)
    trader = PaperTrader(time_exit_seconds=300)
    risk = RiskManager(max_trades_per_hour=10, consecutive_loss_halt=10)
    engine = SignalEngine(
        strategies=[strat], trader=trader, risk=risk, bucket_seconds=60
    )

    base = datetime(2025, 1, 2, 9, 15, tzinfo=timezone.utc)
    # Emit 20 minute-spaced ticks: 10 flat, then rising
    prices = [100.0] * 10 + [100.5, 101.0, 101.5, 102.0, 102.5, 103.0, 103.5, 104.0]
    for i, p in enumerate(prices):
        t = Tick(
            instrument="NIFTY",
            price=p,
            ts=base + timedelta(minutes=i),
        )
        engine.on_tick(t)

    assert len(engine.recent_signals) >= 1
    sig = engine.recent_signals[-1]
    assert sig.action.value in ("BUY", "SELL")


def test_risk_manager_halts_after_consecutive_losses():
    from app.domain.trades import ClosedTrade, ExitReason, Side

    rm = RiskManager(consecutive_loss_halt=2, daily_loss_limit=10_000)
    now = datetime(2025, 1, 2, 10, 0, tzinfo=timezone.utc)
    loss = ClosedTrade(
        trade_id=1,
        instrument="NIFTY",
        side=Side.LONG,
        entry=100,
        exit_price=90,
        qty=1,
        gross_pnl=-10,
        costs=40,
        reason=ExitReason.STOP_LOSS,
        strategy="test",
        opened_at=now,
        closed_at=now,
    )
    rm.record_trade_closed(loss)
    rm.record_trade_closed(loss)
    decision = rm.evaluate(now + timedelta(minutes=5))
    assert not decision.accepted
    assert "consecutive losses" in (decision.reason or "")


def test_risk_manager_enforces_cooldown_after_loss():
    from app.domain.trades import ClosedTrade, ExitReason, Side

    rm = RiskManager(
        consecutive_loss_halt=5, cooldown_seconds=60, daily_loss_limit=10_000
    )
    now = datetime(2025, 1, 2, 10, 0, tzinfo=timezone.utc)
    rm.record_trade_closed(
        ClosedTrade(
            trade_id=1,
            instrument="NIFTY",
            side=Side.LONG,
            entry=100,
            exit_price=99,
            qty=1,
            gross_pnl=-1,
            costs=40,
            reason=ExitReason.STOP_LOSS,
            strategy="test",
            opened_at=now,
            closed_at=now,
        )
    )
    assert not rm.evaluate(now + timedelta(seconds=30)).accepted
    assert rm.evaluate(now + timedelta(seconds=120)).accepted


def test_risk_manager_enforces_trades_per_hour_cap():
    rm = RiskManager(max_trades_per_hour=2, consecutive_loss_halt=99)
    now = datetime(2025, 1, 2, 10, 0, tzinfo=timezone.utc)
    rm.record_trade_opened(now)
    rm.record_trade_opened(now + timedelta(minutes=5))
    d = rm.evaluate(now + timedelta(minutes=10))
    assert not d.accepted
    assert "max_trades_per_hour" in (d.reason or "")
