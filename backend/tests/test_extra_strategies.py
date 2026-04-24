"""Tests for the strategies added in the expansion pass."""

from __future__ import annotations

from app.domain.signals import Action
from app.domain.strategies import (
    DonchianBreak,
    InsideBarBreak,
    MacdFlip,
    MomentumBurst,
    STRATEGIES,
    build_all,
)
from app.domain.signals import Candle
from datetime import datetime, timedelta, timezone


def _minute(i: int) -> datetime:
    return datetime(2025, 1, 2, 9, 15, tzinfo=timezone.utc) + timedelta(minutes=i)


def _bar(i: int, o: float, h: float, lo: float, c: float) -> Candle:
    return Candle(instrument="NIFTY", ts=_minute(i), open=o, high=h, low=lo, close=c)


def test_registry_has_seven_strategies_covering_three_regimes():
    assert len(STRATEGIES) == 7
    regimes = {s.regime for s in build_all()}
    assert regimes == {"trending", "ranging", "volatile"}


def test_donchian_break_fires_on_upside():
    s = DonchianBreak(lookback=10)
    # 10 flat bars at 100, then a bar close at 102 (above 10-bar high 100.5)
    sig = None
    for i in range(10):
        sig = s.on_candle(_bar(i, 100, 100.5, 99.5, 100)) or sig
    sig = s.on_candle(_bar(10, 100, 102.5, 100, 102)) or sig
    assert sig is not None
    assert sig.action == Action.BUY


def test_momentum_burst_fires_on_roc_breach():
    s = MomentumBurst(lookback=5, threshold=0.004)
    sig = None
    for i in range(5):
        sig = s.on_candle(_bar(i, 100, 100.5, 99.5, 100)) or sig
    sig = s.on_candle(_bar(5, 100, 101, 99.5, 100.7)) or sig  # +0.7% in 5 bars
    assert sig is not None
    assert sig.action == Action.BUY


def test_inside_bar_break_fires_on_mother_break():
    s = InsideBarBreak()
    mother = _bar(0, 100, 102, 99, 101)        # range 99-102
    inside = _bar(1, 100.5, 101, 100, 100.8)   # fully inside
    breakout = _bar(2, 101, 103.2, 100.9, 103)  # closes above mother.high
    assert s.on_candle(mother) is None
    assert s.on_candle(inside) is None
    sig = s.on_candle(breakout)
    assert sig is not None
    assert sig.action == Action.BUY


def test_macd_flip_needs_enough_bars():
    s = MacdFlip()
    # Fewer than 40 bars → never fires
    for i in range(30):
        assert s.on_candle(_bar(i, 100, 100.5, 99.5, 100)) is None
