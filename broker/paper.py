"""
Paper broker: simulates execution against live quotes.
Used in Phase 8 (paper trading = training phase).

Order flow:
1. place_order → immediate "fill" at current quote + modeled slippage
2. Track positions in-memory
3. get_positions, get_pnl updated from live quotes
4. All trades logged to DB for reconciliation
"""

import uuid
from typing import Dict, List, Optional
from datetime import datetime

from broker.base import Broker, Order, Position, Quote


class PaperBroker(Broker):
    """
    Simulates broker execution using live market quotes + slippage model.

    Attributes:
        slippage_bps: Slippage in basis points (1 bps = 0.01%)
        quote_provider: Callable that returns live Quote for instrument_key
    """

    def __init__(
        self,
        slippage_bps: float = 5,
        quote_provider: Optional[callable] = None,
    ):
        self.slippage_bps = slippage_bps
        self.quote_provider = quote_provider
        self.orders: Dict[str, Order] = {}
        self.positions: Dict[str, Position] = {}
        self.trade_log: List[dict] = []

    def _apply_slippage(self, price: float, side: str) -> float:
        """Apply slippage to a price based on side."""
        slippage = price * self.slippage_bps / 10000
        return price + slippage if side == "buy" else price - slippage

    def place_order(self, order: Order) -> Order:
        """Simulate immediate fill at quote + slippage."""
        order.order_id = f"PAPER_{uuid.uuid4().hex[:8]}"
        order.timestamp = order.timestamp or datetime.now()

        # Get current quote
        if self.quote_provider:
            quote = self.quote_provider(order.instrument_key)
            fill_price = quote.ltp
        else:
            fill_price = order.price or 0

        # Apply slippage
        filled = self._apply_slippage(fill_price, order.side)
        order.filled_price = filled
        order.filled_quantity = order.quantity
        order.status = "filled"

        # Update positions
        self._update_position(order)

        # Log trade
        self.orders[order.order_id] = order
        self.trade_log.append({
            "order_id": order.order_id,
            "timestamp": order.timestamp,
            "symbol": order.symbol,
            "side": order.side,
            "quantity": order.quantity,
            "price": filled,
            "paper_mode": True,
        })

        return order

    def _update_position(self, order: Order):
        """Update position based on filled order."""
        key = order.instrument_key

        if key not in self.positions:
            if order.side == "buy":
                self.positions[key] = Position(
                    instrument_key=key,
                    symbol=order.symbol,
                    quantity=order.quantity,
                    average_price=order.filled_price,
                    last_price=order.filled_price,
                )
        else:
            pos = self.positions[key]
            if order.side == "buy":
                # Add to position
                new_qty = pos.quantity + order.quantity
                new_avg = (
                    (pos.average_price * pos.quantity + order.filled_price * order.quantity)
                    / new_qty
                )
                pos.quantity = new_qty
                pos.average_price = new_avg
            else:
                # Reduce position
                realized = (order.filled_price - pos.average_price) * order.quantity
                pos.realized_pnl += realized
                pos.quantity -= order.quantity

                if pos.quantity <= 0:
                    del self.positions[key]

    def cancel_order(self, order_id: str) -> bool:
        if order_id in self.orders:
            self.orders[order_id].status = "cancelled"
            return True
        return False

    def modify_order(self, order_id: str, **kwargs) -> Order:
        if order_id not in self.orders:
            raise ValueError(f"Unknown order: {order_id}")
        order = self.orders[order_id]
        for k, v in kwargs.items():
            if hasattr(order, k):
                setattr(order, k, v)
        return order

    def get_positions(self) -> List[Position]:
        return list(self.positions.values())

    def get_quote(self, instrument_key: str) -> Quote:
        if self.quote_provider:
            return self.quote_provider(instrument_key)
        return Quote(instrument_key=instrument_key, ltp=0)

    def get_order_status(self, order_id: str) -> Order:
        return self.orders.get(order_id)

    def square_off_all(self) -> bool:
        """Square off all open positions at current quotes."""
        for key, pos in list(self.positions.items()):
            exit_order = Order(
                order_id="",
                instrument_key=key,
                symbol=pos.symbol,
                side="sell" if pos.quantity > 0 else "buy",
                quantity=abs(pos.quantity),
                order_type="market",
            )
            self.place_order(exit_order)
        return True

    def get_trade_log(self) -> List[dict]:
        return self.trade_log
