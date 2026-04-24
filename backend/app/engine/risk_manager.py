"""Risk manager: enforces trade caps, loss halts, and cooldowns."""

from __future__ import annotations

from collections import deque
from dataclasses import dataclass, field
from datetime import datetime, timedelta, timezone

from ..config import settings
from ..domain.trades import ClosedTrade


@dataclass(slots=True)
class RiskDecision:
    accepted: bool
    reason: str | None = None

    @classmethod
    def ok(cls) -> "RiskDecision":
        return cls(accepted=True)

    @classmethod
    def reject(cls, reason: str) -> "RiskDecision":
        return cls(accepted=False, reason=reason)


@dataclass(slots=True)
class RiskManager:
    max_trades_per_hour: int = settings.risk_max_trades_per_hour
    consecutive_loss_halt: int = settings.risk_consecutive_loss_halt
    daily_loss_limit: float = settings.risk_daily_loss_limit
    cooldown_seconds: int = settings.risk_cooldown_seconds

    _recent: deque[datetime] = field(default_factory=lambda: deque(maxlen=200))
    _consecutive_losses: int = 0
    _daily_pnl: float = 0.0
    _day: datetime | None = None
    _halted_reason: str | None = None
    _cooldown_until: datetime | None = None

    def evaluate(self, now: datetime) -> RiskDecision:
        """Can we take a new trade right now?"""
        self._maybe_reset_day(now)
        # prune trades older than an hour from the rate-limit window
        cutoff = now - timedelta(hours=1)
        while self._recent and self._recent[0] < cutoff:
            self._recent.popleft()
        if self._halted_reason:
            return RiskDecision.reject(self._halted_reason)
        if self._cooldown_until and now < self._cooldown_until:
            return RiskDecision.reject(
                f"cooldown until {self._cooldown_until.isoformat()}"
            )
        if len(self._recent) >= self.max_trades_per_hour:
            return RiskDecision.reject(
                f"max_trades_per_hour ({self.max_trades_per_hour}) hit"
            )
        return RiskDecision.ok()

    def record_trade_opened(self, ts: datetime) -> None:
        self._recent.append(ts)

    def record_trade_closed(self, trade: ClosedTrade) -> None:
        self._maybe_reset_day(trade.closed_at)
        self._daily_pnl += trade.net_pnl

        if trade.net_pnl < 0:
            self._consecutive_losses += 1
            self._cooldown_until = trade.closed_at + timedelta(
                seconds=self.cooldown_seconds
            )
            if self._consecutive_losses >= self.consecutive_loss_halt:
                self._halted_reason = (
                    f"halted after {self._consecutive_losses} consecutive losses"
                )
        else:
            self._consecutive_losses = 0

        if self._daily_pnl <= -self.daily_loss_limit:
            self._halted_reason = (
                f"daily loss limit hit (₹{self._daily_pnl:.0f})"
            )

    def reset_manual(self) -> None:
        self._halted_reason = None
        self._cooldown_until = None
        self._consecutive_losses = 0

    def state(self) -> dict:
        return {
            "halted_reason": self._halted_reason,
            "consecutive_losses": self._consecutive_losses,
            "daily_pnl": round(self._daily_pnl, 2),
            "trades_last_hour": len(self._recent),
            "cooldown_until": (
                self._cooldown_until.isoformat() if self._cooldown_until else None
            ),
        }

    def _maybe_reset_day(self, now: datetime) -> None:
        today = now.astimezone(timezone.utc).date()
        if self._day is None:
            self._day = today
            return
        if self._day != today:
            self._day = today
            self._daily_pnl = 0.0
            self._consecutive_losses = 0
            self._halted_reason = None
            self._cooldown_until = None
            self._recent.clear()
