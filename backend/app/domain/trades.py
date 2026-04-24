"""Trade lifecycle value objects."""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime
from enum import Enum


class Side(str, Enum):
    LONG = "LONG"
    SHORT = "SHORT"


class ExitReason(str, Enum):
    STOP_LOSS = "SL"
    TARGET = "TP"
    TIME = "TIME"
    MANUAL = "MANUAL"
    RISK_HALT = "RISK"


@dataclass(slots=True)
class OpenPosition:
    trade_id: int
    instrument: str
    side: Side
    entry: float
    stop_loss: float
    target: float
    opened_at: datetime
    strategy: str
    qty: int = 1

    def mtm(self, price: float) -> float:
        direction = 1 if self.side is Side.LONG else -1
        return (price - self.entry) * direction * self.qty


@dataclass(slots=True, frozen=True)
class ClosedTrade:
    trade_id: int
    instrument: str
    side: Side
    entry: float
    exit_price: float
    qty: int
    gross_pnl: float
    costs: float
    reason: ExitReason
    strategy: str
    opened_at: datetime
    closed_at: datetime

    @property
    def net_pnl(self) -> float:
        return self.gross_pnl - self.costs

    @property
    def win(self) -> bool:
        return self.net_pnl > 0

    def to_dict(self) -> dict:
        return {
            "trade_id": self.trade_id,
            "instrument": self.instrument,
            "side": self.side.value,
            "entry": self.entry,
            "exit": self.exit_price,
            "qty": self.qty,
            "gross_pnl": self.gross_pnl,
            "costs": self.costs,
            "net_pnl": self.net_pnl,
            "reason": self.reason.value,
            "strategy": self.strategy,
            "opened_at": self.opened_at.isoformat(),
            "closed_at": self.closed_at.isoformat(),
        }
