"""Strategy unit tests — each strategy must be deterministic and gated."""

from __future__ import annotations

from app.domain.signals import Action
from app.domain.strategies import (
    MomentumBreakout,
    RangeBreakout,
    ReversalScalp,
)


def test_momentum_breakout_needs_minimum_bars(make_candles_factory):
    strat = MomentumBreakout()
    bars = make_candles_factory([100] * 10)
    for c in bars:
        assert strat.on_candle(c) is None


def test_momentum_breakout_fires_on_upward_break(make_candles_factory):
    strat = MomentumBreakout(momentum_threshold=0.001, lookback=10)
    # 20 flat bars → then jump > 0.3%
    flat = [100.0] * 20
    breakout = flat + [100.5]  # +0.5% jump, above 10-bar high
    bars = make_candles_factory(breakout)
    sig = None
    for c in bars:
        sig = strat.on_candle(c) or sig
    assert sig is not None
    assert sig.action == Action.BUY


def test_momentum_breakout_fires_on_downward_break(make_candles_factory):
    strat = MomentumBreakout(momentum_threshold=0.001, lookback=10)
    flat = [100.0] * 20
    crash = flat + [99.5]
    bars = make_candles_factory(crash)
    sig = None
    for c in bars:
        sig = strat.on_candle(c) or sig
    assert sig is not None
    assert sig.action == Action.SELL


def test_reversal_scalp_fades_overextension(make_candles_factory):
    strat = ReversalScalp(lookback=20, sigma_mult=1.0)
    # alternating closes produce mean≈100, sd>0 so the band has width
    closes = [100.0 + (1.0 if i % 2 == 0 else -1.0) for i in range(20)]
    closes.append(103.0)  # overextend above the upper band
    closes.append(100.0)  # reversal back inside
    bars = make_candles_factory(closes)
    sig = None
    for c in bars:
        sig = strat.on_candle(c) or sig
    assert sig is not None
    assert sig.action == Action.SELL


def test_range_breakout_ignores_wide_ranges(make_candles_factory):
    strat = RangeBreakout(lookback=10, max_range_pct=0.0025)
    # noisy bars with range > 0.25% → no signal even on break
    closes = [100, 103, 97, 102, 98, 101, 99, 100, 102, 98, 105]
    bars = make_candles_factory(closes)
    for c in bars:
        assert strat.on_candle(c) is None


def test_range_breakout_fires_on_tight_box(make_candles_factory):
    strat = RangeBreakout(lookback=10, max_range_pct=0.01)
    # Tight band 99.9-100.1, then clean break up
    closes = [100.0, 99.95, 100.05, 100.0, 99.9, 100.1, 100.0, 99.95, 100.05, 100.0, 101.5]
    bars = make_candles_factory(closes)
    sig = None
    for c in bars:
        sig = strat.on_candle(c) or sig
    assert sig is not None
    assert sig.action == Action.BUY


def test_strategy_context_reset_clears_bars():
    strat = MomentumBreakout()
    ctx = strat.context("NIFTY")
    ctx.bars.append(None)  # type: ignore[arg-type]
    assert len(ctx.bars) == 1
    strat.reset()
    assert len(strat.context("NIFTY").bars) == 0
