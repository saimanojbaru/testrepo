"""Glue between SEC EDGAR data and the canonical metric shape used by the
KPI / insight / peer engines.

If SEC data is missing or sparse, we fall back to the curated sample dataset.
That guarantees the app always renders something useful.
"""
from __future__ import annotations

import json
import logging
from functools import lru_cache
from pathlib import Path
from typing import Dict, List, Optional, Tuple

from ..config import DATA_DIR
from .sec_edgar import fetch_full_profile
from .classifier import classify


_log = logging.getLogger(__name__)


@lru_cache(maxsize=1)
def _sample_data() -> dict:
    return json.loads((Path(DATA_DIR) / "sample_data.json").read_text())


def _sample_for(ticker: str, insurer_type: str) -> dict:
    metrics = _sample_data()["metrics"]
    if ticker.upper() in metrics:
        return metrics[ticker.upper()]
    fallback_key = "DEFAULT_LIFE" if insurer_type == "Life" else "DEFAULT_PC"
    return metrics[fallback_key]


def _normalize_history(rows: List[dict]) -> List[dict]:
    """Convert SEC EDGAR rows to the canonical schema, scaled to USD millions."""
    out: List[dict] = []
    for r in rows:
        # Skip rows where we have basically nothing useful
        if not any([r.get("premiums_earned"), r.get("losses_incurred"),
                     r.get("net_income")]):
            continue
        out.append({
            "fy": int(r["fy"]),
            "premiums": _to_millions(r.get("premiums_earned")),
            "losses": _to_millions(r.get("losses_incurred")),
            "expenses": _to_millions(r.get("underwriting_expenses")),
            "investment_income": _to_millions(r.get("investment_income")),
            "net_income": _to_millions(r.get("net_income")),
            "reserves": _to_millions(r.get("reserves")),
            "equity": _to_millions(r.get("stockholders_equity")),
            "assets": _to_millions(r.get("total_assets")),
        })
    return out


def _to_millions(value: Optional[float]) -> Optional[float]:
    if value is None:
        return None
    # SEC values are absolute USD; convert to millions for readability.
    return round(value / 1_000_000.0, 2)


async def get_company_financials(company: dict) -> Tuple[Dict, bool]:
    """Return (financials_payload, is_estimated).

    financials_payload structure:
        {
          "insurer_type": "P&C",
          "fiscal_year": 2024,
          "fiscal_history": [ { fy, premiums, losses, expenses, ... }, ... ],
          "submissions": { ... metadata ... }
        }
    """
    ticker = company["ticker"]
    cik = company["cik"]
    profile = await fetch_full_profile(cik)
    sec_history = _normalize_history(profile.get("fiscal_history") or [])
    submissions = profile.get("submissions") or {}

    insurer_type = classify(
        sic=submissions.get("sic"),
        curated=company.get("insurer_type"),
        history=profile.get("fiscal_history") or [],
    )

    # Need at least 2 usable years for KPI trends. Otherwise we estimate.
    if len(sec_history) >= 2:
        fy = sec_history[-1]["fy"]
        return ({
            "insurer_type": insurer_type,
            "fiscal_year": fy,
            "fiscal_history": sec_history,
            "submissions": submissions,
            "source": "SEC EDGAR",
        }, False)

    sample = _sample_for(ticker, insurer_type)
    history = sample["fiscal_history"]
    return ({
        "insurer_type": sample.get("insurer_type", insurer_type),
        "fiscal_year": history[-1]["fy"] if history else None,
        "fiscal_history": history,
        "submissions": submissions,
        "source": "Estimated (curated sample)",
    }, True)
