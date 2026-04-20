"""Trading agent — wires strategy + risk + broker + order manager.

Supports two modes:
  * paper  : PaperBroker, fills simulated against latest tick/bar
  * live   : UpstoxBroker, real orders

Hooks published to subscribers (mobile API, monitor, logs):
  * on_bar(bar)          — every processed bar, includes regime + P&L
  * on_signal(signal)    — when strategy fires
  * on_fill(fill)        — broker fill
  * on_risk(violation)   — risk engine deny
  * on_kill_switch()     — kill switch engaged or disengaged
"""
from __future__ import annotations

import datetime as dt
from dataclasses import dataclass, field
from typing import Callable

import pandas as pd

from broker.base import Broker, Order, OrderSide, OrderType, Product
from broker.paper import FillEvent, PaperBroker
from execution.order_manager import OrderManager
from risk.engine import RiskDecision, RiskEngine, RiskViolation
from risk.sizer import KellyStats
from strategies.base import Signal, SignalDirection, Strategy


@dataclass(frozen=True)
class BarEvent:
    ts: pd.Timestamp
    instrument_key: str
    close: float
    position_qty: int
    unrealized_pnl: float
    realized_pnl: float


@dataclass(frozen=True)
class SignalEvent:
    ts: pd.Timestamp
    instrument_key: str
    direction: str
    decision_approved: bool
    lots: int


# Subscriber callback shape.
EventHook = Callable[[str, object], None]


@dataclass
class TradingAgent:
    strategy: Strategy
    broker: Broker
    risk: RiskEngine
    order_mgr: OrderManager
    instrument_key: str
    lot_size: int = 50
    default_win_stats: KellyStats = field(
        default_factory=lambda: KellyStats(win_rate=0.55, avg_win=200.0, avg_loss=150.0)
    )
    event_hook: EventHook | None = None

    _prepared: pd.DataFrame | None = None
    _position_direction: SignalDirection = SignalDirection.FLAT
    _position_qty: int = 0

    def _emit(self, kind: str, payload: object) -> None:
        if self.event_hook:
            self.event_hook(kind, payload)

    def prepare(self, df: pd.DataFrame) -> None:
        self._prepared = self.strategy.prepare(df).reset_index(drop=True)

    def on_tick(self, price: float) -> None:
        """Live broker price update — PaperBroker uses this to mark positions."""
        if isinstance(self.broker, PaperBroker):
            self.broker.update_price(self.instrument_key, price)

    def on_bar(self, bar: pd.Series, history: pd.DataFrame) -> None:
        """Single-bar tick of the agent loop. Emits events + may place orders."""
        self.on_tick(float(bar["close"]))

        # Kill switch first — if engaged, square off and exit.
        if self.risk.kill_switch.engaged():
            if self._position_qty != 0:
                self.broker.square_off_all()
                self._position_qty = 0
                self._position_direction = SignalDirection.FLAT
            self._emit("kill_switch", {"engaged": True})
            return

        position_qty = self._position_qty
        positions = self.broker.positions()
        pos = next((p for p in positions if p.instrument_key == self.instrument_key), None)
        realized = self.broker.pnl()
        unrealized = pos.unrealized_pnl if pos else 0.0

        bar_evt = BarEvent(
            ts=bar["ts"],
            instrument_key=self.instrument_key,
            close=float(bar["close"]),
            position_qty=position_qty,
            unrealized_pnl=unrealized,
            realized_pnl=realized,
        )
        self._emit("bar", bar_evt)

        sig = self.strategy.on_bar(bar, history)
        if sig is None or sig.direction is SignalDirection.FLAT:
            return
        if self._position_qty != 0:
            # Only one position at a time for MVP scalping
            return

        decision: RiskDecision = self.risk.evaluate(
            instrument_key=self.instrument_key,
            premium=float(bar["close"]),
            lot_size=self.lot_size,
            win_stats=self.default_win_stats,
            now=dt.datetime.now(),
        )
        self._emit(
            "signal",
            SignalEvent(
                ts=bar["ts"],
                instrument_key=self.instrument_key,
                direction=sig.direction.value,
                decision_approved=decision.approved,
                lots=decision.lots,
            ),
        )
        if not decision.approved:
            if decision.violation:
                self._emit("risk", decision.violation)
            return

        qty = decision.lots * self.lot_size
        side = OrderSide.BUY if sig.direction is SignalDirection.LONG else OrderSide.SELL
        order = Order(
            instrument_key=self.instrument_key,
            side=side,
            quantity=qty,
            order_type=OrderType.MARKET,
            product=Product.INTRADAY,
            tag=self.strategy.name,
        )
        placed = self.order_mgr.submit(order)
        self._emit("fill", placed)
        self._position_direction = sig.direction
        self._position_qty = qty if sig.direction is SignalDirection.LONG else -qty
        self.risk.on_position_opened()

    def close_position(self, exit_price: float) -> None:
        if self._position_qty == 0:
            return
        side = OrderSide.SELL if self._position_qty > 0 else OrderSide.BUY
        order = Order(
            instrument_key=self.instrument_key,
            side=side,
            quantity=abs(self._position_qty),
            order_type=OrderType.MARKET,
            product=Product.INTRADAY,
            tag=f"{self.strategy.name}_exit",
        )
        self.order_mgr.submit(order)
        # Paper broker updates realized automatically via _fill
        realized_delta = self.broker.pnl()  # cumulative; net change for this close only:
        self._emit("close", {"ts": dt.datetime.utcnow(), "realized_total": realized_delta})
        self._position_qty = 0
        self._position_direction = SignalDirection.FLAT
        self.risk.on_trade_closed(0.0)  # actual net pnl computed by broker hook below


def on_paper_fill_factory(agent: TradingAgent) -> Callable[[FillEvent], None]:
    """Wire the paper broker's fill listener into the agent's risk-engine update."""
    def _handler(evt: FillEvent) -> None:
        if evt.is_closing:
            agent.risk.on_trade_closed(evt.realized_pnl)
        agent._emit("broker_fill", evt)
    return _handler
