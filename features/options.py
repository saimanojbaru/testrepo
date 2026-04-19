"""Options-specific features — greeks, IV rank, PCR, max-pain, OI buildup.

Greeks computed via Black-Scholes directly (no external dependency) so tests are
deterministic. py_vollib integration is trivial to swap in later if desired.
"""
from __future__ import annotations

from dataclasses import dataclass
from math import erf, exp, log, pi, sqrt

import numpy as np
import pandas as pd

SQRT_2PI = sqrt(2.0 * pi)


def _norm_cdf(x: float) -> float:
    return 0.5 * (1.0 + erf(x / sqrt(2.0)))


def _norm_pdf(x: float) -> float:
    return exp(-0.5 * x * x) / SQRT_2PI


@dataclass(frozen=True)
class Greeks:
    delta: float
    gamma: float
    theta: float
    vega: float


def bs_greeks(
    spot: float,
    strike: float,
    t_years: float,
    rate: float,
    iv: float,
    option_type: str,
) -> Greeks:
    """Black-Scholes greeks. `option_type` in {'CE','PE'}. t_years > 0."""
    if t_years <= 0 or iv <= 0:
        return Greeks(0.0, 0.0, 0.0, 0.0)
    d1 = (log(spot / strike) + (rate + 0.5 * iv * iv) * t_years) / (iv * sqrt(t_years))
    d2 = d1 - iv * sqrt(t_years)
    pdf_d1 = _norm_pdf(d1)
    is_call = option_type.upper() == "CE"
    delta = _norm_cdf(d1) if is_call else _norm_cdf(d1) - 1.0
    gamma = pdf_d1 / (spot * iv * sqrt(t_years))
    theta_calendar = (
        -spot * pdf_d1 * iv / (2.0 * sqrt(t_years))
        - rate * strike * exp(-rate * t_years) * (_norm_cdf(d2) if is_call else -_norm_cdf(-d2))
    )
    # convert annual theta to per-day (approximate)
    theta = theta_calendar / 365.0
    vega = spot * pdf_d1 * sqrt(t_years) / 100.0  # per 1 vol-pt
    return Greeks(delta=delta, gamma=gamma, theta=theta, vega=vega)


def iv_rank(series: pd.Series, lookback: int = 252) -> pd.Series:
    """IV rank over a rolling window in [0, 100]."""
    min_p = min(lookback, max(5, lookback // 5))
    lo = series.rolling(lookback, min_periods=min_p).min()
    hi = series.rolling(lookback, min_periods=min_p).max()
    return 100.0 * (series - lo) / (hi - lo).replace(0.0, np.nan)


def iv_percentile(series: pd.Series, lookback: int = 252) -> pd.Series:
    def _pct(arr: np.ndarray) -> float:
        return 100.0 * (arr[:-1] < arr[-1]).mean() if len(arr) > 1 else np.nan
    min_p = min(lookback, max(5, lookback // 5))
    return series.rolling(lookback, min_periods=min_p).apply(_pct, raw=True)


def put_call_ratio(chain: pd.DataFrame) -> float:
    """PCR = total PE OI / total CE OI across an options chain snapshot."""
    oi_by_type = chain.groupby("option_type")["open_interest"].sum()
    ce = float(oi_by_type.get("CE", 0))
    pe = float(oi_by_type.get("PE", 0))
    return pe / ce if ce > 0 else np.inf


def max_pain(chain: pd.DataFrame) -> float:
    """Strike at which total option buyer loss is minimized.

    Expects columns: strike, option_type ('CE'|'PE'), open_interest.
    Returns the strike (float). For empty/degenerate chains returns NaN.
    """
    if chain.empty:
        return float("nan")
    strikes = np.sort(chain["strike"].unique())
    calls = chain[chain["option_type"] == "CE"].set_index("strike")["open_interest"]
    puts = chain[chain["option_type"] == "PE"].set_index("strike")["open_interest"]
    pain = []
    for s in strikes:
        call_pain = ((np.maximum(strikes - s, 0) * calls.reindex(strikes, fill_value=0).values)).sum()
        put_pain = ((np.maximum(s - strikes, 0) * puts.reindex(strikes, fill_value=0).values)).sum()
        pain.append(call_pain + put_pain)
    return float(strikes[int(np.argmin(pain))])


def oi_buildup(
    current: pd.DataFrame,
    prior: pd.DataFrame,
    price_col: str = "ltp",
    oi_col: str = "open_interest",
) -> pd.Series:
    """Classify each strike's buildup: long_buildup / short_buildup / long_unwind / short_cover.

    Returns a Series indexed by (strike, option_type).
    """
    merged = current.merge(
        prior[["strike", "option_type", price_col, oi_col]],
        on=["strike", "option_type"],
        suffixes=("", "_prev"),
    )
    price_up = merged[price_col] > merged[f"{price_col}_prev"]
    oi_up = merged[oi_col] > merged[f"{oi_col}_prev"]
    labels = []
    for p_up, o_up in zip(price_up, oi_up, strict=True):
        if p_up and o_up:
            labels.append("long_buildup")
        elif not p_up and o_up:
            labels.append("short_buildup")
        elif p_up and not o_up:
            labels.append("short_cover")
        else:
            labels.append("long_unwind")
    merged["buildup"] = labels
    return merged.set_index(["strike", "option_type"])["buildup"]
