"""Paper trading engine — consumes signals + ticks, simulates fills."""

from __future__ import annotations

from datetime import datetime, timezone
from itertools import count

from loguru import logger

from ..backtest.costs import CostModel
from ..domain.signals import Action, Signal
from ..domain.trades import ClosedTrade, ExitReason, OpenPosition, Side


class PaperTrader:
    def __init__(self, cost: CostModel | None = None, time_exit_seconds: int = 240) -> None:
        self.cost = cost or CostModel()
        self.time_exit_seconds = time_exit_seconds
        self._open: dict[str, OpenPosition] = {}
        self._closed: list[ClosedTrade] = []
        self._ids = count(1)

    @property
    def open_positions(self) -> list[OpenPosition]:
        return list(self._open.values())

    @property
    def closed_trades(self) -> list[ClosedTrade]:
        return list(self._closed)

    def has_open(self, instrument: str) -> bool:
        return instrument in self._open

    def apply_signal(self, sig: Signal) -> OpenPosition | None:
        """Open a position if none exists for this instrument."""
        if sig.action is Action.FLAT:
            return None
        if self.has_open(sig.instrument):
            return None
        fill = self.cost.slip(sig.entry_price, side_is_buy=sig.action is Action.BUY)
        pos = OpenPosition(
            trade_id=next(self._ids),
            instrument=sig.instrument,
            side=Side.LONG if sig.action is Action.BUY else Side.SHORT,
            entry=fill,
            stop_loss=sig.stop_loss,
            target=sig.target,
            opened_at=sig.timestamp,
            strategy=sig.strategy,
            qty=1,
        )
        self._open[sig.instrument] = pos
        logger.info(f"opened {pos.side.value} {pos.instrument} @ {fill:.2f} sl={pos.stop_loss:.2f} tp={pos.target:.2f}")
        return pos

    def on_tick(self, instrument: str, price: float, ts: datetime) -> ClosedTrade | None:
        pos = self._open.get(instrument)
        if pos is None:
            return None
        reason = self._exit_reason(pos, price, ts)
        if reason is None:
            return None
        return self._close(pos, price, ts, reason)

    def manual_close(self, instrument: str, price: float, ts: datetime | None = None) -> ClosedTrade | None:
        pos = self._open.get(instrument)
        if pos is None:
            return None
        return self._close(pos, price, ts or datetime.now(timezone.utc), ExitReason.MANUAL)

    def force_close_all(self, reason: ExitReason = ExitReason.RISK_HALT) -> list[ClosedTrade]:
        out: list[ClosedTrade] = []
        for inst, pos in list(self._open.items()):
            price = pos.entry  # no fresh tick → use entry as fallback
            closed = self._close(pos, price, datetime.now(timezone.utc), reason)
            if closed:
                out.append(closed)
        return out

    def _exit_reason(self, pos: OpenPosition, price: float, ts: datetime) -> ExitReason | None:
        if pos.side is Side.LONG:
            if price <= pos.stop_loss:
                return ExitReason.STOP_LOSS
            if price >= pos.target:
                return ExitReason.TARGET
        else:
            if price >= pos.stop_loss:
                return ExitReason.STOP_LOSS
            if price <= pos.target:
                return ExitReason.TARGET
        age = (ts - pos.opened_at).total_seconds()
        if age >= self.time_exit_seconds:
            return ExitReason.TIME
        return None

    def _close(
        self, pos: OpenPosition, price: float, ts: datetime, reason: ExitReason
    ) -> ClosedTrade:
        is_buy_exit = pos.side is Side.SHORT
        fill = self.cost.slip(price, side_is_buy=is_buy_exit)
        gross = pos.mtm(fill)
        costs = self.cost.round_trip_cost(pos.qty)
        closed = ClosedTrade(
            trade_id=pos.trade_id,
            instrument=pos.instrument,
            side=pos.side,
            entry=pos.entry,
            exit_price=fill,
            qty=pos.qty,
            gross_pnl=gross,
            costs=costs,
            reason=reason,
            strategy=pos.strategy,
            opened_at=pos.opened_at,
            closed_at=ts,
        )
        del self._open[pos.instrument]
        self._closed.append(closed)
        logger.info(
            f"closed {pos.side.value} {pos.instrument} {reason.value} "
            f"net=₹{closed.net_pnl:.2f}"
        )
        return closed
