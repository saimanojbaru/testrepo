"""CSV / DataFrame loader for historical OHLC bars.

Expected schema (any CSV with a header matching these):
  ts          (ISO 8601 or unix seconds)
  open, high, low, close, volume
  instrument  (optional — otherwise passed in)
"""

from __future__ import annotations

from dataclasses import dataclass
from datetime import datetime, timezone
from pathlib import Path
from typing import Iterator

import pandas as pd

from ..domain.signals import Candle


def load_ohlc_csv(path: str | Path, instrument: str | None = None) -> list[Candle]:
    df = pd.read_csv(path)
    return _df_to_candles(df, instrument)


def _df_to_candles(df: pd.DataFrame, instrument: str | None) -> list[Candle]:
    if "ts" not in df.columns:
        raise ValueError("CSV must have a 'ts' column")
    for col in ("open", "high", "low", "close"):
        if col not in df.columns:
            raise ValueError(f"CSV missing required column: {col}")

    # Handle both ISO strings and unix timestamps
    ts = df["ts"]
    if pd.api.types.is_numeric_dtype(ts):
        ts = pd.to_datetime(ts, unit="s", utc=True)
    else:
        ts = pd.to_datetime(ts, utc=True, errors="coerce")

    candles: list[Candle] = []
    for i, row in df.iterrows():
        inst = row["instrument"] if "instrument" in df.columns else instrument
        if not inst:
            raise ValueError("instrument must be set via column or argument")
        candles.append(
            Candle(
                instrument=str(inst),
                ts=ts.iloc[i].to_pydatetime(),
                open=float(row["open"]),
                high=float(row["high"]),
                low=float(row["low"]),
                close=float(row["close"]),
                volume=float(row.get("volume", 0.0) or 0.0),
            )
        )
    return candles


@dataclass(slots=True)
class DaySlice:
    day: datetime
    candles: list[Candle]


def split_by_day(candles: list[Candle]) -> Iterator[DaySlice]:
    """Yields contiguous same-day slices. Used so intraday strategies reset daily."""
    if not candles:
        return
    current: list[Candle] = []
    current_day: datetime | None = None
    for c in candles:
        day = c.ts.astimezone(timezone.utc).date()
        if current_day is None or day != current_day:
            if current:
                yield DaySlice(
                    day=datetime.combine(current_day, datetime.min.time(), tzinfo=timezone.utc),
                    candles=current,
                )
            current = []
            current_day = day
        current.append(c)
    if current and current_day is not None:
        yield DaySlice(
            day=datetime.combine(current_day, datetime.min.time(), tzinfo=timezone.utc),
            candles=current,
        )
