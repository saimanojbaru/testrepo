"""Dashboard endpoints — smart insights only (no raw tables)."""
from __future__ import annotations

from fastapi import APIRouter

from ..services.insight_engine import (
    INDUSTRY_INDICATORS,
    INDUSTRY_PULSE,
    SPARKLINES,
)
from ..services.news_service import fetch_industry_news


router = APIRouter(prefix="/dashboard", tags=["dashboard"])


@router.get("/pulse")
async def market_pulse() -> dict:
    return {
        "headline": "P&C combined ratios still elevated; Life carriers benefit from yield tailwind.",
        "insights": [i.model_dump() for i in INDUSTRY_PULSE],
        "indicators": INDUSTRY_INDICATORS,
        "sparklines": SPARKLINES,
    }


@router.get("/top-news")
async def top_news(limit: int = 6) -> dict:
    items = await fetch_industry_news(limit=limit)
    return {"items": items}
