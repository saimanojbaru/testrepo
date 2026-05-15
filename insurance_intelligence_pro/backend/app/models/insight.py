"""Insight, news, and knowledge primitives."""
from __future__ import annotations

from enum import Enum
from typing import List, Optional
from pydantic import BaseModel, Field


class InsightLevel(str, Enum):
    POSITIVE = "positive"
    NEUTRAL = "neutral"
    WARNING = "warning"
    NEGATIVE = "negative"


class Insight(BaseModel):
    title: str
    detail: Optional[str] = None
    level: InsightLevel = InsightLevel.NEUTRAL
    icon: str = "spark"
    tags: List[str] = Field(default_factory=list)


class TrendIndicator(BaseModel):
    label: str
    value: str
    delta: Optional[float] = None
    direction: str = "flat"


class MarketPulse(BaseModel):
    headline: str
    insights: List[Insight] = Field(default_factory=list)
    indicators: List[TrendIndicator] = Field(default_factory=list)
    sparklines: List[List[float]] = Field(default_factory=list)


class NewsItem(BaseModel):
    title: str
    url: str
    source: str
    published: Optional[str] = None
    summary: Optional[str] = None
    category: str = "General"
    impact: str = Field(default="Medium", description="High, Medium, Low")


class KnowledgeArticle(BaseModel):
    slug: str
    title: str
    framework: str = Field(..., description="ASC944, FAS60, FAS97, FAS133, SAP")
    summary: str
    body_md: str
    fsli: Optional[str] = None
    gaap_view: Optional[str] = None
    stat_view: Optional[str] = None
    tags: List[str] = Field(default_factory=list)
