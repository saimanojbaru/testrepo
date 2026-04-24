"""Range breakout: identify a tight consolidation box, enter on the break."""

from __future__ import annotations

from ...config import settings
from ..signals import Action, Candle, Signal
from .base import Strategy


class RangeBreakout(Strategy):
    name = "range_breakout"
    regime = "volatile"
    description = (
        "If the last N bars have a range (high-low) below R% of mid-price, "
        "take the break of that box in whichever direction first breaches it."
    )

    def __init__(
        self,
        lookback: int = 10,
        max_range_pct: float = 0.0025,  # 0.25%
        stop_pct: float | None = None,
        target_pct: float | None = None,
    ) -> None:
        super().__init__()
        self.lookback = lookback
        self.max_range_pct = max_range_pct
        self.stop_pct = stop_pct or settings.signal_stop_loss_pct
        self.target_pct = target_pct or settings.signal_target_pct

    def on_candle(self, candle: Candle) -> Signal | None:
        ctx = self.context(candle.instrument)
        ctx.bars.append(candle)
        if len(ctx.bars) < self.lookback + 1:
            return None

        box = list(ctx.bars)[-self.lookback - 1 : -1]
        hi = max(b.high for b in box)
        lo = min(b.low for b in box)
        mid = (hi + lo) / 2
        if mid <= 0:
            return None
        if (hi - lo) / mid > self.max_range_pct:
            return None  # not a tight box

        px = candle.close
        if candle.close > hi and candle.open < hi:
            return self._signal(candle, Action.BUY, (px - hi) / mid)
        if candle.close < lo and candle.open > lo:
            return self._signal(candle, Action.SELL, (lo - px) / mid)
        return None

    def _signal(self, candle: Candle, action: Action, strength: float) -> Signal:
        px = candle.close
        if action is Action.BUY:
            sl = px * (1 - self.stop_pct)
            tp = px * (1 + self.target_pct)
        else:
            sl = px * (1 + self.stop_pct)
            tp = px * (1 - self.target_pct)
        return Signal(
            instrument=candle.instrument,
            action=action,
            entry_price=px,
            stop_loss=sl,
            target=tp,
            confidence=min(1.0, abs(strength) * 50),
            timestamp=candle.ts,
            strategy=self.name,
            notes={"box_break_pct": strength},
        )
