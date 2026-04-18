"""
Abstract broker interface.
All brokers (Upstox, Kite, PaperBroker) implement this contract.
"""

from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Optional, List, Literal
from datetime import datetime


@dataclass
class Order:
    """Order representation."""
    order_id: str
    instrument_key: str
    symbol: str
    side: Literal["buy", "sell"]
    quantity: int
    order_type: Literal["market", "limit", "sl", "sl-m"]
    price: Optional[float] = None
    trigger_price: Optional[float] = None
    product: Literal["mis", "cnc", "nrml"] = "mis"
    status: str = "pending"
    timestamp: Optional[datetime] = None
    filled_quantity: int = 0
    filled_price: Optional[float] = None


@dataclass
class Position:
    """Current position."""
    instrument_key: str
    symbol: str
    quantity: int
    average_price: float
    last_price: float = 0
    pnl: float = 0
    realized_pnl: float = 0
    unrealized_pnl: float = 0


@dataclass
class Quote:
    """Market quote snapshot."""
    instrument_key: str
    ltp: float
    bid: float = 0
    ask: float = 0
    volume: int = 0
    oi: int = 0
    timestamp: Optional[datetime] = None


class Broker(ABC):
    """Abstract broker interface."""

    @abstractmethod
    def place_order(self, order: Order) -> Order:
        """Place an order. Returns Order with order_id and status populated."""
        pass

    @abstractmethod
    def cancel_order(self, order_id: str) -> bool:
        """Cancel a pending order."""
        pass

    @abstractmethod
    def modify_order(self, order_id: str, **kwargs) -> Order:
        """Modify a pending order (price, qty, etc.)."""
        pass

    @abstractmethod
    def get_positions(self) -> List[Position]:
        """Fetch current open positions."""
        pass

    @abstractmethod
    def get_quote(self, instrument_key: str) -> Quote:
        """Fetch a live quote."""
        pass

    @abstractmethod
    def get_order_status(self, order_id: str) -> Order:
        """Fetch status of an order."""
        pass

    @abstractmethod
    def square_off_all(self) -> bool:
        """Emergency square-off: close all open positions immediately."""
        pass
