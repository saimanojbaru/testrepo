"""Compute insurance-specific KPIs from canonical fiscal history.

Outputs are ``KPISet`` instances annotated with status (good/warn/bad),
deltas vs prior year, and benchmarks pulled from sample_data.json.
"""
from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path
from typing import Dict, List, Optional

from ..config import DATA_DIR
from ..models.kpi import KPI, KPISet, RiskFactor, RiskRadar


@lru_cache(maxsize=1)
def _benchmarks() -> dict:
    return json.loads((Path(DATA_DIR) / "sample_data.json").read_text()) \
        ["industry_benchmarks"]


def _safe_div(a: Optional[float], b: Optional[float]) -> Optional[float]:
    if a is None or b is None or b == 0:
        return None
    return a / b


def _direction(curr: Optional[float], prev: Optional[float]) -> str:
    if curr is None or prev is None:
        return "flat"
    if curr > prev * 1.005:
        return "up"
    if curr < prev * 0.995:
        return "down"
    return "flat"


def _status_from_band(value: Optional[float], good_max: float,
                     warn_max: float, *, lower_is_better: bool = True) -> str:
    if value is None:
        return "neutral"
    if lower_is_better:
        if value <= good_max:
            return "good"
        if value <= warn_max:
            return "warn"
        return "bad"
    else:
        if value >= good_max:
            return "good"
        if value >= warn_max:
            return "warn"
        return "bad"


def _ratio(curr: Optional[float], prev: Optional[float]) -> Optional[float]:
    if curr is None or prev is None or prev == 0:
        return None
    return round((curr - prev) / abs(prev) * 100.0, 2)


def compute_kpis(insurer_type: str, history: List[dict]) -> KPISet:
    if not history:
        return KPISet(insurer_type=insurer_type)
    latest = history[-1]
    prev = history[-2] if len(history) >= 2 else {}
    bench = _benchmarks().get(insurer_type, {})

    if insurer_type == "Life":
        return _life_kpis(latest, prev, bench)
    if insurer_type == "Health":
        return _health_kpis(latest, prev, bench)
    # P&C / Reinsurance / Multiline default to P&C
    return _pc_kpis(latest, prev, bench)


def _pc_kpis(latest: dict, prev: dict, bench: dict) -> KPISet:
    prem = latest.get("premiums")
    loss_ratio = _pct(_safe_div(latest.get("losses"), prem))
    expense_ratio = _pct(_safe_div(latest.get("expenses"), prem))
    combined_ratio = (loss_ratio or 0) + (expense_ratio or 0) if loss_ratio is not None and expense_ratio is not None else None
    underwriting_profit = None
    if prem is not None and combined_ratio is not None:
        underwriting_profit = round(prem * (1 - combined_ratio / 100), 2)

    prev_loss = _pct(_safe_div(prev.get("losses"), prev.get("premiums")))
    prev_exp = _pct(_safe_div(prev.get("expenses"), prev.get("premiums")))
    prev_combined = (prev_loss or 0) + (prev_exp or 0) if prev_loss is not None and prev_exp is not None else None

    inv_yield = _pct(_safe_div(latest.get("investment_income"),
                                latest.get("reserves")))
    prev_inv_yield = _pct(_safe_div(prev.get("investment_income"),
                                     prev.get("reserves")))

    primary = [
        KPI(code="loss_ratio", label="Loss Ratio",
            value=loss_ratio,
            direction=_direction(loss_ratio, prev_loss),
            delta_yoy=_ratio(loss_ratio, prev_loss),
            benchmark=bench.get("loss_ratio"),
            status=_status_from_band(loss_ratio, 65, 75, lower_is_better=True),
            description="Incurred losses & LAE as % of earned premium"),
        KPI(code="expense_ratio", label="Expense Ratio",
            value=expense_ratio,
            direction=_direction(expense_ratio, prev_exp),
            delta_yoy=_ratio(expense_ratio, prev_exp),
            benchmark=bench.get("expense_ratio"),
            status=_status_from_band(expense_ratio, 26, 32, lower_is_better=True),
            description="Underwriting expenses as % of earned premium"),
        KPI(code="combined_ratio", label="Combined Ratio",
            value=combined_ratio,
            direction=_direction(combined_ratio, prev_combined),
            delta_yoy=_ratio(combined_ratio, prev_combined),
            benchmark=bench.get("combined_ratio"),
            status=_status_from_band(combined_ratio, 95, 100, lower_is_better=True),
            description="Loss Ratio + Expense Ratio. <100% = underwriting profit"),
        KPI(code="underwriting_profit", label="Underwriting Profit",
            value=underwriting_profit, unit="USD M",
            direction="up" if (underwriting_profit or 0) > 0 else "down",
            status="good" if (underwriting_profit or 0) > 0 else "warn",
            description="Earned premium × (1 − Combined Ratio)"),
    ]
    secondary = [
        KPI(code="investment_yield", label="Investment Yield",
            value=inv_yield,
            direction=_direction(inv_yield, prev_inv_yield),
            delta_yoy=_ratio(inv_yield, prev_inv_yield),
            benchmark=bench.get("investment_yield"),
            status=_status_from_band(inv_yield, 4.0, 3.0, lower_is_better=False),
            description="Net investment income / reserves"),
        KPI(code="premium_growth", label="Premium Growth",
            value=_ratio(prem, prev.get("premiums")),
            direction=_direction(prem, prev.get("premiums")),
            status=_status_from_band(_ratio(prem, prev.get("premiums")) or 0,
                                     5.0, 0.0, lower_is_better=False),
            description="Year-over-year change in earned premium"),
        KPI(code="leverage", label="Reserves / Equity",
            value=_round(_safe_div(latest.get("reserves"), latest.get("equity"))),
            unit="x",
            status="warn" if (_safe_div(latest.get("reserves"),
                                         latest.get("equity")) or 0) > 5
                    else "good",
            description="Reserve leverage relative to equity"),
        KPI(code="roe", label="Return on Equity",
            value=_pct(_safe_div(latest.get("net_income"), latest.get("equity"))),
            direction=_direction(latest.get("net_income"), prev.get("net_income")),
            status=_status_from_band(
                _pct(_safe_div(latest.get("net_income"), latest.get("equity"))) or 0,
                12.0, 6.0, lower_is_better=False),
            description="Net income / shareholders' equity"),
    ]
    return KPISet(insurer_type="P&C", primary=primary, secondary=secondary)


