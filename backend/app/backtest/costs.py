"""Execution cost model: brokerage + slippage + execution delay."""

from __future__ import annotations

from dataclasses import dataclass


@dataclass(slots=True)
class CostModel:
    brokerage_per_trade: float = 20.0   # Zerodha-style round trip ≈ 40; half per leg
    slippage_bps: float = 2.0           # 2 bps = 0.02% per side
    execution_delay_bars: int = 1       # fills N bars after signal bar

    def round_trip_cost(self, qty: int = 1) -> float:
        return 2 * self.brokerage_per_trade * qty

    def slip(self, price: float, side_is_buy: bool) -> float:
        """Buy fills above quote, sell below."""
        adj = price * self.slippage_bps / 10_000
        return price + adj if side_is_buy else price - adj
