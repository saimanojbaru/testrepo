"""Position + loss limits enforced on every signal."""
from __future__ import annotations

from dataclasses import dataclass


@dataclass(frozen=True)
class RiskLimits:
    max_open_positions: int = 1              # scalping: one at a time by default
    max_daily_loss: float = 2_000.0          # INR
    max_weekly_loss: float = 8_000.0
    max_single_trade_loss: float = 1_000.0
    max_lots_per_trade: int = 10
    kelly_max_fraction: float = 0.25
    kelly_safety: float = 0.5                # half-Kelly
    max_trades_per_day: int = 20             # overtrading guard
    instrument_blacklist: tuple[str, ...] = ()

    def is_blacklisted(self, instrument_key: str) -> bool:
        return instrument_key in self.instrument_blacklist
