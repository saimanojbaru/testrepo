"""In-process pub/sub bridging TradingAgent events -> WebSocket subscribers.

The agent's event_hook is set to `publish`; subscribers get an asyncio.Queue.
"""
from __future__ import annotations

import asyncio
import datetime as dt
from dataclasses import dataclass, field
from typing import Any


@dataclass
class Event:
    kind: str
    ts: dt.datetime
    payload: Any


class StateBus:
    def __init__(self) -> None:
        self._subs: list[asyncio.Queue[Event]] = []
        self._snapshot: dict[str, Any] = {}

    def publish(self, kind: str, payload: Any) -> None:
        event = Event(kind=kind, ts=dt.datetime.utcnow(), payload=payload)
        # Update snapshot view for new subscribers
        if kind == "bar" and hasattr(payload, "close"):
            self._snapshot["last_close"] = float(payload.close)
            self._snapshot["last_ts"] = event.ts.isoformat()
            self._snapshot["realized_pnl"] = getattr(payload, "realized_pnl", 0.0)
            self._snapshot["unrealized_pnl"] = getattr(payload, "unrealized_pnl", 0.0)
        for q in list(self._subs):
            try:
                q.put_nowait(event)
            except asyncio.QueueFull:
                pass

    def subscribe(self) -> asyncio.Queue[Event]:
        q: asyncio.Queue[Event] = asyncio.Queue(maxsize=500)
        self._subs.append(q)
        return q

    def unsubscribe(self, q: asyncio.Queue[Event]) -> None:
        if q in self._subs:
            self._subs.remove(q)

    def snapshot(self) -> dict[str, Any]:
        return dict(self._snapshot)
