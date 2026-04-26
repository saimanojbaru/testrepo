"""Yahoo Finance polling connector.

No API key required. Polls the public quote endpoint at a fixed interval and
emits ticks. Useful as a free, low-frequency fallback when Upstox isn't
authorized — e.g. for end-of-day reconciliation against an external source.
"""

from __future__ import annotations

import asyncio
from datetime import datetime, timezone
from typing import Awaitable, Callable

import httpx
from loguru import logger

from ..domain.signals import Tick
from .base import (
    ConnectorCapability,
    ConnectorMetadata,
    DataConnector,
    register_connector,
)

_DEFAULT_SYMBOLS = {
    "NIFTY": "%5ENSEI",
    "BANKNIFTY": "%5ENSEBANK",
    "SENSEX": "%5EBSESN",
}


@register_connector
class YahooQuoteConnector(DataConnector):
    meta = ConnectorMetadata(
        id="yahoo_quote",
        name="Yahoo Finance polling",
        description="Free index quotes via Yahoo /v7/finance/quote (no auth).",
        capabilities=ConnectorCapability.LIVE_TICKS,
    )

    URL = "https://query1.finance.yahoo.com/v7/finance/quote"

    def __init__(
        self,
        symbols: dict[str, str] | None = None,
        poll_seconds: int = 5,
    ) -> None:
        self.symbols = symbols or _DEFAULT_SYMBOLS
        self.poll_seconds = poll_seconds
        self._stopped = asyncio.Event()

    async def run(self, on_tick: Callable[[Tick], Awaitable[None] | None]) -> None:
        self._stopped.clear()
        async with httpx.AsyncClient(timeout=10) as client:
            while not self._stopped.is_set():
                try:
                    yahoo_syms = ",".join(self.symbols.values())
                    resp = await client.get(
                        self.URL, params={"symbols": yahoo_syms}
                    )
                    resp.raise_for_status()
                    body = resp.json()
                    results = (
                        body.get("quoteResponse", {}).get("result") or []
                    )
                    for r in results:
                        sym_yahoo = r.get("symbol")
                        ltp = r.get("regularMarketPrice")
                        if sym_yahoo is None or ltp is None:
                            continue
                        # reverse-lookup our symbol
                        ours = next(
                            (k for k, v in self.symbols.items()
                             if v.replace("%5E", "^") == sym_yahoo
                             or v == sym_yahoo),
                            None,
                        )
                        if ours is None:
                            continue
                        tick = Tick(
                            instrument=ours,
                            price=float(ltp),
                            ts=datetime.now(timezone.utc),
                        )
                        res = on_tick(tick)
                        if asyncio.iscoroutine(res):
                            await res
                except Exception as e:  # noqa: BLE001
                    logger.warning(f"yahoo connector error: {e}")
                await asyncio.sleep(self.poll_seconds)

    def stop(self) -> None:
        self._stopped.set()
