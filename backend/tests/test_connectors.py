"""Connector registry + CSV replay smoke tests."""

from __future__ import annotations

import asyncio
from pathlib import Path

from app.connectors import (
    CsvReplayConnector,
    available_connectors,
    get_connector,
)


def test_registry_has_three_connectors():
    metas = available_connectors()
    ids = {m.id for m in metas}
    assert {"upstox_live", "yahoo_quote", "csv_replay"}.issubset(ids)


def test_get_connector_returns_instance():
    c = get_connector(
        "csv_replay",
        path=str(Path(__file__).parent.parent / "data" / "sample_ohlcv.csv"),
        instrument="NIFTY",
    )
    assert isinstance(c, CsvReplayConnector)


def test_csv_replay_emits_ticks(tmp_path):
    src = Path(__file__).parent.parent / "data" / "sample_ohlcv.csv"
    received: list = []

    async def go():
        connector = CsvReplayConnector(path=str(src), instrument="NIFTY")

        async def on_tick(t):
            received.append(t)

        await connector.run(on_tick)

    asyncio.run(go())
    assert len(received) > 30
    assert received[0].instrument == "NIFTY"


def test_unknown_connector_raises():
    import pytest
    with pytest.raises(ValueError):
        get_connector("nonexistent")
