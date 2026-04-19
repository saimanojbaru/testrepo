"""Upstox historical candles via REST — free with Upstox developer app.

Endpoint: GET /v2/historical-candle/{instrument_key}/{interval}/{to_date}/{from_date}
Docs: https://upstox.com/developer/api-documentation/historical-candle-data

Intervals supported: 1minute, 30minute, day, week, month.
For scalping we want 1minute; this helper slices multi-day requests into
calendar-month chunks to respect Upstox's per-call window cap.
"""
from __future__ import annotations

import datetime as dt
from dataclasses import dataclass

import httpx
import pandas as pd
from tenacity import retry, stop_after_attempt, wait_exponential

from config import get_settings

BASE_URL = "https://api.upstox.com/v2/historical-candle"

# Upstox V2 returns columns in this fixed order.
_CANDLE_COLS = ["ts", "open", "high", "low", "close", "volume", "open_interest"]


@dataclass(frozen=True)
class Candle:
    ts: dt.datetime
    open: float
    high: float
    low: float
    close: float
    volume: int
    open_interest: int | None


def parse_candles(payload: dict) -> pd.DataFrame:
    """Pure parser; payload is the JSON returned by Upstox."""
    candles = payload.get("data", {}).get("candles", [])
    df = pd.DataFrame(candles, columns=_CANDLE_COLS)
    if not df.empty:
        df["ts"] = pd.to_datetime(df["ts"], utc=True).dt.tz_convert("Asia/Kolkata")
        for col in ("open", "high", "low", "close"):
            df[col] = df[col].astype(float)
        df["volume"] = df["volume"].astype("int64")
    return df


@retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=1, max=8))
def fetch_window(
    instrument_key: str,
    interval: str,
    from_date: dt.date,
    to_date: dt.date,
    client: httpx.Client | None = None,
) -> pd.DataFrame:
    token = get_settings().upstox_access_token
    if not token:
        raise RuntimeError("UPSTOX_ACCESS_TOKEN not configured; complete OAuth flow")
    url = f"{BASE_URL}/{instrument_key}/{interval}/{to_date.isoformat()}/{from_date.isoformat()}"
    headers = {"Accept": "application/json", "Authorization": f"Bearer {token}"}

    owns = client is None
    c = client or httpx.Client(timeout=15.0)
    try:
        resp = c.get(url, headers=headers)
        resp.raise_for_status()
        return parse_candles(resp.json())
    finally:
        if owns:
            c.close()


def fetch_range(
    instrument_key: str,
    interval: str,
    start: dt.date,
    end: dt.date,
) -> pd.DataFrame:
    """Slice a long range into monthly chunks and concatenate."""
    frames: list[pd.DataFrame] = []
    cursor = start
    while cursor <= end:
        chunk_end = min(end, cursor + dt.timedelta(days=30))
        frames.append(fetch_window(instrument_key, interval, cursor, chunk_end))
        cursor = chunk_end + dt.timedelta(days=1)
    if not frames:
        return pd.DataFrame(columns=_CANDLE_COLS)
    return pd.concat(frames, ignore_index=True).sort_values("ts").reset_index(drop=True)
