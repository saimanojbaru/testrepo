"""Replay a historical OHLC CSV as a tick stream.

Used for offline reconciliation runs and deterministic backtest replays
through the same DataConnector interface as live sources.
"""

from __future__ import annotations

import asyncio
from pathlib import Path
from typing import Awaitable, Callable

from ..backtest.data_loader import load_ohlc_csv
from ..domain.signals import Tick
from .base import (
    ConnectorCapability,
    ConnectorMetadata,
    DataConnector,
    register_connector,
)


@register_connector
class CsvReplayConnector(DataConnector):
    meta = ConnectorMetadata(
        id="csv_replay",
        name="CSV replay",
        description="Replays a historical OHLC CSV bar-by-bar as ticks.",
        capabilities=ConnectorCapability.LIVE_TICKS
        | ConnectorCapability.HISTORICAL_BARS,
        config_keys=("path", "instrument", "speed"),
    )

    def __init__(
        self,
        path: str | Path,
        instrument: str | None = None,
        speed: float = 0.0,
    ) -> None:
        """speed=0 means as fast as possible; speed=1 means real-time."""
        self.path = Path(path)
        self.instrument = instrument
        self.speed = speed

    async def run(self, on_tick: Callable[[Tick], Awaitable[None] | None]) -> None:
        candles = load_ohlc_csv(self.path, instrument=self.instrument)
        prev_ts = None
        for c in candles:
            tick = Tick(instrument=c.instrument, price=c.close, ts=c.ts)
            res = on_tick(tick)
            if asyncio.iscoroutine(res):
                await res
            if self.speed > 0 and prev_ts is not None:
                gap = (c.ts - prev_ts).total_seconds() / self.speed
                if gap > 0:
                    await asyncio.sleep(min(gap, 5.0))
            prev_ts = c.ts
