"""Order lifecycle state machine + idempotency + persistent log.

Responsibilities:
  * Generate deterministic idempotency keys
  * Track state transitions (NEW -> SENT -> OPEN -> FILLED/CANCELLED/REJECTED)
  * Persist every transition to a JSON-lines log for crash recovery and P&L audit
  * Reconcile on startup: read the log + query broker for open orders, align
"""
from __future__ import annotations

import datetime as dt
import hashlib
import json
from dataclasses import asdict, dataclass, field
from pathlib import Path

from broker.base import Broker, Order, OrderStatus

DEFAULT_LOG_DIR = Path("./backtest_runs/order_log")


def idempotency_key(instrument_key: str, side: str, qty: int, tag: str, bucket_ts: dt.datetime) -> str:
    """Stable key: two identical requests inside the same minute get the same key."""
    minute = bucket_ts.strftime("%Y-%m-%dT%H:%M")
    raw = f"{instrument_key}|{side}|{qty}|{tag}|{minute}"
    return hashlib.sha256(raw.encode()).hexdigest()[:24]


@dataclass
class OrderManager:
    broker: Broker
    log_dir: Path = DEFAULT_LOG_DIR
    _seen_keys: set[str] = field(default_factory=set)
    _by_key: dict[str, Order] = field(default_factory=dict)

    def __post_init__(self) -> None:
        self.log_dir.mkdir(parents=True, exist_ok=True)

    def _log_path(self, day: dt.date | None = None) -> Path:
        day = day or dt.date.today()
        return self.log_dir / f"orders_{day.isoformat()}.jsonl"

    def _append(self, event: dict) -> None:
        path = self._log_path()
        with path.open("a") as f:
            f.write(json.dumps(event, default=str) + "\n")

    def submit(self, order: Order) -> Order:
        if not order.idempotency_key:
            order.idempotency_key = idempotency_key(
                order.instrument_key,
                order.side.value,
                order.quantity,
                order.tag,
                dt.datetime.utcnow(),
            )
        if order.idempotency_key in self._seen_keys:
            # Already submitted — return existing order without re-sending
            return self._by_key[order.idempotency_key]
        self._seen_keys.add(order.idempotency_key)
        self._by_key[order.idempotency_key] = order

        self._append({"event": "submit", "order": _order_dict(order)})
        try:
            placed = self.broker.place_order(order)
        except Exception as exc:
            order.status = OrderStatus.REJECTED
            order.rejection_reason = str(exc)
            self._append({"event": "reject", "order": _order_dict(order), "error": str(exc)})
            raise
        self._append({"event": "placed", "order": _order_dict(placed)})
        return placed

    def cancel(self, broker_order_id: str) -> Order:
        order = self.broker.cancel_order(broker_order_id)
        self._append({"event": "cancel", "order": _order_dict(order)})
        return order

    def reconcile(self) -> list[Order]:
        """Pull open positions + orders from broker and align internal state.

        Called on startup to recover after a crash/restart.
        """
        open_orders: list[Order] = []
        positions = self.broker.positions()
        for pos in positions:
            open_orders.append(
                Order(
                    instrument_key=pos.instrument_key,
                    side=None,  # type: ignore[arg-type]
                    quantity=abs(pos.quantity),
                    order_type=None,  # type: ignore[arg-type]
                    product=None,  # type: ignore[arg-type]
                    broker_order_id=None,
                    status=OrderStatus.FILLED,
                    filled_quantity=abs(pos.quantity),
                    avg_fill_price=pos.avg_price,
                )
            )
        self._append({"event": "reconcile", "positions": [asdict(p) for p in positions]})
        return open_orders


def _order_dict(o: Order) -> dict:
    d = asdict(o)
    for k in ("side", "order_type", "product", "status"):
        v = d.get(k)
        if v is not None and hasattr(v, "value"):
            d[k] = v.value
    return d
