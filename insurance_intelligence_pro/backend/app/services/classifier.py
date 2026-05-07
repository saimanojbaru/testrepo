"""Classify an insurer as P&C, Life, Health, Reinsurance, or Multiline.

Uses (in order):
  1. Curated mapping in tickers.json
  2. SEC SIC code (e.g. 6311 Life, 6331 P&C, 6321 Accident & Health)
  3. Heuristic on revenue mix (fallback)
"""
from __future__ import annotations

from typing import Optional


SIC_MAP = {
    "6311": "Life",        # Life Insurance
    "6321": "Health",      # Accident & Health Insurance
    "6331": "P&C",         # Fire, Marine, Casualty Insurance
    "6411": "Multiline",   # Insurance Agents/Brokers
    "6361": "P&C",         # Title Insurance
    "6399": "Multiline",   # Insurance Carriers, NEC
}


def classify(sic: Optional[str], curated: Optional[str],
             history: Optional[list] = None) -> str:
    if curated and curated != "Unknown":
        return curated
    if sic and sic in SIC_MAP:
        return SIC_MAP[sic]
    if history:
        # Revenue-mix heuristic: if investment income > 40% of premiums, lean Life.
        latest = history[-1] if history else {}
        prem = latest.get("premiums_earned") or 0
        inv = latest.get("investment_income") or 0
        if prem and inv / prem > 0.4:
            return "Life"
        if prem:
            return "P&C"
    return "Unknown"
