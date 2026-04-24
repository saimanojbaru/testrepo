"""Momentum breakout: price moves > threshold AND breaks recent high/low."""

from __future__ import annotations

from ...config import settings
from ..signals import Action, Candle, Signal
from .base import Strategy


class MomentumBreakout(Strategy):
    name = "momentum_breakout"
    regime = "trending"
    description = (
        "Enter long when last bar moves > momentum_threshold AND closes above "
        "the recent N-bar high. Mirror for short."
    )

    def __init__(
        self,
        momentum_threshold: float | None = None,
        lookback: int | None = None,
        stop_pct: float | None = None,
        target_pct: float | None = None,
    ) -> None:
        super().__init__()
        self.momentum_threshold = (
            momentum_threshold
            if momentum_threshold is not None
            else settings.signal_momentum_threshold
        )
        self.lookback = lookback or settings.signal_breakout_lookback
        self.stop_pct = stop_pct or settings.signal_stop_loss_pct
        self.target_pct = target_pct or settings.signal_target_pct

    def on_candle(self, candle: Candle) -> Signal | None:
        ctx = self.context(candle.instrument)
        ctx.bars.append(candle)
        if len(ctx.bars) <= self.lookback:
            return None

        recent = list(ctx.bars)[-(self.lookback + 1) : -1]  # exclude current bar
        prior = list(ctx.bars)[-2]
        momentum = (candle.close - prior.close) / prior.close if prior.close else 0.0
        high = max(b.high for b in recent)
        low = min(b.low for b in recent)

        if momentum > self.momentum_threshold and candle.close > high:
            return self._signal(candle, Action.BUY, momentum)
        if momentum < -self.momentum_threshold and candle.close < low:
            return self._signal(candle, Action.SELL, -momentum)
        return None

    def _signal(self, candle: Candle, action: Action, strength: float) -> Signal:
        px = candle.close
        if action is Action.BUY:
            sl = px * (1 - self.stop_pct)
            tp = px * (1 + self.target_pct)
        else:
            sl = px * (1 + self.stop_pct)
            tp = px * (1 - self.target_pct)
        confidence = min(1.0, strength / (self.momentum_threshold * 3))
        return Signal(
            instrument=candle.instrument,
            action=action,
            entry_price=px,
            stop_loss=sl,
            target=tp,
            confidence=confidence,
            timestamp=candle.ts,
            strategy=self.name,
            notes={
                "momentum": strength,
                "breakout_level": px,
            },
        )
