"""Broker adapter interface — paper, Upstox, Kite all implement this ABC."""
from __future__ import annotations

import datetime as dt
from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from enum import Enum
from typing import Iterable


class OrderSide(str, Enum):
    BUY = "BUY"
    SELL = "SELL"


class OrderType(str, Enum):
    MARKET = "MARKET"
    LIMIT = "LIMIT"
    SL = "SL"
    SL_M = "SL_M"


class OrderStatus(str, Enum):
    NEW = "NEW"
    SENT = "SENT"
    OPEN = "OPEN"
    PARTIAL = "PARTIAL"
    FILLED = "FILLED"
    CANCELLED = "CANCELLED"
    REJECTED = "REJECTED"


class Product(str, Enum):
    INTRADAY = "INTRADAY"     # MIS / I
    CARRY = "CARRY"           # NRML / D
    DELIVERY = "DELIVERY"     # CNC


@dataclass
class Order:
    instrument_key: str
    side: OrderSide
    quantity: int
    order_type: OrderType
    product: Product
    price: float | None = None
    trigger_price: float | None = None
    tag: str = ""
    idempotency_key: str = ""
    broker_order_id: str | None = None
    status: OrderStatus = OrderStatus.NEW
    filled_quantity: int = 0
    avg_fill_price: float | None = None
    rejection_reason: str | None = None
    created_at: dt.datetime = field(default_factory=dt.datetime.utcnow)
    updated_at: dt.datetime = field(default_factory=dt.datetime.utcnow)


@dataclass
class Position:
    instrument_key: str
    quantity: int
    avg_price: float
    last_price: float
    unrealized_pnl: float
    realized_pnl: float = 0.0


class Broker(ABC):
    """Minimum viable broker interface the trading agent depends on."""

    name: str

    @abstractmethod
    def place_order(self, order: Order) -> Order:
        ...

    @abstractmethod
    def modify_order(self, broker_order_id: str, **changes) -> Order:
        ...

    @abstractmethod
    def cancel_order(self, broker_order_id: str) -> Order:
        ...

    @abstractmethod
    def get_order(self, broker_order_id: str) -> Order:
        ...

    @abstractmethod
    def positions(self) -> list[Position]:
        ...

    @abstractmethod
    def pnl(self) -> float:
        """Realized P&L for the current session (for reconciliation)."""

    @abstractmethod
    def square_off_all(self) -> list[Order]:
        """Emergency exit. Used by kill-switch and EOD close."""
