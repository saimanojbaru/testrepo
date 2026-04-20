"""Central risk engine — decides whether a proposed trade is allowed.

Called on every signal:
  1. Kill switch? -> deny.
  2. Kelly sizing -> compute lots.
  3. Limit checks (open positions, daily loss, weekly loss, trade count, blacklist).
  4. Return RiskDecision with approved lots or a RiskViolation explaining why.

Hooks:
  * `on_violation(viol)`      — optional callback; mobile API subscribes here
  * `on_daily_loss_breach()`  — optional callback; fires when daily_loss exceeds cap
"""
from __future__ import annotations

import datetime as dt
from dataclasses import dataclass, field
from enum import Enum
from typing import Callable

from risk.kill_switch import KillSwitch
from risk.limits import RiskLimits
from risk.sizer import KellyStats, kelly_fraction, position_size


class RiskViolationKind(str, Enum):
    KILL_SWITCH = "kill_switch"
    DAILY_LOSS_CAP = "daily_loss_cap"
    WEEKLY_LOSS_CAP = "weekly_loss_cap"
    MAX_OPEN_POSITIONS = "max_open_positions"
    INSTRUMENT_BLACKLISTED = "instrument_blacklisted"
    MAX_TRADES_PER_DAY = "max_trades_per_day"
    ZERO_SIZING = "zero_sizing"


@dataclass(frozen=True)
class RiskViolation:
    kind: RiskViolationKind
    detail: str


@dataclass(frozen=True)
class RiskDecision:
    approved: bool
    lots: int
    violation: RiskViolation | None = None


@dataclass
class RiskEngine:
    limits: RiskLimits
    capital: float
    kill_switch: KillSwitch = field(default_factory=KillSwitch)
    # mutable runtime state:
    daily_pnl: float = 0.0
    weekly_pnl: float = 0.0
    trades_today: int = 0
    open_positions: int = 0
    current_day: dt.date = field(default_factory=dt.date.today)
    current_week: int = field(default_factory=lambda: dt.date.today().isocalendar().week)
    # callbacks:
    on_violation: Callable[[RiskViolation], None] | None = None
    on_daily_loss_breach: Callable[[], None] | None = None

    def _roll_day_if_needed(self, now: dt.date) -> None:
        if now != self.current_day:
            self.daily_pnl = 0.0
            self.trades_today = 0
            self.current_day = now
        week = now.isocalendar().week
        if week != self.current_week:
            self.weekly_pnl = 0.0
            self.current_week = week

    def evaluate(
        self,
        instrument_key: str,
        premium: float,
        lot_size: int,
        win_stats: KellyStats,
        now: dt.datetime | None = None,
    ) -> RiskDecision:
        now = now or dt.datetime.now()
        self._roll_day_if_needed(now.date())

        def deny(kind: RiskViolationKind, detail: str) -> RiskDecision:
            v = RiskViolation(kind=kind, detail=detail)
            if self.on_violation:
                self.on_violation(v)
            return RiskDecision(approved=False, lots=0, violation=v)

        if self.kill_switch.engaged():
            return deny(RiskViolationKind.KILL_SWITCH, self.kill_switch.reason() or "engaged")

        if self.limits.is_blacklisted(instrument_key):
            return deny(RiskViolationKind.INSTRUMENT_BLACKLISTED, instrument_key)

        if self.daily_pnl <= -self.limits.max_daily_loss:
            if self.on_daily_loss_breach:
                self.on_daily_loss_breach()
            return deny(RiskViolationKind.DAILY_LOSS_CAP, f"daily={self.daily_pnl:.2f}")

        if self.weekly_pnl <= -self.limits.max_weekly_loss:
            return deny(RiskViolationKind.WEEKLY_LOSS_CAP, f"weekly={self.weekly_pnl:.2f}")

        if self.open_positions >= self.limits.max_open_positions:
            return deny(RiskViolationKind.MAX_OPEN_POSITIONS, str(self.open_positions))

        if self.trades_today >= self.limits.max_trades_per_day:
            return deny(RiskViolationKind.MAX_TRADES_PER_DAY, str(self.trades_today))

        frac = kelly_fraction(
            win_stats,
            max_fraction=self.limits.kelly_max_fraction,
            safety=self.limits.kelly_safety,
        )
        lots = position_size(
            capital=self.capital,
            price_per_contract=premium,
            lot_size=lot_size,
            fraction=frac,
            max_lots=self.limits.max_lots_per_trade,
        )
        if lots <= 0:
            return deny(RiskViolationKind.ZERO_SIZING, f"frac={frac:.4f}")

        return RiskDecision(approved=True, lots=lots)

    # --- runtime updates called by the trading agent ---
    def on_position_opened(self) -> None:
        self.open_positions += 1
        self.trades_today += 1

    def on_trade_closed(self, net_pnl: float) -> None:
        self.open_positions = max(0, self.open_positions - 1)
        self.daily_pnl += net_pnl
        self.weekly_pnl += net_pnl
        if self.daily_pnl <= -self.limits.max_daily_loss and self.on_daily_loss_breach:
            self.on_daily_loss_breach()
