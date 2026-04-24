"""Real-time signal engine.

Wires together:
  Upstox ticks  →  TickProcessor (1-minute candles)
                →  Strategy.on_candle (deterministic)
                →  RiskManager gate
                →  PaperTrader fill
                →  observers (DB / websocket stream)
"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Callable

from loguru import logger

from ..domain.signals import Candle, Signal, Tick
from ..domain.strategies import build_all
from ..domain.strategies.base import Strategy
from .paper_trader import PaperTrader
from .risk_manager import RiskManager
from .tick_processor import TickProcessor


SignalObserver = Callable[[Signal], None]


class SignalEngine:
    def __init__(
        self,
        strategies: list[Strategy] | None = None,
        processor: TickProcessor | None = None,
        trader: PaperTrader | None = None,
        risk: RiskManager | None = None,
        bucket_seconds: int = 60,
    ) -> None:
        self.strategies = strategies or build_all()
        self.processor = processor or TickProcessor(bucket_seconds=bucket_seconds)
        self.trader = trader or PaperTrader()
        self.risk = risk or RiskManager()
        self._observers: list[SignalObserver] = []
        self._live_signals: list[Signal] = []

    def subscribe(self, observer: SignalObserver) -> None:
        self._observers.append(observer)

    @property
    def recent_signals(self) -> list[Signal]:
        return list(self._live_signals[-50:])

    def on_tick(self, tick: Tick) -> None:
        closed = self.processor.on_tick(tick)
        # Exit check on every tick
        closed_trade = self.trader.on_tick(tick.instrument, tick.price, tick.ts)
        if closed_trade is not None:
            self.risk.record_trade_closed(closed_trade)

        if closed is not None:
            self._on_candle_closed(closed)

    def _on_candle_closed(self, candle: Candle) -> None:
        best: Signal | None = None
        for s in self.strategies:
            sig = s.on_candle(candle)
            if sig is not None and (best is None or sig.confidence > best.confidence):
                best = sig
        if best is None:
            return
        self._live_signals.append(best)

        decision = self.risk.evaluate(best.timestamp)
        if not decision.accepted:
            logger.warning(f"risk rejected {best.strategy} signal: {decision.reason}")
            for obs in self._observers:
                obs(best)
            return

        if self.trader.has_open(best.instrument):
            for obs in self._observers:
                obs(best)
            return

        pos = self.trader.apply_signal(best)
        if pos is not None:
            self.risk.record_trade_opened(best.timestamp)
        for obs in self._observers:
            obs(best)

    def state(self) -> dict:
        return {
            "open_positions": [p.__dict__ for p in self.trader.open_positions],
            "closed_trades_today": [
                t.to_dict()
                for t in self.trader.closed_trades
                if t.closed_at.astimezone(timezone.utc).date()
                == datetime.now(timezone.utc).date()
            ],
            "risk": self.risk.state(),
            "recent_signals": [s.to_dict() for s in self.recent_signals],
        }
