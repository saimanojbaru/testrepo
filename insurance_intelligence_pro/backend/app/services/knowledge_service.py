"""Knowledge library — accounting standards & GAAP-vs-STAT explainers."""
from __future__ import annotations

import json
from functools import lru_cache
from pathlib import Path
from typing import List, Optional

from ..config import DATA_DIR
from ..models.insight import KnowledgeArticle


@lru_cache(maxsize=1)
def _articles() -> List[KnowledgeArticle]:
    raw = json.loads((Path(DATA_DIR) / "knowledge.json").read_text())
    return [KnowledgeArticle(**a) for a in raw["articles"]]


def list_articles(framework: Optional[str] = None) -> List[dict]:
    rows = _articles()
    if framework and framework != "All":
        rows = [r for r in rows if r.framework == framework]
    return [r.model_dump() for r in rows]


def search_articles(query: str) -> List[dict]:
    q = query.strip().lower()
    if not q:
        return [r.model_dump() for r in _articles()]
    out = []
    for r in _articles():
        haystack = " ".join([
            r.title, r.framework, r.summary, r.body_md or "",
            r.fsli or "", r.gaap_view or "", r.stat_view or "",
            " ".join(r.tags or []),
        ]).lower()
        if q in haystack:
            out.append(r.model_dump())
    return out


def get_article(slug: str) -> Optional[dict]:
    for r in _articles():
        if r.slug == slug:
            return r.model_dump()
    return None


def list_frameworks() -> List[str]:
    return sorted({r.framework for r in _articles()})
