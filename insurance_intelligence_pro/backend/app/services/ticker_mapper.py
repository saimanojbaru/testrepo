"""Map a free-form company query to a CIK + ticker + insurer type.

Strategy (in order):
  1. Exact ticker match in local fallback list
  2. Substring name match in local fallback list
  3. SEC's company_tickers.json (cached on first hit)
  4. None
"""
from __future__ import annotations

import json
import logging
from functools import lru_cache
from pathlib import Path
from typing import Optional

from ..cache import cached
from ..config import DATA_DIR, settings
from ..database import find_company, upsert_company
from .http_client import fetch_json


_log = logging.getLogger(__name__)


@lru_cache(maxsize=1)
def _local_index() -> dict:
    path = Path(DATA_DIR) / "tickers.json"
    return json.loads(path.read_text())


def _normalize_cik(cik: str | int) -> str:
    return str(cik).strip().lstrip("0").zfill(10)


def _local_lookup(query: str) -> Optional[dict]:
    q = query.strip().upper()
    if not q:
        return None
    data = _local_index()
    # Try exact ticker
    for c in data["companies"]:
        if c["ticker"].upper() == q:
            return _to_record(c)
    # Try ticker with dot/dash variants (BRK.B vs BRKB)
    qp = q.replace("-", ".")
    for c in data["companies"]:
        if c["ticker"].upper() == qp:
            return _to_record(c)
    # Substring on name (prefer shortest match)
    matches = [c for c in data["companies"] if q in c["name"].upper()]
    if matches:
        matches.sort(key=lambda c: len(c["name"]))
        return _to_record(matches[0])
    return None


def _to_record(c: dict) -> dict:
    return {
        "cik": _normalize_cik(c["cik"]),
        "ticker": c["ticker"].upper(),
        "name": c["name"],
        "insurer_type": c.get("type", "Unknown"),
    }


@cached("sec-tickers", persist=True, memory_ttl=60 * 60 * 24,
        disk_ttl=60 * 60 * 24 * 30)
async def _fetch_sec_tickers() -> Optional[dict]:
    return await fetch_json(settings.sec_company_tickers_url)


async def _sec_lookup(query: str) -> Optional[dict]:
    payload = await _fetch_sec_tickers()
    if not payload:
        return None
    q = query.strip().upper()
    best = None
    for entry in payload.values():
        ticker = str(entry.get("ticker", "")).upper()
        title = str(entry.get("title", ""))
        cik = str(entry.get("cik_str", entry.get("cik", "")))
        if not ticker or not cik:
            continue
        if ticker == q:
            return {"cik": _normalize_cik(cik), "ticker": ticker,
                    "name": title, "insurer_type": "Unknown"}
        if q in title.upper() and (best is None or len(title) < len(best["name"])):
            best = {"cik": _normalize_cik(cik), "ticker": ticker,
                    "name": title, "insurer_type": "Unknown"}
    return best


async def resolve_company(query: str) -> Optional[dict]:
    """Resolve a free-form query to a normalized company record."""
    if not query:
        return None
    cached_row = find_company(query)
    if cached_row:
        return {
            "cik": _normalize_cik(cached_row["cik"]),
            "ticker": cached_row["ticker"],
            "name": cached_row["name"],
            "insurer_type": cached_row.get("insurer_type") or "Unknown",
            "sic": cached_row.get("sic"),
        }
    local = _local_lookup(query)
    if local:
        upsert_company(local["cik"], local["ticker"], local["name"],
                       sic=None, insurer_type=local["insurer_type"])
        return local
    sec = await _sec_lookup(query)
    if sec:
        upsert_company(sec["cik"], sec["ticker"], sec["name"],
                       sic=None, insurer_type=sec["insurer_type"])
        return sec
    return None


def list_local_companies() -> list[dict]:
    return [_to_record(c) for c in _local_index()["companies"]]


def peer_group_for(insurer_type: str) -> list[str]:
    return list(_local_index().get("peer_groups", {}).get(insurer_type, []))
