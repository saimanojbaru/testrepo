"""Upstox V3 live-tick connector — wraps the existing WS client."""

from __future__ import annotations

from typing import Awaitable, Callable

from ..domain.signals import Tick
from ..upstox.ws_client import UpstoxWebSocket
from .base import (
    ConnectorCapability,
    ConnectorMetadata,
    DataConnector,
    register_connector,
)


@register_connector
class UpstoxLiveConnector(DataConnector):
    meta = ConnectorMetadata(
        id="upstox_live",
        name="Upstox V3 WebSocket",
        description="Streams Nifty/BankNifty/FinNifty/Sensex via Upstox V3.",
        capabilities=ConnectorCapability.LIVE_TICKS
        | ConnectorCapability.REQUIRES_AUTH,
        config_keys=("UPSTOX_ACCESS_TOKEN", "UPSTOX_SYMBOLS"),
    )

    def __init__(
        self,
        access_token: str | None = None,
        symbols: list[str] | None = None,
    ) -> None:
        self._ws = UpstoxWebSocket(access_token=access_token, symbols=symbols)

    async def run(self, on_tick: Callable[[Tick], Awaitable[None] | None]) -> None:
        await self._ws.run(on_tick)
