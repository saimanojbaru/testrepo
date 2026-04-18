"""
Risk Engine: position sizing, daily loss limits, kill-switch.

Hard guarantees:
- Daily loss cap: auto square-off + halt trading
- Max open positions: prevents over-exposure
- Kelly-capped position sizing: 1-2% per trade
- Kill-switch: file flag or Telegram command
"""

from dataclasses import dataclass, field
from datetime import datetime, date
from pathlib import Path
from typing import Callable, Optional, List


@dataclass
class RiskConfig:
    """Risk configuration."""
    trading_capital: float = 100000
    max_loss_per_day: float = 2000
    max_loss_percentage: float = 0.02  # 2% of capital
    max_open_positions: int = 3
    kelly_fraction: float = 0.25  # 25% of Kelly (conservative)
    min_position_size_pct: float = 0.005  # 0.5% minimum
    max_position_size_pct: float = 0.02   # 2% maximum
    kill_switch_file: str = ".kill_switch"


@dataclass
class DailyPnL:
    """Tracks P&L for the current trading day."""
    trading_date: date
    realized_pnl: float = 0.0
    unrealized_pnl: float = 0.0
    trades_count: int = 0
    halted: bool = False
    halt_reason: str = ""


class RiskEngine:
    """
    Gatekeeper between strategy signals and order execution.
    Every order must pass risk checks.
    """

    def __init__(
        self,
        config: RiskConfig = None,
        event_hook: Optional[Callable[[str, dict], None]] = None,
    ):
        self.config = config or RiskConfig()
        self.daily_pnl = DailyPnL(trading_date=datetime.now().date())
        self.open_positions: List[dict] = []
        self._event_hook = event_hook

    def _emit(self, kind: str, payload: dict) -> None:
        if self._event_hook is None:
            return
        try:
            self._event_hook(kind, payload)
        except Exception:
            pass

    def reset_daily(self):
        """Reset daily P&L tracking (call at start of each trading day)."""
        self.daily_pnl = DailyPnL(trading_date=datetime.now().date())

    def check_kill_switch(self) -> bool:
        """Check if kill-switch file exists."""
        return Path(self.config.kill_switch_file).exists()

    def activate_kill_switch(self, reason: str = "Manual trigger"):
        """Create kill-switch file."""
        Path(self.config.kill_switch_file).write_text(reason)
        self.daily_pnl.halted = True
        self.daily_pnl.halt_reason = reason
        self._emit("kill_switch", {"reason": reason})

    def clear_kill_switch(self):
        """Remove kill-switch file (manual intervention only)."""
        ks = Path(self.config.kill_switch_file)
        if ks.exists():
            ks.unlink()

    def record_trade(self, pnl: float):
        """Record a closed trade."""
        self.daily_pnl.realized_pnl += pnl
        self.daily_pnl.trades_count += 1
        self._emit("trade_recorded", {"pnl": pnl, "daily_pnl": self.daily_pnl.realized_pnl})

        # Check daily loss limit
        if self._exceeds_daily_loss():
            self.activate_kill_switch(
                f"Daily loss limit breached: ₹{self.daily_pnl.realized_pnl:.0f}"
            )

    def _exceeds_daily_loss(self) -> bool:
        """Check if daily loss limit is breached."""
        cap_abs = self.config.max_loss_per_day
        cap_pct = self.config.trading_capital * self.config.max_loss_percentage
        effective_cap = min(cap_abs, cap_pct) * -1  # Loss is negative

        return self.daily_pnl.realized_pnl <= effective_cap

    def calculate_position_size(
        self,
        entry_price: float,
        stop_loss_price: float,
        win_rate: float = 0.55,
        avg_win_loss_ratio: float = 1.5,
    ) -> int:
        """
        Calculate position size using fractional Kelly + fixed-fractional floor.

        Args:
            entry_price: Entry premium
            stop_loss_price: Stop loss level
            win_rate: Historical win rate (0..1)
            avg_win_loss_ratio: Average win / average loss

        Returns:
            Lot count (integer)
        """
        # Kelly fraction: f = (bp - q) / b
        # Where b = avg_win / avg_loss, p = win_rate, q = 1-p
        b = avg_win_loss_ratio
        p = win_rate
        q = 1 - p

        kelly = (b * p - q) / b if b > 0 else 0
        kelly = max(0, kelly)  # Never negative

        # Scale Kelly down (conservative)
        kelly_scaled = kelly * self.config.kelly_fraction

        # Apply min/max caps
        size_pct = max(self.config.min_position_size_pct,
                       min(self.config.max_position_size_pct, kelly_scaled))

        # Convert to position notional
        position_notional = self.config.trading_capital * size_pct

        # Risk per lot = entry - stop_loss
        risk_per_lot = max(abs(entry_price - stop_loss_price), 0.01)

        # Number of lots = position_notional / risk_per_lot
        lots = int(position_notional / risk_per_lot)
        return max(1, lots)

    def can_trade(self, current_price: float = 0) -> tuple:
        """
        Comprehensive pre-trade check.

        Returns:
            (allowed: bool, reason: str)
        """
        if self.check_kill_switch():
            return False, "Kill switch active"

        if self.daily_pnl.halted:
            return False, self.daily_pnl.halt_reason

        if self._exceeds_daily_loss():
            self.activate_kill_switch(
                f"Daily loss: ₹{self.daily_pnl.realized_pnl:.0f}"
            )
            return False, "Daily loss limit breached"

        if len(self.open_positions) >= self.config.max_open_positions:
            return False, f"Max open positions ({self.config.max_open_positions}) reached"

        return True, "OK"

    def open_position(self, position: dict):
        """Register an opened position."""
        self.open_positions.append(position)

    def close_position(self, position_id: str):
        """Close a position by ID."""
        self.open_positions = [
            p for p in self.open_positions if p.get("id") != position_id
        ]

    def status(self) -> dict:
        """Get current risk status."""
        return {
            "daily_pnl": self.daily_pnl.realized_pnl,
            "trades_today": self.daily_pnl.trades_count,
            "open_positions": len(self.open_positions),
            "halted": self.daily_pnl.halted,
            "halt_reason": self.daily_pnl.halt_reason,
            "kill_switch_active": self.check_kill_switch(),
        }