def _life_kpis(latest: dict, prev: dict, bench: dict) -> KPISet:
    prem = latest.get("premiums")
    inv_yield = _pct(_safe_div(latest.get("investment_income"),
                                latest.get("reserves")))
    prev_inv_yield = _pct(_safe_div(prev.get("investment_income"),
                                     prev.get("reserves")))
    persistency = _persistency(latest, prev)
    growth = _ratio(prem, prev.get("premiums"))
    benefit_ratio = _pct(_safe_div(latest.get("losses"), prem))

    primary = [
        KPI(code="persistency", label="Persistency Ratio",
            value=persistency,
            benchmark=bench.get("persistency", 92.0),
            status=_status_from_band(persistency, 90, 85, lower_is_better=False),
            description="Estimated retention based on premium and reserve continuity"),
        KPI(code="investment_yield", label="Investment Yield",
            value=inv_yield,
            direction=_direction(inv_yield, prev_inv_yield),
            delta_yoy=_ratio(inv_yield, prev_inv_yield),
            benchmark=bench.get("investment_yield", 4.6),
            status=_status_from_band(inv_yield, 4.5, 3.5, lower_is_better=False),
            description="Net investment income / policy reserves"),
        KPI(code="premium_growth", label="Premium Growth",
            value=growth,
            direction=_direction(prem, prev.get("premiums")),
            benchmark=bench.get("premium_growth", 4.0),
            status=_status_from_band(growth or 0, 4.0, 0.0, lower_is_better=False),
            description="Year-over-year change in gross premium"),
        KPI(code="benefit_ratio", label="Benefit Ratio",
            value=benefit_ratio,
            status=_status_from_band(benefit_ratio, 70, 80, lower_is_better=True),
            description="Policyholder benefits / premium"),
    ]
    secondary = [
        KPI(code="expense_ratio", label="Expense Ratio",
            value=_pct(_safe_div(latest.get("expenses"), prem)),
            description="Operating expenses / premium"),
        KPI(code="roe", label="Return on Equity",
            value=_pct(_safe_div(latest.get("net_income"), latest.get("equity"))),
            description="Net income / shareholders' equity"),
        KPI(code="leverage", label="Reserves / Equity",
            value=_round(_safe_div(latest.get("reserves"), latest.get("equity"))),
            unit="x",
            description="Reserve leverage"),
    ]
    return KPISet(insurer_type="Life", primary=primary, secondary=secondary)


