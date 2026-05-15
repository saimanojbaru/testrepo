"""KPI primitives shared across the API."""
from __future__ import annotations

from typing import List, Optional
from pydantic import BaseModel, Field


class KPI(BaseModel):
    code: str
    label: str
    value: Optional[float]
    unit: str = "%"
    direction: str = Field(default="neutral",
                           description="up, down, flat — for trend arrows")
    delta_yoy: Optional[float] = None
    benchmark: Optional[float] = None
    status: str = Field(default="neutral",
                        description="good, warn, bad, neutral")
    description: Optional[str] = None


class KPISet(BaseModel):
    insurer_type: str
    primary: List[KPI] = Field(default_factory=list)
    secondary: List[KPI] = Field(default_factory=list)


class RiskFactor(BaseModel):
    label: str
    score: float = Field(..., description="0-100, higher = more risk")
    band: str = Field(default="green", description="green, yellow, red")
    note: Optional[str] = None


class RiskRadar(BaseModel):
    overall: float
    factors: List[RiskFactor] = Field(default_factory=list)
