"""Background driver that keeps a PaperBroker + RiskEngine alive for the mobile app.

In dev, we don't always have the full discovery + regime classifier wired up.
This runner feeds a synthetic random walk into the existing PaperBroker so the
phone has real P&L ticking. In production the trading loop (`main.py --mode paper`)
replaces this by publishing to the same `bus`.
"""

from __future__ import annotations

import logging
import random
import threading
import time
from datetime import datetime
from typing import TYPE_CHECKING

from broker.base import Order, Quote
from broker.paper import PaperBroker
from mobile_api.schemas import PositionDto, RiskStatus, TradeEvent
from risk.engine import RiskConfig, RiskEngine

if TYPE_CHECKING:
    from mobile_api.state_bus import StateBus
    from mobile_api.server import AgentContext

logger = logging.getLogger(__name__)


def start_demo_agent(bus: "StateBus") -> "AgentContext":
    from mobile_api.server import AgentContext

    risk_engine = RiskEngine(RiskConfig(trading_capital=100000, max_loss_per_day=2000))

    instrument = "NSE_INDEX|Nifty 50"
    symbol = "NIFTY"
    price_state = {"ltp": 22000.0}

    def quote_provider(key: str) -> Quote:
        return Quote(instrument_key=key, ltp=price_state["ltp"])

    broker = PaperBroker(slippage_bps=5, quote_provider=quote_provider)
    bus.update_meta(paper_mode=True, symbol=symbol)

    thread = threading.Thread(
        target=_drive,
        args=(bus, broker, risk_engine, instrument, symbol, price_state),
        daemon=True,
        name="demo-agent-runner",
    )
    thread.start()

    return AgentContext(agent=None, risk_engine=risk_engine, broker=broker)  # type: ignore[arg-type]


def _drive(
    bus: "StateBus",
    broker: PaperBroker,
    risk_engine: RiskEngine,
    instrument: str,
    symbol: str,
    price_state: dict,
) -> None:
    rng = random.Random(42)
    open_side: str | None = None
    open_qty = 0
    open_price = 0.0
    bars = 0

    _publish_risk(bus, risk_engine)

    while True:
        time.sleep(2.0)

        if risk_engine.check_kill_switch() or risk_engine.daily_pnl.halted:
            _publish_risk(bus, risk_engine)
            _publish_positions(bus, broker, price_state["ltp"])
            continue

        step = rng.gauss(0, 8.0)
        price_state["ltp"] = max(100.0, price_state["ltp"] + step)
        bars += 1

        if open_side is None and bars % 4 == 0:
            side = "buy" if rng.random() < 0.55 else "sell"
            qty = 75  # one Nifty lot
            order = broker.place_order(
                Order(
                    order_id="",
                    instrument_key=instrument,
                    symbol=symbol,
                    side=side,
                    quantity=qty,
                    order_type="market",
                    price=price_state["ltp"],
                    timestamp=datetime.now(),
                )
            )
            open_side = side
            open_qty = qty
            open_price = order.filled_price or price_state["ltp"]
            risk_engine.open_position(
                {"id": order.order_id, "symbol": symbol, "entry_price": open_price, "quantity": qty}
            )
            bus.publish_event(
                TradeEvent(
                    timestamp=datetime.now(),
                    kind="fill",
                    message=f"{side.upper()} {qty} {symbol} @ {open_price:.2f}",
                    symbol=symbol,
                    side=side,
                    quantity=qty,
                    price=open_price,
                    strategy="demo",
                )
            )

        elif open_side is not None and bars % 9 == 0:
            exit_side = "sell" if open_side == "buy" else "buy"
            order = broker.place_order(
                Order(
                    order_id="",
                    instrument_key=instrument,
                    symbol=symbol,
                    side=exit_side,
                    quantity=open_qty,
                    order_type="market",
                    price=price_state["ltp"],
                    timestamp=datetime.now(),
                )
            )
            exit_price = order.filled_price or price_state["ltp"]
            pnl = (exit_price - open_price) * open_qty if open_side == "buy" else (open_price - exit_price) * open_qty
            risk_engine.record_trade(pnl)
            if risk_engine.open_positions:
                risk_engine.close_position(risk_engine.open_positions[0]["id"])
            bus.publish_event(
                TradeEvent(
                    timestamp=datetime.now(),
                    kind="exit",
                    message=f"EXIT {symbol} P&L ₹{pnl:.0f}",
                    symbol=symbol,
                    side=exit_side,
                    quantity=open_qty,
                    price=exit_price,
                    pnl=pnl,
                    strategy="demo",
                ),
                push=abs(pnl) > 500,
            )
            open_side = None
            open_qty = 0
            open_price = 0.0

        _publish_positions(bus, broker, price_state["ltp"])
        _publish_risk(bus, risk_engine)


def _publish_positions(bus: "StateBus", broker: PaperBroker, ltp: float) -> None:
    dtos: list[PositionDto] = []
    for pos in broker.get_positions():
        unrealized = (ltp - pos.average_price) * pos.quantity
        dtos.append(
            PositionDto(
                instrument_key=pos.instrument_key,
                symbol=pos.symbol,
                quantity=pos.quantity,
                average_price=pos.average_price,
                last_price=ltp,
                unrealized_pnl=unrealized,
            )
        )
    bus.update_positions(dtos)


def _publish_risk(bus: "StateBus", risk_engine: RiskEngine) -> None:
    status = risk_engine.status()
    bus.update_risk(
        RiskStatus(
            daily_pnl=status["daily_pnl"],
            trades_today=status["trades_today"],
            open_positions=status["open_positions"],
            halted=status["halted"],
            halt_reason=status["halt_reason"],
            kill_switch_active=status["kill_switch_active"],
            daily_loss_cap=risk_engine.config.max_loss_per_day,
            trading_capital=risk_engine.config.trading_capital,
        )
    )
