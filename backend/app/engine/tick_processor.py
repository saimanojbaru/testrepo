"""Aggregates raw ticks into short-interval candles."""

from __future__ import annotations

from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone
from typing import Callable

from ..domain.signals import Candle, Tick


@dataclass(slots=True)
class _Partial:
    ts: datetime
    open: float
    high: float
    low: float
    close: float
    volume: float = 0.0


class TickProcessor:
    """Per-instrument multi-timeframe aggregator.

    Emits closed candles to a callback. Call on_tick(tick) for each incoming
    tick; closed candles fire when a tick crosses the next bucket boundary.
    """

    def __init__(
        self,
        bucket_seconds: int = 60,
        on_candle: Callable[[Candle], None] | None = None,
    ) -> None:
        self.bucket_seconds = bucket_seconds
        self.on_candle = on_candle
        self._partials: dict[str, _Partial] = {}
        self._buckets: dict[str, list[Candle]] = defaultdict(list)

    def _bucket_start(self, ts: datetime) -> datetime:
        epoch = ts.replace(microsecond=0)
        s = (epoch.minute * 60 + epoch.second) % self.bucket_seconds
        return epoch - timedelta(seconds=s)

    def on_tick(self, tick: Tick) -> Candle | None:
        bucket = self._bucket_start(tick.ts)
        partial = self._partials.get(tick.instrument)
        closed: Candle | None = None

        if partial is None:
            self._partials[tick.instrument] = _Partial(
                ts=bucket,
                open=tick.price,
                high=tick.price,
                low=tick.price,
                close=tick.price,
            )
            return None

        if bucket != partial.ts:
            # Bucket boundary crossed: close previous bar
            closed = Candle(
                instrument=tick.instrument,
                ts=partial.ts,
                open=partial.open,
                high=partial.high,
                low=partial.low,
                close=partial.close,
                volume=partial.volume,
            )
            self._buckets[tick.instrument].append(closed)
            if self.on_candle is not None:
                self.on_candle(closed)
            self._partials[tick.instrument] = _Partial(
                ts=bucket,
                open=tick.price,
                high=tick.price,
                low=tick.price,
                close=tick.price,
            )
            return closed

        # Same bucket: update OHLC
        partial.high = max(partial.high, tick.price)
        partial.low = min(partial.low, tick.price)
        partial.close = tick.price
        return None

    def history(self, instrument: str) -> list[Candle]:
        return list(self._buckets[instrument])

    def snapshot(self, instrument: str) -> Candle | None:
        p = self._partials.get(instrument)
        if p is None:
            return None
        return Candle(
            instrument=instrument,
            ts=p.ts,
            open=p.open,
            high=p.high,
            low=p.low,
            close=p.close,
            volume=p.volume,
        )


def utc_now() -> datetime:
    return datetime.now(timezone.utc)
