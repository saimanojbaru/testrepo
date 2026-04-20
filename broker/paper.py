"""Paper broker — simulates fills against a price feed. Used for training and tests.

Behavior:
  * MARKET orders fill at the next tick/bar after an optional slippage bump.
  * LIMIT orders fill when price crosses the limit.
  * Tracks positions, realized P&L, unrealized P&L.
  * Emits Trade events every time a position closes — observable by the trading agent.

The paper broker applies the same cost model as live — so backtest/paper/live P&L
is directly comparable.
"""
from __future__ import annotations

import uuid
from dataclasses import dataclass, field
from typing import Callable

from broker.base import Broker, Order, OrderSide, OrderStatus, OrderType, Position, Product
from costs import CostConfig, Trade, TradeSide, compute_costs


@dataclass
class FillEvent:
    order: Order
    fill_price: float
    quantity: int
    costs: float
    is_closing: bool
    realized_pnl: float = 0.0


FillListener = Callable[[FillEvent], None]


@dataclass
class PaperBroker(Broker):
    last_price_by_instrument: dict[str, float] = field(default_factory=dict)
    slippage_bps: float = 5.0  # 0.05% default slippage
    cost_cfg: CostConfig = field(default_factory=CostConfig)
    listener: FillListener | None = None

    _orders: dict[str, Order] = field(default_factory=dict)
    _positions: dict[str, Position] = field(default_factory=dict)
    _realized: float = 0.0

    name: str = "paper"

    def update_price(self, instrument_key: str, price: float) -> None:
        self.last_price_by_instrument[instrument_key] = price
        if instrument_key in self._positions:
            pos = self._positions[instrument_key]
            pos.last_price = price
            pos.unrealized_pnl = (price - pos.avg_price) * pos.quantity

    # ---- Broker interface ----
    def place_order(self, order: Order) -> Order:
        if not order.idempotency_key:
            order.idempotency_key = uuid.uuid4().hex
        order.broker_order_id = order.broker_order_id or f"paper-{uuid.uuid4().hex[:12]}"
        order.status = OrderStatus.SENT
        self._orders[order.broker_order_id] = order

        ref = self.last_price_by_instrument.get(order.instrument_key)
        if ref is None:
            order.status = OrderStatus.REJECTED
            order.rejection_reason = "no reference price on paper broker"
            return order

        if order.order_type is OrderType.MARKET:
            fill_px = ref * (1 + self.slippage_bps / 10_000) if order.side is OrderSide.BUY else ref * (1 - self.slippage_bps / 10_000)
            self._fill(order, fill_px)
        elif order.order_type is OrderType.LIMIT:
            limit = order.price or ref
            crosses = (
                (order.side is OrderSide.BUY and ref <= limit)
                or (order.side is OrderSide.SELL and ref >= limit)
            )
            if crosses:
                self._fill(order, limit)
            else:
                order.status = OrderStatus.OPEN
        else:  # SL / SL_M — simplified: act as MARKET when triggered
            trigger = order.trigger_price or ref
            triggered = (
                (order.side is OrderSide.BUY and ref >= trigger)
                or (order.side is OrderSide.SELL and ref <= trigger)
            )
            if triggered:
                self._fill(order, ref)
            else:
                order.status = OrderStatus.OPEN

        return order

    def modify_order(self, broker_order_id: str, **changes) -> Order:
        order = self._orders[broker_order_id]
        for k, v in changes.items():
            if hasattr(order, k):
                setattr(order, k, v)
        return order

    def cancel_order(self, broker_order_id: str) -> Order:
        order = self._orders.get(broker_order_id)
        if not order:
            raise KeyError(broker_order_id)
        if order.status in {OrderStatus.FILLED, OrderStatus.CANCELLED, OrderStatus.REJECTED}:
            return order
        order.status = OrderStatus.CANCELLED
        return order

    def get_order(self, broker_order_id: str) -> Order:
        return self._orders[broker_order_id]

    def positions(self) -> list[Position]:
        return [p for p in self._positions.values() if p.quantity != 0]

    def pnl(self) -> float:
        return self._realized

    def square_off_all(self) -> list[Order]:
        exits: list[Order] = []
        for inst, pos in list(self._positions.items()):
            if pos.quantity == 0:
                continue
            side = OrderSide.SELL if pos.quantity > 0 else OrderSide.BUY
            exit = Order(
                instrument_key=inst,
                side=side,
                quantity=abs(pos.quantity),
                order_type=OrderType.MARKET,
                product=Product.INTRADAY,
                tag="kill_switch",
            )
            exits.append(self.place_order(exit))
        return exits

    # ---- internals ----
    def _fill(self, order: Order, fill_px: float) -> None:
        order.status = OrderStatus.FILLED
        order.filled_quantity = order.quantity
        order.avg_fill_price = fill_px

        leg = Trade(
            side=TradeSide.BUY if order.side is OrderSide.BUY else TradeSide.SELL,
            qty=order.quantity,
            premium=fill_px,
        )
        cost = compute_costs(leg, self.cost_cfg).total

        inst = order.instrument_key
        pos = self._positions.get(inst)
        is_closing = False
        realized_on_this_fill = 0.0

        signed_qty = order.quantity if order.side is OrderSide.BUY else -order.quantity
        if pos is None or pos.quantity == 0:
            # Opening a new position
            self._positions[inst] = Position(
                instrument_key=inst,
                quantity=signed_qty,
                avg_price=fill_px,
                last_price=fill_px,
                unrealized_pnl=0.0,
            )
        elif (pos.quantity > 0 and signed_qty < 0) or (pos.quantity < 0 and signed_qty > 0):
            # Reducing / closing
            close_qty = min(abs(signed_qty), abs(pos.quantity))
            direction = 1 if pos.quantity > 0 else -1
            realized_on_this_fill = direction * (fill_px - pos.avg_price) * close_qty - cost
            self._realized += realized_on_this_fill
            pos.quantity += signed_qty
            if pos.quantity == 0:
                is_closing = True
                pos.realized_pnl += realized_on_this_fill
            pos.last_price = fill_px
        else:
            # Adding to position — weighted avg
            total_qty = pos.quantity + signed_qty
            pos.avg_price = (
                pos.avg_price * pos.quantity + fill_px * signed_qty
            ) / total_qty
            pos.quantity = total_qty

        if self.listener:
            self.listener(
                FillEvent(
                    order=order,
                    fill_price=fill_px,
                    quantity=order.quantity,
                    costs=cost,
                    is_closing=is_closing,
                    realized_pnl=realized_on_this_fill,
                )
            )
