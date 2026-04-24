"""Pluggable strategy registry.

To add a strategy:
  1. Inherit from Strategy
  2. Implement on_candle(candle, context) -> Signal | None
  3. Append it to STRATEGIES

No other code needs to change — the lab, backtester, and signal engine all
iterate the registry.
"""

from .base import Strategy, StrategyContext
from .momentum_breakout import MomentumBreakout
from .range_breakout import RangeBreakout
from .reversal_scalp import ReversalScalp

STRATEGIES: list[type[Strategy]] = [
    MomentumBreakout,
    ReversalScalp,
    RangeBreakout,
]


def build_all() -> list[Strategy]:
    return [cls() for cls in STRATEGIES]


__all__ = [
    "Strategy",
    "StrategyContext",
    "MomentumBreakout",
    "ReversalScalp",
    "RangeBreakout",
    "STRATEGIES",
    "build_all",
]
