"""Pydantic models describing companies and analysis output."""
from __future__ import annotations

from typing import List, Optional, Dict
from pydantic import BaseModel, Field

from .kpi import KPISet, RiskRadar
from .insight import Insight


class Company(BaseModel):
    cik: str
    ticker: str
    name: str
    sic: Optional[str] = None
    insurer_type: str = Field(default="Unknown",
                              description="P&C, Life, Health, Multiline, Reinsurance, Unknown")
    exchange: Optional[str] = None


class CompanySummary(BaseModel):
    company: Company
    score: float = Field(..., description="Overall financial-health score 0-100")
    headline: str
    last_fiscal_year: Optional[int] = None


class FinancialSeries(BaseModel):
    """Time-series of a single line item, ordered oldest -> newest."""
    label: str
    unit: str = "USD"
    points: List[Dict[str, float]] = Field(default_factory=list)


class CompanyAnalysis(BaseModel):
    company: Company
    score: float
    headline: str
    last_fiscal_year: Optional[int]
    kpis: KPISet
    risk_radar: RiskRadar
    insights: List[Insight] = Field(default_factory=list)
    series: List[FinancialSeries] = Field(default_factory=list)
    raw_metrics: Dict[str, Optional[float]] = Field(default_factory=dict)
    data_source: str = "SEC EDGAR"
    is_estimated: bool = False
