"""Slippage model: slippage = f(spread, size, volatility).

Implements a simple but realistic model calibrated against Indian option books:
  base_slip = max(0.5 * spread, k_vol * vol * premium, k_size * sqrt(size / adv))
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class SlippageConfig:
    spread_multiplier: float = 0.5      # half-spread baseline
    vol_coefficient: float = 0.10       # extra slip in high vol
    size_coefficient: float = 0.05      # sqrt-law size impact
    percentile: float = 0.50            # 0.5 = median, 0.9 = stress test


def estimate_slippage(
    premium: float,
    spread: float,
    vol: float,
    size: int,
    avg_daily_volume: int,
    cfg: SlippageConfig = SlippageConfig(),
) -> float:
    """Returns absolute slippage in INR per unit — subtract on buys, add on sells."""
    base = cfg.spread_multiplier * max(spread, 0.0)
    vol_term = cfg.vol_coefficient * vol * premium
    size_term = 0.0
    if avg_daily_volume > 0:
        size_term = cfg.size_coefficient * premium * (size / avg_daily_volume) ** 0.5
    median = base + vol_term + size_term
    # Simple percentile scaling: assume slippage ~ lognormal with cv=0.5
    scale = 1.0 + (cfg.percentile - 0.5) * 2.0 * 0.5
    return max(0.0, median * scale)
