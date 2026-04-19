"""Quandl / Nasdaq Data Link free-tier loader for index + options history.

Free tier is rate-limited (300 calls / 10s burst, 5k / day). Endpoints used:
  - NSE/NIFTY_50          : EOD NIFTY index history
  - NSE/INDIA_VIX         : VIX daily
  - BSE/BOM500325 etc.    : specific equities (if needed)

This module is a thin wrapper — tests cover the parsing logic against stubbed
JSON responses so CI needs no network or API key.
"""
from __future__ import annotations

import datetime as dt
from dataclasses import dataclass
from typing import Any

import httpx
import pandas as pd
from tenacity import retry, stop_after_attempt, wait_exponential

from config import get_settings

BASE_URL = "https://data.nasdaq.com/api/v3/datasets"


@dataclass(frozen=True)
class QuandlResponse:
    column_names: list[str]
    rows: list[list[Any]]

    def to_frame(self) -> pd.DataFrame:
        return pd.DataFrame(self.rows, columns=self.column_names)


def parse_response(payload: dict[str, Any]) -> QuandlResponse:
    """Pure parser — used by tests without hitting the network."""
    ds = payload["dataset"]
    return QuandlResponse(column_names=list(ds["column_names"]), rows=list(ds["data"]))


@retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, min=1, max=8))
def fetch_dataset(
    code: str,
    start: dt.date | None = None,
    end: dt.date | None = None,
    client: httpx.Client | None = None,
) -> pd.DataFrame:
    """Fetch a Quandl dataset code e.g. 'NSE/NIFTY_50'. Returns a DataFrame."""
    api_key = get_settings().quandl_api_key
    if not api_key:
        raise RuntimeError("QUANDL_API_KEY not configured; set in .env")

    params: dict[str, str] = {"api_key": api_key}
    if start:
        params["start_date"] = start.isoformat()
    if end:
        params["end_date"] = end.isoformat()

    owns = client is None
    c = client or httpx.Client(timeout=10.0)
    try:
        resp = c.get(f"{BASE_URL}/{code}/data.json", params=params)
        resp.raise_for_status()
        return parse_response(resp.json()).to_frame()
    finally:
        if owns:
            c.close()
