"""Rule-based insight generation.

The goal is *conclusions, not data*: the user should read each card and
walk away with one sentence of value.
"""
from __future__ import annotations

from typing import List, Optional

from ..models.insight import Insight, InsightLevel
from ..models.kpi import KPISet


def _kpi(kpis: KPISet, code: str):
    for k in kpis.primary + kpis.secondary:
        if k.code == code:
            return k
    return None


def generate_company_insights(kpis: KPISet, history: List[dict],
                               insurer_type: str) -> List[Insight]:
    if not history:
        return [Insight(title="Limited financial coverage available",
                        detail="We could not extract enough filings to draw insights.",
                        level=InsightLevel.NEUTRAL, icon="info")]

    insights: List[Insight] = []
    insights.extend(_underwriting_insights(kpis, insurer_type))
    insights.extend(_growth_insights(history, kpis))
    insights.extend(_balance_sheet_insights(history))
    insights.extend(_investment_insights(kpis))
    insights.extend(_profitability_insights(history, kpis))
    return insights[:8]


def _underwriting_insights(kpis: KPISet, insurer_type: str) -> List[Insight]:
    out: List[Insight] = []
    cr = _kpi(kpis, "combined_ratio")
    if cr and cr.value is not None:
        if cr.value < 95:
            out.append(Insight(
                title="Strong underwriting discipline",
                detail=f"Combined ratio of {cr.value:.1f}% indicates a solid underwriting profit.",
                level=InsightLevel.POSITIVE, icon="shield",
                tags=["underwriting"]))
        elif cr.value < 100:
            out.append(Insight(
                title="Underwriting at breakeven",
                detail=f"Combined ratio of {cr.value:.1f}% is profitable but tight.",
                level=InsightLevel.NEUTRAL, icon="balance",
                tags=["underwriting"]))
        else:
            out.append(Insight(
                title="Underwriting losses persisting",
                detail=f"Combined ratio of {cr.value:.1f}% means losses + expenses exceed premium; profit relies on investment income.",
                level=InsightLevel.NEGATIVE, icon="alert",
                tags=["underwriting"]))
        if cr.delta_yoy is not None and cr.delta_yoy > 1.5:
            out.append(Insight(
                title="Combined ratio deteriorating",
                detail=f"YoY +{cr.delta_yoy:.1f}% — claims inflation and severity pressure visible.",
                level=InsightLevel.WARNING, icon="trend-up",
                tags=["claims"]))
    mlr = _kpi(kpis, "medical_loss_ratio")
    if mlr and mlr.value is not None and insurer_type == "Health":
        if mlr.value > 88:
            out.append(Insight(
                title="MLR pressure",
                detail=f"Medical loss ratio of {mlr.value:.1f}% leaves limited margin for admin and profit.",
                level=InsightLevel.WARNING, icon="medical",
                tags=["MLR"]))
    return out


def _growth_insights(history: List[dict], kpis: KPISet) -> List[Insight]:
    out: List[Insight] = []
    growth = _kpi(kpis, "premium_growth")
    if growth and growth.value is not None:
        if growth.value > 10:
            out.append(Insight(
                title="Premium scaling rapidly",
                detail=f"Premium growth +{growth.value:.1f}% YoY — watch for reserve adequacy on newer cohorts.",
                level=InsightLevel.POSITIVE, icon="rocket",
                tags=["growth"]))
        elif growth.value > 4:
            out.append(Insight(
                title="Healthy top-line momentum",
                detail=f"Premium growth +{growth.value:.1f}% YoY — above industry average.",
                level=InsightLevel.POSITIVE, icon="trend-up",
                tags=["growth"]))
        elif growth.value < 0:
            out.append(Insight(
                title="Top-line contraction",
                detail=f"Premium {growth.value:.1f}% YoY — pricing or retention may be under pressure.",
                level=InsightLevel.WARNING, icon="trend-down",
                tags=["growth"]))
    return out


def _balance_sheet_insights(history: List[dict]) -> List[Insight]:
    out: List[Insight] = []
    if len(history) < 2:
        return out
    latest, prev = history[-1], history[-2]
    res_now = latest.get("reserves")
    res_prev = prev.get("reserves")
    eq_now = latest.get("equity")
    if res_now and res_prev and res_now > 0 and res_prev > 0:
        delta = (res_now - res_prev) / res_prev * 100
        if delta > 8:
            out.append(Insight(
                title="Reserves expanding rapidly",
                detail=f"Reserves +{delta:.1f}% YoY — supports growth but tightens RBC headroom.",
                level=InsightLevel.NEUTRAL, icon="vault",
                tags=["reserves"]))
        elif delta < -3:
            out.append(Insight(
                title="Reserve releases visible",
                detail=f"Reserves {delta:.1f}% YoY — favorable development is boosting earnings quality.",
                level=InsightLevel.POSITIVE, icon="vault",
                tags=["reserves"]))
    if eq_now and res_now and eq_now > 0 and res_now / eq_now > 6:
        out.append(Insight(
            title="Elevated reserve leverage",
            detail=f"Reserves are {res_now/eq_now:.1f}× equity — capital sensitivity is high.",
            level=InsightLevel.WARNING, icon="warning",
            tags=["leverage"]))
    return out


