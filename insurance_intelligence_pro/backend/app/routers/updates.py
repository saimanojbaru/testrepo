"""Industry updates / news endpoints."""
from __future__ import annotations

from typing import Optional

from fastapi import APIRouter, Query

from ..services.news_service import fetch_industry_news


router = APIRouter(prefix="/updates", tags=["updates"])


@router.get("")
async def updates(
    category: Optional[str] = Query(None),
    limit: int = Query(40, ge=1, le=100),
) -> dict:
    items = await fetch_industry_news(limit=limit, category=category)
    categories = sorted({i["category"] for i in items}) if items else []
    return {"items": items, "categories": ["All"] + categories}
