"""In-process pub/sub bridging the trading agent and connected WebSocket clients.

Events flow: TradingAgent / RiskEngine / PaperBroker -> StateBus.publish()
                                                   -> all WebSocket subscribers
                                                   -> FCM push sender (for high-priority)
"""

from __future__ import annotations

import asyncio
import logging
from collections import deque
from datetime import datetime
from typing import Callable, Deque, List, Optional

from mobile_api.schemas import (
    PositionDto,
    RiskStatus,
    StateSnapshot,
    TradeEvent,
    WsEnvelope,
)

logger = logging.getLogger(__name__)


class StateBus:
    """Single source of truth for live agent state shared with mobile clients."""

    def __init__(self, max_events: int = 200, max_pnl_points: int = 300) -> None:
        self._subscribers: List[asyncio.Queue[WsEnvelope]] = []
        self._events: Deque[TradeEvent] = deque(maxlen=max_events)
        self._pnl_curve: Deque[float] = deque(maxlen=max_pnl_points)
        self._push_sender: Optional[Callable[[TradeEvent], None]] = None
        self._loop: Optional[asyncio.AbstractEventLoop] = None

        # Latest known state (updated by the agent on each bar)
        self._risk: Optional[RiskStatus] = None
        self._positions: List[PositionDto] = []
        self._paper_mode: bool = True
        self._symbol: str = "NIFTY"

    def bind_loop(self, loop: asyncio.AbstractEventLoop) -> None:
        self._loop = loop

    def set_push_sender(self, sender: Callable[[TradeEvent], None]) -> None:
        self._push_sender = sender

    async def subscribe(self) -> asyncio.Queue[WsEnvelope]:
        queue: asyncio.Queue[WsEnvelope] = asyncio.Queue(maxsize=256)
        self._subscribers.append(queue)
        return queue

    def unsubscribe(self, queue: asyncio.Queue[WsEnvelope]) -> None:
        if queue in self._subscribers:
            self._subscribers.remove(queue)

    def snapshot(self) -> StateSnapshot:
        return StateSnapshot(
            server_time=datetime.now(),
            paper_mode=self._paper_mode,
            symbol=self._symbol,
            risk=self._risk or _empty_risk(),
            positions=list(self._positions),
            recent_events=list(self._events),
            pnl_curve=list(self._pnl_curve),
        )

    # --- publisher API (called from sync agent code) -----------------------

    def update_risk(self, risk: RiskStatus) -> None:
        self._risk = risk
        self._pnl_curve.append(risk.daily_pnl)
        self._broadcast(WsEnvelope(type="risk", payload=risk.model_dump(mode="json")))

    def update_positions(self, positions: List[PositionDto]) -> None:
        self._positions = positions

    def update_meta(self, paper_mode: bool, symbol: str) -> None:
        self._paper_mode = paper_mode
        self._symbol = symbol

    def publish_event(self, event: TradeEvent, push: bool = False) -> None:
        self._events.append(event)
        self._broadcast(WsEnvelope(type="event", payload=event.model_dump(mode="json")))
        if push and self._push_sender is not None:
            try:
                self._push_sender(event)
            except Exception:
                logger.exception("push sender failed")

    # --- internals ---------------------------------------------------------

    def _broadcast(self, envelope: WsEnvelope) -> None:
        if self._loop is None:
            return
        self._loop.call_soon_threadsafe(self._dispatch_sync, envelope)

    def _dispatch_sync(self, envelope: WsEnvelope) -> None:
        dead: List[asyncio.Queue[WsEnvelope]] = []
        for queue in self._subscribers:
            try:
                queue.put_nowait(envelope)
            except asyncio.QueueFull:
                dead.append(queue)
        for q in dead:
            self.unsubscribe(q)


def _empty_risk() -> RiskStatus:
    return RiskStatus(
        daily_pnl=0.0,
        trades_today=0,
        open_positions=0,
        halted=False,
        halt_reason="",
        kill_switch_active=False,
        daily_loss_cap=2000.0,
        trading_capital=100000.0,
    )


# Singleton used across the FastAPI process.
bus = StateBus()
