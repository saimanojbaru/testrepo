"""Peer comparison engine.

Given a set of resolved companies (or a single company plus 'auto-peers'),
fetch financials in parallel, compute KPIs, then rank them per metric.
"""
from __future__ import annotations

import asyncio
from typing import Dict, List, Optional

from .financial_extractor import get_company_financials
from .kpi_engine import compute_kpis, compute_risk_radar, composite_score
from .ticker_mapper import peer_group_for, resolve_company


async def _resolve_many(queries: List[str]) -> List[dict]:
    tasks = [resolve_company(q) for q in queries]
    results = await asyncio.gather(*tasks, return_exceptions=False)
    return [r for r in results if r]


async def _company_payload(company: dict) -> Optional[dict]:
    financials, estimated = await get_company_financials(company)
    insurer_type = financials["insurer_type"]
    history = financials["fiscal_history"]
    kpis = compute_kpis(insurer_type, history)
    risk = compute_risk_radar(insurer_type, kpis, history)
    score = composite_score(kpis, risk)
    return {
        "company": company,
        "insurer_type": insurer_type,
        "fiscal_year": financials.get("fiscal_year"),
        "score": score,
        "kpis": kpis.model_dump(),
        "risk": risk.model_dump(),
        "is_estimated": estimated,
    }


def _rank_metric(payloads: List[dict], code: str, *, lower_is_better: bool):
    values = []
    for p in payloads:
        kp = next((k for k in p["kpis"]["primary"] + p["kpis"]["secondary"]
                    if k["code"] == code), None)
        v = kp["value"] if kp else None
        values.append((p["company"]["ticker"], v))
    sortable = [(t, v) for t, v in values if v is not None]
    sortable.sort(key=lambda x: x[1], reverse=not lower_is_better)
    rank = {t: i + 1 for i, (t, _) in enumerate(sortable)}
    return [
        {"ticker": t, "value": v, "rank": rank.get(t)} for t, v in values
    ]


def _verdict(payloads: List[dict]) -> str:
    if not payloads:
        return "No companies to compare."
    sorted_p = sorted(payloads, key=lambda p: p["score"], reverse=True)
    top = sorted_p[0]
    bottom = sorted_p[-1]
    if top["score"] - bottom["score"] < 5:
        return f"Tightly bunched — {top['company']['name']} narrowly leads on a balanced KPI mix."
    return (f"{top['company']['name']} ({top['company']['ticker']}) leads with a "
            f"{top['score']:.0f}/100 score; {bottom['company']['name']} trails at "
            f"{bottom['score']:.0f}/100, mainly on underwriting and capital metrics.")


async def compare_companies(queries: List[str]) -> dict:
    companies = await _resolve_many(queries)
    if not companies:
        return {"companies": [], "metrics": [], "verdict": "Could not resolve any of the inputs."}
    payloads = await asyncio.gather(*[_company_payload(c) for c in companies])
    payloads = [p for p in payloads if p]
    if not payloads:
        return {"companies": [], "metrics": [], "verdict": "No financial data available."}

    insurer_type = payloads[0]["insurer_type"]
    metric_specs = _metric_specs(insurer_type)
    metrics = [
        {"code": code, "label": label, "lower_is_better": lib,
         "values": _rank_metric(payloads, code, lower_is_better=lib)}
        for code, label, lib in metric_specs
    ]
    return {
        "insurer_type": insurer_type,
        "companies": payloads,
        "metrics": metrics,
        "verdict": _verdict(payloads),
    }


async def auto_peer_compare(query: str) -> dict:
    primary = await resolve_company(query)
    if not primary:
        return await compare_companies([query])
    peer_tickers = peer_group_for(primary["insurer_type"])
    universe = [primary["ticker"]]
    for t in peer_tickers:
        if t not in universe:
            universe.append(t)
    universe = universe[:6]
    return await compare_companies(universe)


def _metric_specs(insurer_type: str):
    if insurer_type == "Life":
        return [
            ("persistency", "Persistency", False),
            ("investment_yield", "Investment Yield", False),
            ("premium_growth", "Premium Growth", False),
            ("benefit_ratio", "Benefit Ratio", True),
            ("roe", "ROE", False),
        ]
    if insurer_type == "Health":
        return [
            ("medical_loss_ratio", "MLR", True),
            ("expense_ratio", "Expense Ratio", True),
            ("premium_growth", "Premium Growth", False),
            ("roe", "ROE", False),
        ]
    return [
        ("combined_ratio", "Combined Ratio", True),
        ("loss_ratio", "Loss Ratio", True),
        ("expense_ratio", "Expense Ratio", True),
        ("investment_yield", "Investment Yield", False),
        ("premium_growth", "Premium Growth", False),
        ("roe", "ROE", False),
    ]
