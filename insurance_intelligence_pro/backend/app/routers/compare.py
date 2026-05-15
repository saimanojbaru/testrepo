"""Compare endpoint — peer-group benchmarking."""
from __future__ import annotations

from typing import List, Optional

from fastapi import APIRouter, Body, Query

from ..cache import cached
from ..services.peer_engine import auto_peer_compare, compare_companies


router = APIRouter(prefix="/compare", tags=["compare"])


@router.post("")
async def compare(payload: dict = Body(...)) -> dict:
    queries: List[str] = [q for q in (payload.get("companies") or []) if q]
    if not queries:
        return {"error": "Provide at least one company in 'companies'."}
    return await _compare_cached(tuple(sorted(q.upper() for q in queries)))


@router.get("/auto")
async def auto_compare(q: str = Query(..., min_length=1)) -> dict:
    return await _auto_compare_cached(q.upper())


@cached("compare", persist=True, memory_ttl=60 * 20, disk_ttl=60 * 60 * 6)
async def _compare_cached(queries: tuple) -> dict:
    return await compare_companies(list(queries))


@cached("auto-compare", persist=True, memory_ttl=60 * 20, disk_ttl=60 * 60 * 6)
async def _auto_compare_cached(query: str) -> dict:
    return await auto_peer_compare(query)
