"""Sentiment analysis for equity research.

Uses a keyword-based approach with financial and Indian market terminology.
Returns a sentiment label (bullish / neutral / bearish) and a numeric score.
"""

from __future__ import annotations

import re

# ── Positive / bullish keywords ──────────────────────────────────────────────
BULLISH_KEYWORDS: list[str] = [
    # Generic financial positives
    "beat", "beats", "exceeded", "outperform", "outperformed", "upgrade",
    "upgraded", "buy", "strong buy", "overweight", "positive", "growth",
    "record", "rally", "surge", "surged", "bullish", "upside", "profit",
    "profits", "gain", "gains", "revenue growth", "expansion", "breakout",
    "recovery", "rebound", "strong", "robust", "momentum", "opportunity",
    "dividend", "buyback", "raise", "raised", "guidance raised",
    "margin expansion", "market share", "new high", "all-time high",
    # Indian market specific
    "sebi approval", "nifty high", "sensex rally", "fii inflow", "fii buying",
    "dii buying", "promoter buying", "capex", "order win", "order inflow",
    "quarterly profit", "net profit up", "pat up", "ebitda growth",
    "gst collection", "infrastructure push", "make in india", "atmanirbhar",
]

# ── Negative / bearish keywords ──────────────────────────────────────────────
BEARISH_KEYWORDS: list[str] = [
    # Generic financial negatives
    "miss", "missed", "below", "downgrade", "downgraded", "sell",
    "underperform", "underweight", "negative", "loss", "losses", "decline",
    "declined", "fell", "fall", "drop", "dropped", "bearish", "downside",
    "weak", "weakness", "slowdown", "recession", "inflation", "debt",
    "default", "risk", "concern", "warning", "cut", "guidance cut",
    "margin compression", "write-off", "write-down", "fraud", "penalty",
    "investigation", "regulatory action", "lawsuit",
    # Indian market specific
    "fii outflow", "fii selling", "promoter selling", "pledged shares",
    "npa", "bad loan", "rbi action", "sebi notice", "it raid",
    "quarterly loss", "net loss", "pat down", "ebitda decline",
    "rupee depreciation", "current account deficit", "fiscal deficit",
]


def _tokenize(text: str) -> str:
    """Lowercase and normalise whitespace."""
    return re.sub(r"\s+", " ", text.lower().strip())


def analyze_sentiment(text: str) -> dict:
    """Score text as bullish, neutral, or bearish.

    Matches whole-word and phrase occurrences of keywords.

    Args:
        text: News headline, earnings excerpt, or research note.

    Returns:
        Dict with:
            - sentiment: "bullish" | "neutral" | "bearish"
            - score: float in [-1.0, 1.0]
            - bullish_matches: list of matched bullish keywords
            - bearish_matches: list of matched bearish keywords
    """
    normalised = _tokenize(text)

    bullish_hits: list[str] = []
    bearish_hits: list[str] = []

    for kw in BULLISH_KEYWORDS:
        pattern = r"\b" + re.escape(kw) + r"\b"
        if re.search(pattern, normalised):
            bullish_hits.append(kw)

    for kw in BEARISH_KEYWORDS:
        pattern = r"\b" + re.escape(kw) + r"\b"
        if re.search(pattern, normalised):
            bearish_hits.append(kw)

    b = len(bullish_hits)
    br = len(bearish_hits)
    total = b + br

    if total == 0:
        score = 0.0
    else:
        score = round((b - br) / total, 4)

    if score > 0.1:
        label = "bullish"
    elif score < -0.1:
        label = "bearish"
    else:
        label = "neutral"

    return {
        "sentiment": label,
        "score": score,
        "bullish_matches": bullish_hits,
        "bearish_matches": bearish_hits,
        "text_preview": text[:200] + ("..." if len(text) > 200 else ""),
    }


def analyze_multiple(texts: list[str]) -> dict:
    """Analyse a list of headlines and return aggregate sentiment.

    Args:
        texts: List of news headlines or snippets.

    Returns:
        Dict with per-item results and aggregate score/sentiment.
    """
    if not texts:
        return {"aggregate_sentiment": "neutral", "aggregate_score": 0.0, "items": []}

    items = [analyze_sentiment(t) for t in texts]
    avg_score = round(sum(i["score"] for i in items) / len(items), 4)

    if avg_score > 0.1:
        agg_label = "bullish"
    elif avg_score < -0.1:
        agg_label = "bearish"
    else:
        agg_label = "neutral"

    return {
        "aggregate_sentiment": agg_label,
        "aggregate_score": avg_score,
        "items": items,
    }
