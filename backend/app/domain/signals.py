"""Signal and candle value objects."""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime
from enum import Enum


class Action(str, Enum):
    BUY = "BUY"
    SELL = "SELL"
    FLAT = "FLAT"


@dataclass(slots=True, frozen=True)
class Tick:
    instrument: str
    price: float
    ts: datetime


@dataclass(slots=True)
class Candle:
    instrument: str
    ts: datetime
    open: float
    high: float
    low: float
    close: float
    volume: float = 0.0

    @property
    def bullish(self) -> bool:
        return self.close >= self.open


@dataclass(slots=True, frozen=True)
class Signal:
    instrument: str
    action: Action
    entry_price: float
    stop_loss: float
    target: float
    confidence: float
    timestamp: datetime
    strategy: str = "unknown"
    notes: dict = field(default_factory=dict)

    def to_dict(self) -> dict:
        return {
            "instrument": self.instrument,
            "action": self.action.value,
            "entry_price": self.entry_price,
            "stop_loss": self.stop_loss,
            "target": self.target,
            "confidence": self.confidence,
            "timestamp": self.timestamp.isoformat(),
            "strategy": self.strategy,
            "notes": self.notes,
        }
