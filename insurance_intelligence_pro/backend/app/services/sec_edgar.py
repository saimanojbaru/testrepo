"""SEC EDGAR XBRL Company Facts client.

We rely on the *companyfacts* endpoint:
    https://data.sec.gov/api/xbrl/companyfacts/CIK##########.json

It returns every tagged numerical fact ever filed by the company across
all reporting standards (us-gaap, ifrs-full, dei, etc.). We pluck the tags
relevant to insurance accounting and return a tidy time-series.
"""
from __future__ import annotations

import logging
from typing import Dict, List, Optional

from ..cache import cached
from ..config import settings
from .http_client import fetch_json


_log = logging.getLogger(__name__)


# Mapping of the canonical metric -> ranked list of XBRL tags to try.
# Different filers tag the same line item differently; we try in order
# and take the first hit.
INSURANCE_TAGS: Dict[str, List[str]] = {
    "premiums_earned": [
        "PremiumsEarnedNet",
        "PremiumsEarnedNetPropertyAndCasualty",
        "PremiumsEarnedNetLife",
        "PremiumsWrittenNet",
        "Revenues",
    ],
    "premiums_written": [
        "PremiumsWrittenNet",
        "PremiumsWrittenGross",
        "PremiumsEarnedNet",
    ],
    "losses_incurred": [
        "PolicyholderBenefitsAndClaimsIncurredNet",
        "LiabilityForClaimsAndClaimsAdjustmentExpenseClaimsIncurredNet",
        "InsuranceCommissionsAndFees",
        "BenefitsLossesAndExpenses",
    ],
    "underwriting_expenses": [
        "InsuranceCommissionsAndFees",
        "OperatingExpenses",
        "GeneralAndAdministrativeExpense",
        "OtherCostAndExpenseOperating",
    ],
    "investment_income": [
        "NetInvestmentIncome",
        "InterestAndDividendIncomeOperating",
        "InvestmentIncomeInterest",
    ],
    "net_income": [
        "NetIncomeLoss",
        "ProfitLoss",
    ],
    "reserves": [
        "LiabilityForFuturePolicyBenefitsAndUnpaidClaimsAndClaimsAdjustmentExpense",
        "LiabilityForUnpaidClaimsAndClaimsAdjustmentExpense",
        "FuturePolicyBenefitsLiability",
    ],
    "stockholders_equity": [
        "StockholdersEquity",
        "StockholdersEquityIncludingPortionAttributableToNoncontrollingInterest",
    ],
    "total_assets": ["Assets"],
    "total_liabilities": ["Liabilities"],
}


def _company_facts_url(cik: str) -> str:
    return f"{settings.sec_base_url}/api/xbrl/companyfacts/CIK{cik}.json"


def _submissions_url(cik: str) -> str:
    return f"{settings.sec_base_url}/submissions/CIK{cik}.json"


@cached("sec-facts", persist=True,
        memory_ttl=60 * 60 * 6, disk_ttl=60 * 60 * 24 * 7)
async def fetch_company_facts(cik: str) -> Optional[dict]:
    return await fetch_json(_company_facts_url(cik))


@cached("sec-submissions", persist=True,
        memory_ttl=60 * 60 * 6, disk_ttl=60 * 60 * 24 * 7)
async def fetch_submissions(cik: str) -> Optional[dict]:
    return await fetch_json(_submissions_url(cik))


def _select_units(fact: dict) -> Optional[List[dict]]:
    """Pick the preferred unit (USD > USD/shares > shares > first)."""
    units = fact.get("units") or {}
    for u in ("USD", "USD/shares", "shares"):
        if u in units:
            return units[u]
    if units:
        return next(iter(units.values()))
    return None


def _annual_points(records: List[dict]) -> List[dict]:
    """Filter to FY annual records (form 10-K, period length ~ 12 months).

    We dedupe by fiscal year, preferring the latest filed value.
    """
    by_fy: Dict[int, dict] = {}
    for r in records:
        fp = r.get("fp")
        form = r.get("form", "")
        fy = r.get("fy")
        if not fy:
            continue
        if not (fp == "FY" or form.startswith("10-K") or form.startswith("20-F")):
            continue
        existing = by_fy.get(fy)
        if existing is None or (r.get("filed", "") > existing.get("filed", "")):
            by_fy[fy] = r
    out = []
    for fy in sorted(by_fy.keys()):
        r = by_fy[fy]
        out.append({
            "fy": int(fy),
            "value": float(r.get("val", 0.0)),
            "end": r.get("end"),
            "filed": r.get("filed"),
            "accn": r.get("accn"),
            "form": r.get("form"),
        })
    return out


def extract_metric(facts: dict, candidates: List[str]) -> List[dict]:
    """Pull the first matching XBRL tag from companyfacts data."""
    if not facts:
        return []
    us_gaap = (facts.get("facts") or {}).get("us-gaap") or {}
    ifrs = (facts.get("facts") or {}).get("ifrs-full") or {}
    for tag in candidates:
        for taxonomy in (us_gaap, ifrs):
            if tag in taxonomy:
                units = _select_units(taxonomy[tag])
                if units:
                    return _annual_points(units)
    return []


def build_fiscal_history(facts: dict) -> List[dict]:
    """Assemble a tidy FY-indexed table of insurance metrics."""
    series: Dict[str, List[dict]] = {
        key: extract_metric(facts, tags) for key, tags in INSURANCE_TAGS.items()
    }
    fys = sorted({p["fy"] for s in series.values() for p in s})
    rows: List[dict] = []
    for fy in fys:
        row = {"fy": fy}
        for key, points in series.items():
            value = next((p["value"] for p in points if p["fy"] == fy), None)
            row[key] = value
        rows.append(row)
    return rows


def latest_filing_meta(submissions: Optional[dict]) -> Dict[str, Optional[str]]:
    if not submissions:
        return {}
    recent = (submissions.get("filings") or {}).get("recent") or {}
    forms = recent.get("form") or []
    accs = recent.get("accessionNumber") or []
    dates = recent.get("filingDate") or []
    primary_docs = recent.get("primaryDocument") or []
    for i, form in enumerate(forms):
        if form in ("10-K", "20-F"):
            return {
                "form": form,
                "filed": dates[i] if i < len(dates) else None,
                "accession": accs[i] if i < len(accs) else None,
                "primary_document": primary_docs[i] if i < len(primary_docs) else None,
            }
    return {}


async def fetch_full_profile(cik: str) -> dict:
    facts = await fetch_company_facts(cik)
    submissions = await fetch_submissions(cik)
    history = build_fiscal_history(facts) if facts else []
    return {
        "facts_available": bool(facts),
        "fiscal_history": history,
        "submissions": {
            "name": (submissions or {}).get("name"),
            "sic": (submissions or {}).get("sic"),
            "sicDescription": (submissions or {}).get("sicDescription"),
            "exchanges": (submissions or {}).get("exchanges"),
            "category": (submissions or {}).get("category"),
            "tickers": (submissions or {}).get("tickers"),
            "latest_filing": latest_filing_meta(submissions),
        },
    }
