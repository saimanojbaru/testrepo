"""Knowledge library endpoints."""
from __future__ import annotations

from typing import Optional

from fastapi import APIRouter, HTTPException, Query

from ..services.knowledge_service import (
    get_article,
    list_articles,
    list_frameworks,
    search_articles,
)


router = APIRouter(prefix="/knowledge", tags=["knowledge"])


@router.get("")
async def listing(framework: Optional[str] = Query(None)) -> dict:
    return {
        "frameworks": ["All"] + list_frameworks(),
        "articles": list_articles(framework),
    }


@router.get("/search")
async def search(q: str = Query(..., min_length=1)) -> dict:
    return {"results": search_articles(q)}


@router.get("/{slug}")
async def detail(slug: str) -> dict:
    article = get_article(slug)
    if not article:
        raise HTTPException(404, f"No article with slug '{slug}'.")
    return article
