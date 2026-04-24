"""Momentum burst — N-bar rate-of-change over threshold."""

from __future__ import annotations

from ...config import settings
from ..signals import Action, Candle, Signal
from .base import Strategy


class MomentumBurst(Strategy):
    name = "momentum_burst"
    regime = "volatile"
    description = "Enter when N-bar ROC exceeds a threshold."

    def __init__(
        self,
        lookback: int = 5,
        threshold: float = 0.004,
        stop_pct: float | None = None,
        target_pct: float | None = None,
    ) -> None:
        super().__init__()
        self.lookback = lookback
        self.threshold = threshold
        self.stop_pct = stop_pct or settings.signal_stop_loss_pct
        self.target_pct = target_pct or settings.signal_target_pct

    def on_candle(self, candle: Candle) -> Signal | None:
        ctx = self.context(candle.instrument)
        ctx.bars.append(candle)
        if len(ctx.bars) < self.lookback + 1:
            return None
        anchor = list(ctx.bars)[-self.lookback - 1]
        if anchor.close == 0:
            return None
        roc = (candle.close - anchor.close) / anchor.close
        px = candle.close
        if roc > self.threshold:
            return Signal(
                instrument=candle.instrument,
                action=Action.BUY,
                entry_price=px,
                stop_loss=px * (1 - self.stop_pct),
                target=px * (1 + self.target_pct),
                confidence=min(1.0, roc / (self.threshold * 3)),
                timestamp=candle.ts,
                strategy=self.name,
                notes={"roc": roc},
            )
        if roc < -self.threshold:
            return Signal(
                instrument=candle.instrument,
                action=Action.SELL,
                entry_price=px,
                stop_loss=px * (1 + self.stop_pct),
                target=px * (1 - self.target_pct),
                confidence=min(1.0, abs(roc) / (self.threshold * 3)),
                timestamp=candle.ts,
                strategy=self.name,
                notes={"roc": roc},
            )
        return None