def _health_kpis(latest: dict, prev: dict, bench: dict) -> KPISet:
    prem = latest.get("premiums")
    mlr = _pct(_safe_div(latest.get("losses"), prem))
    expense = _pct(_safe_div(latest.get("expenses"), prem))
    primary = [
        KPI(code="medical_loss_ratio", label="Medical Loss Ratio",
            value=mlr,
            benchmark=bench.get("medical_loss_ratio", 86.5),
            status=_status_from_band(mlr, 85, 90, lower_is_better=True),
            description="Medical claims / premium (ACA min 85%)"),
        KPI(code="expense_ratio", label="Expense Ratio",
            value=expense,
            benchmark=bench.get("expense_ratio", 11.0),
            status=_status_from_band(expense, 12, 16, lower_is_better=True),
            description="Operating expense / premium"),
        KPI(code="premium_growth", label="Premium Growth",
            value=_ratio(prem, prev.get("premiums")),
            description="Year-over-year change in premium revenue"),
        KPI(code="roe", label="Return on Equity",
            value=_pct(_safe_div(latest.get("net_income"), latest.get("equity"))),
            description="Net income / equity"),
    ]
    return KPISet(insurer_type="Health", primary=primary, secondary=[])


def _persistency(latest: dict, prev: dict) -> Optional[float]:
    """Approximate persistency from change in policy reserves & premiums."""
    if not prev:
        return None
    p_now = latest.get("premiums") or 0
    p_prev = prev.get("premiums") or 0
    r_now = latest.get("reserves") or 0
    r_prev = prev.get("reserves") or 0
    if p_prev == 0 or r_prev == 0:
        return None
    persistency = 100 - max(0, (1 - r_now / r_prev) * 100)
    growth_signal = max(0, min(5, (p_now - p_prev) / p_prev * 100)) if p_prev else 0
    return round(min(99.0, persistency + growth_signal * 0.2), 2)


def _pct(value: Optional[float]) -> Optional[float]:
    if value is None:
        return None
    return round(value * 100.0, 2)


def _round(value: Optional[float]) -> Optional[float]:
    if value is None:
        return None
    return round(value, 2)


def compute_risk_radar(insurer_type: str, kpis: KPISet,
                        history: List[dict]) -> RiskRadar:
    """Translate KPI status into risk bands for the radar chart."""
    factors: List[RiskFactor] = []
    by_code = {k.code: k for k in (kpis.primary + kpis.secondary)}

    def _factor(label: str, code: str, *, invert: bool = False,
                neutral_score: float = 50.0) -> RiskFactor:
        kpi = by_code.get(code)
        if not kpi or kpi.value is None:
            return RiskFactor(label=label, score=neutral_score, band="yellow",
                              note="Insufficient data")
        score = neutral_score
        if kpi.status == "good":
            score = 25.0
        elif kpi.status == "warn":
            score = 55.0
        elif kpi.status == "bad":
            score = 80.0
        if invert:
            score = 100 - score
        band = "green" if score < 40 else ("yellow" if score < 65 else "red")
        return RiskFactor(label=label, score=score, band=band,
                          note=f"{kpi.label}: {kpi.value}{kpi.unit}")

    if insurer_type in ("P&C", "Reinsurance", "Multiline"):
        factors = [
            _factor("Underwriting", "combined_ratio"),
            _factor("Loss exposure", "loss_ratio"),
            _factor("Expense discipline", "expense_ratio"),
            _factor("Investment", "investment_yield", invert=True),
            _factor("Growth", "premium_growth", invert=True),
            _factor("Leverage", "leverage"),
        ]
    elif insurer_type == "Life":
        factors = [
            _factor("Persistency", "persistency", invert=True),
            _factor("Investment", "investment_yield", invert=True),
            _factor("Growth", "premium_growth", invert=True),
            _factor("Benefit pressure", "benefit_ratio"),
            _factor("Leverage", "leverage"),
            _factor("Profitability", "roe", invert=True),
        ]
    else:  # Health
        factors = [
            _factor("MLR", "medical_loss_ratio"),
            _factor("Expense", "expense_ratio"),
            _factor("Growth", "premium_growth", invert=True),
            _factor("Profitability", "roe", invert=True),
        ]
    overall = round(sum(f.score for f in factors) / len(factors), 2) if factors else 50.0
    return RiskRadar(overall=overall, factors=factors)


def composite_score(kpis: KPISet, risk: RiskRadar) -> float:
    """Return a 0-100 health score; higher is better."""
    risk_component = max(0.0, 100.0 - risk.overall)
    primary_good = sum(1 for k in kpis.primary if k.status == "good")
    primary_total = max(1, len(kpis.primary))
    kpi_component = (primary_good / primary_total) * 100.0
    return round(0.6 * risk_component + 0.4 * kpi_component, 1)
