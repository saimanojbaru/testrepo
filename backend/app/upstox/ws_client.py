"""Upstox V3 Market Data WebSocket client.

Upstox V3 streams protobuf-encoded MarketDataFeed messages. We keep the
protobuf decoding out of the hot path by reading the authorized-redirect URL
from their REST endpoint, opening the WS, and parsing the top-level JSON
envelope. For tick prices we fall back to the `ltpc` payload which Upstox
also exposes in JSON mode on the V3 feed when `type=ltpc` is requested.

Requires UPSTOX_ACCESS_TOKEN env var — obtained via the Flutter app's OAuth
flow and copied over (or re-authorized daily).

This client emits app.domain.signals.Tick objects to a user-supplied callback.
"""

from __future__ import annotations

import asyncio
import json
from datetime import datetime, timezone
from typing import Awaitable, Callable

import httpx
import websockets
from loguru import logger

from ..config import settings
from ..domain.signals import Tick

TickCallback = Callable[[Tick], Awaitable[None] | None]


class UpstoxWebSocket:
    AUTHORIZE_URL = "https://api.upstox.com/v3/feed/market-data-feed/authorize"

    def __init__(
        self,
        access_token: str | None = None,
        symbols: list[str] | None = None,
    ) -> None:
        self.access_token = access_token or settings.upstox_access_token
        self.symbols = symbols or settings.upstox_symbols
        self._task: asyncio.Task | None = None
        self._stopped = asyncio.Event()

    async def _authorized_uri(self) -> str:
        if not self.access_token:
            raise RuntimeError("UPSTOX_ACCESS_TOKEN not set — cannot stream")
        async with httpx.AsyncClient(timeout=10) as client:
            resp = await client.get(
                self.AUTHORIZE_URL,
                headers={"Authorization": f"Bearer {self.access_token}"},
            )
            resp.raise_for_status()
            data = resp.json()
            uri = data.get("data", {}).get("authorized_redirect_uri")
            if not uri:
                raise RuntimeError(f"Unexpected authorize response: {data}")
            return uri

    async def run(self, on_tick: TickCallback) -> None:
        self._stopped.clear()
        while not self._stopped.is_set():
            try:
                uri = await self._authorized_uri()
                logger.info(f"connecting Upstox WS for {len(self.symbols)} symbols")
                async with websockets.connect(uri, max_size=8 * 1024 * 1024) as ws:
                    await self._subscribe(ws)
                    async for raw in ws:
                        tick = self._decode(raw)
                        if tick is None:
                            continue
                        res = on_tick(tick)
                        if asyncio.iscoroutine(res):
                            await res
            except asyncio.CancelledError:
                raise
            except Exception as e:  # noqa: BLE001
                logger.exception(f"Upstox WS error: {e}; reconnecting in 3s")
                await asyncio.sleep(3)

    async def _subscribe(self, ws) -> None:
        msg = {
            "guid": "scalping-agent",
            "method": "sub",
            "data": {"mode": "ltpc", "instrumentKeys": self.symbols},
        }
        await ws.send(json.dumps(msg))

    def _decode(self, raw: bytes | str) -> Tick | None:
        # Upstox V3 defaults to protobuf; we request ltpc mode which is JSON.
        try:
            if isinstance(raw, bytes):
                raw = raw.decode("utf-8", errors="ignore")
            payload = json.loads(raw)
        except Exception:
            return None
        feeds = payload.get("feeds") or {}
        for inst_key, body in feeds.items():
            ltpc = (body.get("ltpc") or {})
            ltp = ltpc.get("ltp")
            if ltp is None:
                continue
            ts = ltpc.get("ltt") or int(datetime.now(timezone.utc).timestamp() * 1000)
            return Tick(
                instrument=inst_key,
                price=float(ltp),
                ts=datetime.fromtimestamp(ts / 1000, tz=timezone.utc),
            )
        return None

    def stop(self) -> None:
        self._stopped.set()
