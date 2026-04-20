"""Upstox V2 broker adapter.

Implements the `Broker` interface against Upstox's REST API. Networked calls use
httpx + tenacity for retries. This adapter is functionally complete but needs a
valid UPSTOX_ACCESS_TOKEN (OAuth done out-of-band) to run; tests use a stub.

Docs: https://upstox.com/developer/api-documentation/v2/
Endpoints used:
  POST /v2/order/place
  PUT  /v2/order/modify
  DELETE /v2/order/cancel
  GET  /v2/order/details
  GET  /v2/portfolio/short-term-positions
  GET  /v2/trade/profit-loss
"""
from __future__ import annotations

import uuid
from dataclasses import dataclass, field
from typing import Any

import httpx
from tenacity import retry, stop_after_attempt, wait_exponential

from broker.base import Broker, Order, OrderSide, OrderStatus, OrderType, Position, Product
from config import get_settings

BASE_URL = "https://api.upstox.com"

_STATUS_MAP = {
    "complete": OrderStatus.FILLED,
    "rejected": OrderStatus.REJECTED,
    "cancelled": OrderStatus.CANCELLED,
    "open": OrderStatus.OPEN,
    "pending": OrderStatus.OPEN,
    "trigger pending": OrderStatus.OPEN,
    "partially executed": OrderStatus.PARTIAL,
}


def _translate_status(upstox_status: str) -> OrderStatus:
    return _STATUS_MAP.get(upstox_status.lower(), OrderStatus.OPEN)


@dataclass
class UpstoxBroker(Broker):
    client: httpx.Client | None = None
    _token: str = ""

    name: str = "upstox"

    def __post_init__(self) -> None:
        settings = get_settings()
        self._token = settings.upstox_access_token
        if self.client is None:
            self.client = httpx.Client(
                base_url=BASE_URL,
                timeout=10.0,
                headers={
                    "Accept": "application/json",
                    "Api-Version": "2.0",
                    "Authorization": f"Bearer {self._token}",
                },
            )

    # ---- HTTP helpers ----
    @retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=1, max=6))
    def _post(self, path: str, json: dict[str, Any]) -> dict[str, Any]:
        assert self.client is not None
        r = self.client.post(path, json=json)
        r.raise_for_status()
        return r.json()

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=1, max=6))
    def _get(self, path: str, params: dict[str, Any] | None = None) -> dict[str, Any]:
        assert self.client is not None
        r = self.client.get(path, params=params or {})
        r.raise_for_status()
        return r.json()

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=1, max=6))
    def _delete(self, path: str, params: dict[str, Any]) -> dict[str, Any]:
        assert self.client is not None
        r = self.client.delete(path, params=params)
        r.raise_for_status()
        return r.json()

    # ---- Broker interface ----
    def place_order(self, order: Order) -> Order:
        body = {
            "quantity": order.quantity,
            "product": "I" if order.product is Product.INTRADAY else "D",
            "validity": "DAY",
            "price": order.price if order.price else 0,
            "tag": order.tag or "scalp",
            "instrument_token": order.instrument_key,
            "order_type": order.order_type.value,
            "transaction_type": order.side.value,
            "disclosed_quantity": 0,
            "trigger_price": order.trigger_price if order.trigger_price else 0,
            "is_amo": False,
        }
        if not order.idempotency_key:
            order.idempotency_key = uuid.uuid4().hex
        payload = self._post("/v2/order/place", body)
        data = payload.get("data", {})
        order.broker_order_id = data.get("order_id")
        order.status = OrderStatus.SENT
        return order

    def modify_order(self, broker_order_id: str, **changes) -> Order:
        body = {"order_id": broker_order_id, **changes}
        self._post("/v2/order/modify", body)
        return self.get_order(broker_order_id)

    def cancel_order(self, broker_order_id: str) -> Order:
        self._delete("/v2/order/cancel", params={"order_id": broker_order_id})
        return self.get_order(broker_order_id)

    def get_order(self, broker_order_id: str) -> Order:
        payload = self._get("/v2/order/details", params={"order_id": broker_order_id})
        data = payload.get("data", {})
        return Order(
            instrument_key=data.get("instrument_token", ""),
            side=OrderSide(data.get("transaction_type", "BUY")),
            quantity=int(data.get("quantity", 0)),
            order_type=OrderType(data.get("order_type", "MARKET")),
            product=Product.INTRADAY if data.get("product", "I") == "I" else Product.CARRY,
            price=data.get("price"),
            trigger_price=data.get("trigger_price"),
            tag=data.get("tag", ""),
            broker_order_id=broker_order_id,
            status=_translate_status(data.get("status", "open")),
            filled_quantity=int(data.get("filled_quantity", 0)),
            avg_fill_price=data.get("average_price"),
            rejection_reason=data.get("status_message"),
        )

    def positions(self) -> list[Position]:
        payload = self._get("/v2/portfolio/short-term-positions")
        out: list[Position] = []
        for raw in payload.get("data", []):
            qty = int(raw.get("quantity", 0))
            out.append(
                Position(
                    instrument_key=raw.get("instrument_token", ""),
                    quantity=qty,
                    avg_price=float(raw.get("average_price", 0.0)),
                    last_price=float(raw.get("last_price", 0.0)),
                    unrealized_pnl=float(raw.get("unrealised", 0.0)),
                    realized_pnl=float(raw.get("realised", 0.0)),
                )
            )
        return out

    def pnl(self) -> float:
        # Upstox doesn't expose a single-endpoint session P&L; sum realized across positions.
        return sum(p.realized_pnl for p in self.positions())

    def square_off_all(self) -> list[Order]:
        out: list[Order] = []
        for p in self.positions():
            if p.quantity == 0:
                continue
            side = OrderSide.SELL if p.quantity > 0 else OrderSide.BUY
            order = Order(
                instrument_key=p.instrument_key,
                side=side,
                quantity=abs(p.quantity),
                order_type=OrderType.MARKET,
                product=Product.INTRADAY,
                tag="kill_switch",
            )
            out.append(self.place_order(order))
        return out
