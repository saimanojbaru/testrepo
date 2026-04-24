"""Inside bar breakout — volatile expansion."""

from __future__ import annotations

from ...config import settings
from ..signals import Action, Candle, Signal
from .base import Strategy


class InsideBarBreak(Strategy):
    name = "inside_bar_break"
    regime = "volatile"
    description = "Bar fully inside prior bar, then break either side."

    def __init__(
        self,
        stop_pct: float | None = None,
        target_pct: float | None = None,
    ) -> None:
        super().__init__()
        self.stop_pct = stop_pct or settings.signal_stop_loss_pct
        self.target_pct = target_pct or settings.signal_target_pct

    def on_candle(self, candle: Candle) -> Signal | None:
        ctx = self.context(candle.instrument)
        ctx.bars.append(candle)
        if len(ctx.bars) < 3:
            return None
        bars = list(ctx.bars)
        mother = bars[-3]
        inside = bars[-2]
        last = bars[-1]
        is_inside = inside.high <= mother.high and inside.low >= mother.low
        if not is_inside:
            return None
        px = last.close
        if px > mother.high:
            return Signal(
                instrument=candle.instrument,
                action=Action.BUY,
                entry_price=px,
                stop_loss=inside.low,
                target=px * (1 + self.target_pct),
                confidence=0.55,
                timestamp=candle.ts,
                strategy=self.name,
                notes={"mother_high": mother.high},
            )
        if px < mother.low:
            return Signal(
                instrument=candle.instrument,
                action=Action.SELL,
                entry_price=px,
                stop_loss=inside.high,
                target=px * (1 - self.target_pct),
                confidence=0.55,
                timestamp=candle.ts,
                strategy=self.name,
                notes={"mother_low": mother.low},
            )
        return None
