"""MACD histogram flip — trending strategy."""

from __future__ import annotations

import statistics

from ...config import settings
from ..signals import Action, Candle, Signal
from .base import Strategy


def _ema(series: list[float], period: int) -> float:
    if len(series) < period:
        return series[-1]
    k = 2 / (period + 1)
    e = statistics.fmean(series[:period])
    for x in series[period:]:
        e = x * k + e * (1 - k)
    return e


def _macd(closes: list[float], fast: int = 12, slow: int = 26, sig: int = 9) -> tuple[float, float]:
    if len(closes) < slow + sig:
        return 0.0, 0.0
    macd_series = []
    for i in range(slow, len(closes) + 1):
        window = closes[:i]
        macd_series.append(_ema(window, fast) - _ema(window, slow))
    line = macd_series[-1]
    signal = _ema(macd_series, sig)
    return line, signal


class MacdFlip(Strategy):
    name = "macd_flip"
    regime = "trending"
    description = "Enter on MACD histogram crossing zero line."

    def __init__(
        self,
        stop_pct: float | None = None,
        target_pct: float | None = None,
    ) -> None:
        super().__init__()
        self.stop_pct = stop_pct or settings.signal_stop_loss_pct
        self.target_pct = target_pct or settings.signal_target_pct
        self._prev_hist: dict[str, float] = {}

    def on_candle(self, candle: Candle) -> Signal | None:
        ctx = self.context(candle.instrument)
        ctx.bars.append(candle)
        closes = [b.close for b in ctx.bars]
        line, signal_line = _macd(closes)
        hist = line - signal_line
        prev = self._prev_hist.get(candle.instrument)
        self._prev_hist[candle.instrument] = hist
        if prev is None or len(closes) < 40:
            return None

        px = candle.close
        if prev <= 0 and hist > 0:
            return Signal(
                instrument=candle.instrument,
                action=Action.BUY,
                entry_price=px,
                stop_loss=px * (1 - self.stop_pct),
                target=px * (1 + self.target_pct),
                confidence=min(1.0, abs(hist) / max(1e-9, abs(line))),
                timestamp=candle.ts,
                strategy=self.name,
                notes={"hist": hist, "line": line},
            )
        if prev >= 0 and hist < 0:
            return Signal(
                instrument=candle.instrument,
                action=Action.SELL,
                entry_price=px,
                stop_loss=px * (1 + self.stop_pct),
                target=px * (1 - self.target_pct),
                confidence=min(1.0, abs(hist) / max(1e-9, abs(line))),
                timestamp=candle.ts,
                strategy=self.name,
                notes={"hist": hist, "line": line},
            )
        return None
