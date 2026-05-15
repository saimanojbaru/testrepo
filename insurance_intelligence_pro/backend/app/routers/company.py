"""Company analysis endpoint — the heart of the app."""
from __future__ import annotations

from fastapi import APIRouter, HTTPException, Query

from ..cache import cached
from ..database import record_analysis
from ..models.company import (
    Company,
    CompanyAnalysis,
    CompanySummary,
    FinancialSeries,
)
from ..services.financial_extractor import get_company_financials
from ..services.insight_engine import generate_company_insights
from ..services.kpi_engine import (
    composite_score,
    compute_kpis,
    compute_risk_radar,
)
from ..services.ticker_mapper import list_local_companies, resolve_company


router = APIRouter(prefix="/company", tags=["company"])


@router.get("/search")
async def search_companies(q: str = Query(..., min_length=1)) -> dict:
    """Quick lookup, used by the Flutter search bar."""
    company = await resolve_company(q)
    suggestions = [c for c in list_local_companies()
                   if q.upper() in c["ticker"] or q.upper() in c["name"].upper()][:8]
    return {"resolved": company, "suggestions": suggestions}


@router.get("/{query}", response_model=CompanyAnalysis)
async def analyze_company(query: str) -> CompanyAnalysis:
    return await _analyze_cached(query)


@cached("company-analysis", persist=True,
        memory_ttl=60 * 30, disk_ttl=60 * 60 * 12)
async def _analyze_cached(query: str) -> CompanyAnalysis:
    company = await resolve_company(query)
    if not company:
        raise HTTPException(404, f"Could not resolve company '{query}'.")
    financials, estimated = await get_company_financials(company)
    insurer_type = financials["insurer_type"]
    history = financials["fiscal_history"]
    kpis = compute_kpis(insurer_type, history)
    risk = compute_risk_radar(insurer_type, kpis, history)
    score = composite_score(kpis, risk)
    insights = generate_company_insights(kpis, history, insurer_type)
    series = _build_series(history)

    company_model = Company(
        cik=company["cik"], ticker=company["ticker"], name=company["name"],
        sic=(financials.get("submissions") or {}).get("sic"),
        insurer_type=insurer_type,
        exchange=_first_exchange(financials.get("submissions")),
    )
    headline = _headline(insurer_type, kpis, score)
    raw_metrics = history[-1] if history else {}
    record_analysis(company["cik"], financials.get("fiscal_year"),
                    {"score": score, "insurer_type": insurer_type})
    return CompanyAnalysis(
        company=company_model,
        score=score,
        headline=headline,
        last_fiscal_year=financials.get("fiscal_year"),
        kpis=kpis,
        risk_radar=risk,
        insights=insights,
        series=series,
        raw_metrics={k: v for k, v in raw_metrics.items() if k != "fy"},
        data_source=financials.get("source", "SEC EDGAR"),
        is_estimated=estimated,
    )


@router.get("/{query}/summary", response_model=CompanySummary)
async def company_summary(query: str) -> CompanySummary:
    full = await _analyze_cached(query)
    return CompanySummary(
        company=full.company, score=full.score, headline=full.headline,
        last_fiscal_year=full.last_fiscal_year,
    )


def _build_series(history: list) -> list[FinancialSeries]:
    if not history:
        return []
    metrics = [
        ("premiums", "Premium", "USD M"),
        ("losses", "Losses", "USD M"),
        ("expenses", "Expenses", "USD M"),
        ("investment_income", "Investment Income", "USD M"),
        ("net_income", "Net Income", "USD M"),
    ]
    return [
        FinancialSeries(
            label=label, unit=unit,
            points=[
                {"fy": float(r["fy"]), "value": float(r.get(key) or 0)}
                for r in history
            ],
        )
        for key, label, unit in metrics
    ]


def _headline(insurer_type: str, kpis, score: float) -> str:
    primary = {k.code: k for k in kpis.primary}
    if insurer_type in ("P&C", "Reinsurance", "Multiline"):
        cr = primary.get("combined_ratio")
        if cr and cr.value is not None:
            mood = "underwriting profit" if cr.value < 100 else "underwriting loss"
            return f"Combined ratio {cr.value:.1f}% — running at an {mood}; health score {score:.0f}/100."
    if insurer_type == "Life":
        py = primary.get("persistency")
        iy = primary.get("investment_yield")
        return (f"Persistency {py.value:.1f}% and yield {iy.value:.2f}% drive a {score:.0f}/100 score."
                if py and iy and py.value is not None and iy.value is not None
                else f"Long-duration book; health score {score:.0f}/100.")
    if insurer_type == "Health":
        mlr = primary.get("medical_loss_ratio")
        if mlr and mlr.value is not None:
            return f"Medical loss ratio {mlr.value:.1f}% — score {score:.0f}/100."
    return f"Composite score {score:.0f}/100."


def _first_exchange(submissions: dict | None) -> str | None:
    if not submissions:
        return None
    exch = submissions.get("exchanges") or []
    return exch[0] if exch else None
