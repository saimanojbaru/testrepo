"""Strategy abstract base class."""

from __future__ import annotations

from abc import ABC, abstractmethod
from collections import deque
from dataclasses import dataclass, field
from datetime import datetime

from ..signals import Candle, Signal


@dataclass(slots=True)
class StrategyContext:
    """Mutable per-instrument context the strategy can use for short history."""

    instrument: str
    bars: deque[Candle] = field(default_factory=lambda: deque(maxlen=240))
    opened_at: datetime | None = None
    last_signal_at: datetime | None = None


class Strategy(ABC):
    name: str = "abstract"
    regime: str = "neutral"
    description: str = ""

    def __init__(self) -> None:
        self._ctx: dict[str, StrategyContext] = {}

    def context(self, instrument: str) -> StrategyContext:
        ctx = self._ctx.get(instrument)
        if ctx is None:
            ctx = StrategyContext(instrument=instrument)
            self._ctx[instrument] = ctx
        return ctx

    @abstractmethod
    def on_candle(self, candle: Candle) -> Signal | None:
        """Consume the newest candle; return a signal or None."""

    def reset(self) -> None:
        self._ctx.clear()
