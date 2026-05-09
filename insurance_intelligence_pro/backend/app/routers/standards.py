"""Standards endpoints — SSAP / ASC 944 / PCAOB.

Serves the four-section standards schema (chapter_content, need,
evolution, gaap_comparison) from the same JSON files the Flutter app
ships in its asset bundle.

Endpoints
---------
* GET /standards                     — list of frameworks
* GET /standards/{framework}         — Level 1 catalog index
* GET /standards/{framework}/{id}    — Level 2 deep dive
* GET /standards/{framework}/search?q=... — full-text search across the
                                            four-section corpus
"""
from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path
from typing import List, Optional

from fastapi import APIRouter, HTTPException, Query
from pydantic import BaseModel, Field

from ..config import DATA_DIR


router = APIRouter(prefix="/standards", tags=["standards"])


# ---- Pydantic schema --------------------------------------------------------


class StandardEvolution(BaseModel):
    year: str = Field(..., description="Calendar year of the change")
    change: str = Field(..., description="Plain-English summary of what changed")


class GaapComparisonRow(BaseModel):
    aspect: str = Field(..., description="Topic of the comparison row")
    gaap: str = Field(..., description="GAAP treatment")
    stat: str = Field(..., description="Statutory (SAP) treatment")
    delta: str = Field(..., description="Key delta narrative")


class Standard(BaseModel):
    id: str
    framework: str
    number: str
    title: str
    fsli: Optional[str] = None
    summary: str
    chapter_content: str = Field(..., description="Full chapter text — Section 1")
    need: str = Field(..., description="Need / objective — Section 2")
    evolution: List[StandardEvolution] = Field(
        default_factory=list, description="Evolution timeline — Section 3")
    gaap_comparison: List[GaapComparisonRow] = Field(
        default_factory=list, description="GAAP-vs-STAT rows — Section 4")
    tags: List[str] = Field(default_factory=list)


class CatalogMeta(BaseModel):
    framework: str
    manual: str
    publisher: str
    source_url: str
    count: int


class CatalogResponse(BaseModel):
    meta: CatalogMeta
    standards: List[Standard]


class FrameworksResponse(BaseModel):
    frameworks: List[CatalogMeta]


# ---- Loader -----------------------------------------------------------------


_FILES = {
    "SSAP": "ssap.json",
    "ASC944": "asc944.json",
    "PCAOB": "pcaob.json",
}


@lru_cache(maxsize=4)
def _catalog(framework: str) -> dict:
    if framework not in _FILES:
        raise HTTPException(404, f"Unknown framework: {framework}")
    path = Path(DATA_DIR) / _FILES[framework]
    if not path.exists():
        raise HTTPException(500, f"Catalog file missing: {path.name}")
    return json.loads(path.read_text())


def _meta(catalog: dict) -> CatalogMeta:
    return CatalogMeta(
        framework=catalog.get("framework", ""),
        manual=catalog.get("manual", ""),
        publisher=catalog.get("publisher", ""),
        source_url=catalog.get("source_url", ""),
        count=len(catalog.get("standards", [])),
    )


# ---- Routes -----------------------------------------------------------------


@router.get("", response_model=FrameworksResponse)
async def list_frameworks() -> FrameworksResponse:
    """Return metadata for every available standards framework."""
    return FrameworksResponse(
        frameworks=[_meta(_catalog(fw)) for fw in _FILES],
    )


@router.get("/{framework}", response_model=CatalogResponse)
async def list_standards(framework: str) -> CatalogResponse:
    """Level 1 — Full standards index for the framework."""
    catalog = _catalog(framework.upper())
    standards = [Standard(**row) for row in catalog.get("standards", [])]
    return CatalogResponse(meta=_meta(catalog), standards=standards)


@router.get("/{framework}/search", response_model=CatalogResponse)
async def search_standards(
    framework: str,
    q: str = Query(..., min_length=1, description="Search query"),
) -> CatalogResponse:
    """Full-text search across all four content sections."""
    catalog = _catalog(framework.upper())
    needle = q.strip().lower()
    out: list[Standard] = []
    for row in catalog.get("standards", []):
        corpus = " ".join([
            row.get("id", ""),
            row.get("number", ""),
            row.get("title", ""),
            row.get("fsli", "") or "",
            row.get("summary", ""),
            row.get("chapter_content", ""),
            row.get("need", ""),
            " ".join(row.get("tags", []) or []),
            " ".join(f"{e.get('year', '')} {e.get('change', '')}"
                     for e in row.get("evolution", [])),
            " ".join(
                f"{r.get('aspect', '')} {r.get('gaap', '')} {r.get('stat', '')} {r.get('delta', '')}"
                for r in row.get("gaap_comparison", [])),
        ]).lower()
        if needle in corpus:
            out.append(Standard(**row))
    return CatalogResponse(meta=_meta(catalog), standards=out)


@router.get("/{framework}/{standard_id}", response_model=Standard)
async def get_standard(framework: str, standard_id: str) -> Standard:
    """Level 2 — Comprehensive deep dive on a single standard."""
    catalog = _catalog(framework.upper())
    target = standard_id.upper()
    for row in catalog.get("standards", []):
        if row.get("id", "").upper() == target or \
                row.get("number", "").upper() == target:
            return Standard(**row)
    raise HTTPException(404, f"Standard not found: {standard_id}")
