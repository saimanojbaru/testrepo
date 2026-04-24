"""Shared pytest fixtures."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone
from typing import Iterable

import pytest

from app.domain.signals import Candle


def _minute(i: int) -> datetime:
    return datetime(2025, 1, 2, 9, 15, tzinfo=timezone.utc) + timedelta(minutes=i)


def make_candles(
    closes: Iterable[float],
    instrument: str = "NIFTY",
    wick: float = 0.05,
) -> list[Candle]:
    """Build tight-wick bars so strategy breakouts aren't swallowed by noise."""
    out: list[Candle] = []
    prev = None
    for i, c in enumerate(closes):
        o = prev if prev is not None else c
        hi = max(o, c) + wick
        lo = min(o, c) - wick
        out.append(
            Candle(
                instrument=instrument,
                ts=_minute(i),
                open=o,
                high=hi,
                low=lo,
                close=c,
            )
        )
        prev = c
    return out


@pytest.fixture
def make_candles_factory():
    return make_candles