def _investment_insights(kpis: KPISet) -> List[Insight]:
    inv = _kpi(kpis, "investment_yield")
    if not inv or inv.value is None:
        return []
    out = []
    if inv.delta_yoy is not None and inv.delta_yoy > 5:
        out.append(Insight(
            title="Investment income tailwind",
            detail=f"Yield up {inv.delta_yoy:.1f}% YoY as the portfolio reprices into a higher-rate regime.",
            level=InsightLevel.POSITIVE, icon="coins",
            tags=["investment"]))
    elif inv.value < 3.0:
        out.append(Insight(
            title="Investment yield drag",
            detail=f"Yield of {inv.value:.2f}% trails industry; portfolio mix may be defensive.",
            level=InsightLevel.WARNING, icon="coins",
            tags=["investment"]))
    return out


def _profitability_insights(history: List[dict], kpis: KPISet) -> List[Insight]:
    if len(history) < 2:
        return []
    latest, prev = history[-1], history[-2]
    ni_now = latest.get("net_income") or 0
    ni_prev = prev.get("net_income") or 0
    growth = _kpi(kpis, "premium_growth")
    if ni_now < 0:
        return [Insight(
            title="Net loss for fiscal year",
            detail="Bottom line in the red; investigate large CAT, reserve charges, or DAC unlocking.",
            level=InsightLevel.NEGATIVE, icon="trend-down",
            tags=["profitability"])]
    if ni_prev > 0 and ni_now < ni_prev * 0.7:
        out: List[Insight] = [Insight(
            title="Earnings compression",
            detail=f"Net income fell to ${ni_now:.0f}M from ${ni_prev:.0f}M — margin pressure despite growth." \
                   if growth and (growth.value or 0) > 0 else \
                   f"Net income fell to ${ni_now:.0f}M from ${ni_prev:.0f}M.",
            level=InsightLevel.WARNING, icon="trend-down",
            tags=["earnings"])]
        return out
    if ni_prev > 0 and ni_now > ni_prev * 1.25:
        return [Insight(
            title="Earnings rebound",
            detail=f"Net income up {((ni_now/ni_prev - 1) * 100):.1f}% YoY on improved underwriting and yields.",
            level=InsightLevel.POSITIVE, icon="trend-up",
            tags=["earnings"])]
    return []


# ----- Dashboard / market-wide insights -------------------------------------


INDUSTRY_PULSE = [
    Insight(
        title="Combined ratios rising across P&C",
        detail="Sector-wide CR drift +2.1% as social inflation and severe weather extend the hard market.",
        level=InsightLevel.WARNING, icon="trend-up", tags=["P&C", "industry"]),
    Insight(
        title="Investment yields lifting Life carriers",
        detail="Net investment income at multi-year highs as portfolios reprice — cushioning DAC unlocking.",
        level=InsightLevel.POSITIVE, icon="coins", tags=["Life", "yields"]),
    Insight(
        title="LDTI noise normalising in 2025 filings",
        detail="Cohort-level LFPB rollforwards now disclosed by all SEC life filers; comparability improving.",
        level=InsightLevel.POSITIVE, icon="standard", tags=["LDTI", "GAAP"]),
    Insight(
        title="Reinsurance capacity selectively returning",
        detail="Property cat retro pricing softening at mid-year; primary insurers retaining more risk.",
        level=InsightLevel.NEUTRAL, icon="globe", tags=["reinsurance"]),
    Insight(
        title="Health MLRs holding above 87%",
        detail="Utilization runs hot post-COVID backlog; ACA rebates expected for several MA carriers.",
        level=InsightLevel.WARNING, icon="medical", tags=["Health", "MLR"]),
]


INDUSTRY_INDICATORS = [
    {"label": "10Y Treasury", "value": "4.32%", "delta": 0.04, "direction": "up"},
    {"label": "Claims Inflation (P&C)", "value": "+5.8%", "delta": 0.6, "direction": "up"},
    {"label": "Auto Severity YoY", "value": "+8.4%", "delta": 1.2, "direction": "up"},
    {"label": "Cat Bond Issuance", "value": "$12.1B", "delta": 0.18, "direction": "up"},
    {"label": "MA Star Ratings (4+)", "value": "42%", "delta": -0.05, "direction": "down"},
]


SPARKLINES = [
    [96.1, 97.4, 98.6, 99.2, 100.8, 99.5, 98.7],
    [3.4, 3.6, 3.9, 4.0, 4.1, 4.2, 4.3],
    [85.0, 85.6, 86.2, 86.8, 87.1, 87.4, 87.6],
]
