"""Fractional Kelly position sizing.

Kelly* = p/L - q/W  where p = win rate, q=1-p, W = avg win, L = avg loss.
We cap at `max_fraction` (default 0.25) and apply a safety multiplier (default 0.5).
"""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class KellyStats:
    win_rate: float
    avg_win: float
    avg_loss: float  # positive number


def kelly_fraction(
    stats: KellyStats,
    max_fraction: float = 0.25,
    safety: float = 0.5,
) -> float:
    """Return fractional-Kelly stake size as a fraction of capital in [0, max_fraction]."""
    if stats.avg_win <= 0 or stats.avg_loss <= 0 or not (0 < stats.win_rate < 1):
        return 0.0
    p = stats.win_rate
    q = 1.0 - p
    w_over_l = stats.avg_win / stats.avg_loss
    k = (p * w_over_l - q) / w_over_l
    k = max(0.0, k) * safety
    return min(max_fraction, k)


def position_size(
    capital: float,
    price_per_contract: float,
    lot_size: int,
    fraction: float,
    max_lots: int = 10,
) -> int:
    """Convert a Kelly fraction into integer lots, capped by `max_lots`.

    Uses premium * lot_size as the per-lot capital required (option-buyer notional).
    Conservative: no broker margin benefit assumed.
    """
    if price_per_contract <= 0 or lot_size <= 0 or fraction <= 0:
        return 0
    per_lot_cost = price_per_contract * lot_size
    allocation = capital * fraction
    lots = int(allocation // per_lot_cost)
    return max(0, min(max_lots, lots))
