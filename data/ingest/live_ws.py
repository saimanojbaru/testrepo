"""Upstox Market Feed V3 WebSocket subscriber — live ticks into Timescale.

This is a Phase-1 scaffold. The V3 feed uses protobuf; wiring the full decoder is
deferred to Phase 7 (paper trading). For MVP we define the tick dataclass and the
connect/subscribe/dispatch loop skeleton so downstream code can depend on a stable
interface now.
"""
from __future__ import annotations

import asyncio
import json
from dataclasses import dataclass
from typing import Callable

import websockets


@dataclass(frozen=True)
class Tick:
    ts: str
    instrument_key: str
    ltp: float
    bid: float | None = None
    ask: float | None = None
    bid_qty: int | None = None
    ask_qty: int | None = None
    volume: int | None = None
    open_interest: int | None = None


TickHandler = Callable[[Tick], None]


async def run_subscriber(
    authorized_url: str,
    instrument_keys: list[str],
    handler: TickHandler,
) -> None:
    """Connect, subscribe, dispatch ticks to handler. Caller obtains authorized_url
    from Upstox /v3/feed/market-data-feed/authorize."""
    async with websockets.connect(authorized_url) as ws:
        await ws.send(
            json.dumps(
                {
                    "guid": "scalp-mvp",
                    "method": "sub",
                    "data": {"mode": "ltpc", "instrumentKeys": instrument_keys},
                }
            )
        )
        async for raw in ws:
            # Protobuf decode deferred; for MVP we pass JSON text through.
            if isinstance(raw, str) and raw.startswith("{"):
                payload = json.loads(raw)
                for inst, feed in payload.get("feeds", {}).items():
                    ltpc = feed.get("ltpc") or {}
                    if "ltp" in ltpc:
                        handler(
                            Tick(
                                ts=ltpc.get("ltt", ""),
                                instrument_key=inst,
                                ltp=float(ltpc["ltp"]),
                            )
                        )


def subscribe(authorized_url: str, instrument_keys: list[str], handler: TickHandler) -> None:
    asyncio.run(run_subscriber(authorized_url, instrument_keys, handler))
