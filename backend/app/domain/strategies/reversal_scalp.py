"""Reversal scalp: fade an overextended move against a short-term mean."""

from __future__ import annotations

import statistics

from ...config import settings
from ..signals import Action, Candle, Signal
from .base import Strategy


class ReversalScalp(Strategy):
    name = "reversal_scalp"
    regime = "ranging"
    description = (
        "If the latest bar stretches > 2 sigma from the 20-bar mean and "
        "reverses (close back inside the band), fade the move."
    )

    def __init__(
        self,
        lookback: int = 20,
        sigma_mult: float = 2.0,
        stop_pct: float | None = None,
        target_pct: float | None = None,
    ) -> None:
        super().__init__()
        self.lookback = lookback
        self.sigma_mult = sigma_mult
        self.stop_pct = stop_pct or settings.signal_stop_loss_pct
        self.target_pct = target_pct or (settings.signal_target_pct * 0.75)

    def on_candle(self, candle: Candle) -> Signal | None:
        ctx = self.context(candle.instrument)
        ctx.bars.append(candle)
        if len(ctx.bars) < self.lookback + 1:
            return None

        recent = list(ctx.bars)[-self.lookback - 1 : -1]
        closes = [b.close for b in recent]
        mean = statistics.fmean(closes)
        sd = statistics.pstdev(closes) or 1e-9
        upper = mean + self.sigma_mult * sd
        lower = mean - self.sigma_mult * sd

        prior = list(ctx.bars)[-2]
        px = candle.close

        # Price pushed above upper band last bar, closed back below upper → short
        if prior.high > upper and px < upper and px < prior.close:
            return self._signal(candle, Action.SELL, (prior.high - upper) / sd)
        # Mirrored long on the lower band
        if prior.low < lower and px > lower and px > prior.close:
            return self._signal(candle, Action.BUY, (lower - prior.low) / sd)
        return None

    def _signal(self, candle: Candle, action: Action, strength: float) -> Signal:
        px = candle.close
        if action is Action.BUY:
            sl = px * (1 - self.stop_pct)
            tp = px * (1 + self.target_pct)
        else:
            sl = px * (1 + self.stop_pct)
            tp = px * (1 - self.target_pct)
        confidence = min(1.0, strength / 3.0)
        return Signal(
            instrument=candle.instrument,
            action=action,
            entry_price=px,
            stop_loss=sl,
            target=tp,
            confidence=confidence,
            timestamp=candle.ts,
            strategy=self.name,
            notes={"z_score": strength},
        )
