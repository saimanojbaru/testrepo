"""Donchian channel breakout — trending strategy."""

from __future__ import annotations

from ...config import settings
from ..signals import Action, Candle, Signal
from .base import Strategy


class DonchianBreak(Strategy):
    name = "donchian_break"
    regime = "trending"
    description = "Enter on break of N-bar high/low."

    def __init__(
        self,
        lookback: int = 20,
        stop_pct: float | None = None,
        target_pct: float | None = None,
    ) -> None:
        super().__init__()
        self.lookback = lookback
        self.stop_pct = stop_pct or settings.signal_stop_loss_pct
        self.target_pct = target_pct or settings.signal_target_pct

    def on_candle(self, candle: Candle) -> Signal | None:
        ctx = self.context(candle.instrument)
        ctx.bars.append(candle)
        if len(ctx.bars) < self.lookback + 1:
            return None
        prior = list(ctx.bars)[-self.lookback - 1 : -1]
        hi = max(b.high for b in prior)
        lo = min(b.low for b in prior)
        px = candle.close
        if px > hi:
            return Signal(
                instrument=candle.instrument,
                action=Action.BUY,
                entry_price=px,
                stop_loss=px * (1 - self.stop_pct),
                target=px * (1 + self.target_pct),
                confidence=min(1.0, (px - hi) / hi * 200) if hi else 0.5,
                timestamp=candle.ts,
                strategy=self.name,
                notes={"break_level": hi},
            )
        if px < lo:
            return Signal(
                instrument=candle.instrument,
                action=Action.SELL,
                entry_price=px,
                stop_loss=px * (1 + self.stop_pct),
                target=px * (1 - self.target_pct),
                confidence=min(1.0, (lo - px) / lo * 200) if lo else 0.5,
                timestamp=candle.ts,
                strategy=self.name,
                notes={"break_level": lo},
            )
        return None
