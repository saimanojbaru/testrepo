"""
Upstox WebSocket V3 live tick ingestion.

Subscribes to live market feed and writes ticks to TimescaleDB.
Used during paper trading and live execution.
"""

import asyncio
import json
import logging
from typing import List, Optional, Callable
from datetime import datetime


logger = logging.getLogger(__name__)

UPSTOX_WS_URL = "wss://api.upstox.com/v3/feed/market-data-feed"


class UpstoxWebSocketClient:
    """
    Live tick subscriber for Upstox.

    Usage:
        client = UpstoxWebSocketClient(token, on_tick=my_handler)
        await client.connect()
        await client.subscribe(["NSE_INDEX|Nifty 50", "NSE_INDEX|Nifty Bank"])
        await client.run_forever()
    """

    def __init__(
        self,
        access_token: str,
        on_tick: Optional[Callable] = None,
        mode: str = "ltpc",  # 'ltpc' (LTP+close) or 'full' (L5 depth)
    ):
        self.access_token = access_token
        self.on_tick = on_tick or self._default_handler
        self.mode = mode
        self.ws = None
        self.subscribed: List[str] = []
        self.running = False

    async def connect(self):
        """Establish WebSocket connection."""
        try:
            import websockets
        except ImportError:
            raise ImportError("Install: pip install websockets")

        headers = {"Authorization": f"Bearer {self.access_token}"}
        self.ws = await websockets.connect(UPSTOX_WS_URL, extra_headers=headers)
        logger.info("[WS] Connected to Upstox market feed")

    async def subscribe(self, instrument_keys: List[str]):
        """Subscribe to given instruments."""
        if self.ws is None:
            raise RuntimeError("Not connected. Call connect() first.")

        msg = {
            "guid": f"sub_{datetime.now().timestamp()}",
            "method": "sub",
            "data": {
                "mode": self.mode,
                "instrumentKeys": instrument_keys,
            },
        }
        await self.ws.send(json.dumps(msg))
        self.subscribed.extend(instrument_keys)
        logger.info(f"[WS] Subscribed to {len(instrument_keys)} instruments")

    async def unsubscribe(self, instrument_keys: List[str]):
        """Unsubscribe from instruments."""
        msg = {
            "guid": f"unsub_{datetime.now().timestamp()}",
            "method": "unsub",
            "data": {"instrumentKeys": instrument_keys},
        }
        await self.ws.send(json.dumps(msg))

    async def run_forever(self):
        """Main receive loop. Parses ticks and invokes on_tick."""
        self.running = True
        try:
            async for raw_msg in self.ws:
                try:
                    # Upstox sends Protobuf-encoded messages; in production use the
                    # MarketDataFeed protobuf schema. For simplicity, treat as JSON.
                    tick = self._parse_tick(raw_msg)
                    if tick:
                        await self.on_tick(tick)
                except Exception as e:
                    logger.error(f"[WS] Parse error: {e}")
        except Exception as e:
            logger.error(f"[WS] Connection error: {e}")
        finally:
            self.running = False

    def _parse_tick(self, raw: bytes) -> Optional[dict]:
        """
        Parse incoming Upstox WebSocket message.

        Production note: Upstox uses Protobuf for efficient tick delivery.
        Integrate via: https://github.com/upstox/upstox-python/blob/master/MarketDataFeed.proto
        This stub handles JSON for testing.
        """
        try:
            if isinstance(raw, bytes):
                raw = raw.decode("utf-8", errors="ignore")
            data = json.loads(raw)

            feeds = data.get("feeds", {})
            if not feeds:
                return None

            # Yield one tick per instrument in the batch
            for instrument_key, feed in feeds.items():
                ltpc = feed.get("ltpc", {})
                return {
                    "instrument_key": instrument_key,
                    "ltp": ltpc.get("ltp"),
                    "ltt": ltpc.get("ltt"),
                    "cp": ltpc.get("cp"),  # previous close
                    "timestamp": datetime.now(),
                }
        except (json.JSONDecodeError, AttributeError):
            return None

    async def _default_handler(self, tick: dict):
        """Default tick handler: just log."""
        logger.debug(f"[WS] Tick: {tick}")

    async def close(self):
        """Close connection."""
        if self.ws:
            await self.ws.close()
            self.running = False


class TickStore:
    """
    In-memory tick buffer with optional DB persistence.
    Used by paper trading for live quote lookup.
    """

    def __init__(self, db_writer: Optional[Callable] = None):
        self.latest: dict = {}  # instrument_key -> latest tick
        self.db_writer = db_writer

    async def on_tick(self, tick: dict):
        """Handler to register with UpstoxWebSocketClient."""
        key = tick.get("instrument_key")
        if key:
            self.latest[key] = tick
            if self.db_writer:
                await self.db_writer(tick)

    def get_ltp(self, instrument_key: str) -> Optional[float]:
        """Get latest traded price for an instrument."""
        tick = self.latest.get(instrument_key)
        return tick.get("ltp") if tick else None


if __name__ == "__main__":
    # Smoke test (requires valid token)
    async def demo():
        from config.settings import settings
        client = UpstoxWebSocketClient(access_token="YOUR_TOKEN")
        await client.connect()
        await client.subscribe(["NSE_INDEX|Nifty 50"])
        await client.run_forever()

    # asyncio.run(demo())
    print("Live WS client defined. Integrate via: python -m data.ingest.live_ws")
