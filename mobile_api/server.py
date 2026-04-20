"""FastAPI server wrapping the trading agent — LAN/Tailscale access from phone."""
from __future__ import annotations

import os
from pathlib import Path

from fastapi import FastAPI

from broker.paper import PaperBroker
from execution.agent import TradingAgent, on_paper_fill_factory
from execution.order_manager import OrderManager
from mobile_api.auth import hash_secret
from mobile_api.routes_rest import ApiDeps, build_router
from mobile_api.routes_ws import build_ws_router
from mobile_api.state_bus import StateBus
from risk.engine import RiskEngine
from risk.kill_switch import KillSwitch
from risk.limits import RiskLimits
from strategies.registry import load
from strategies.rule_strategy import RuleStrategy

API_SECRET_ENV = "MOBILE_API_SECRET"
SHARED_SECRET_ENV = "MOBILE_API_SHARED_SECRET"


def _build_agent(bus: StateBus) -> tuple[TradingAgent, PaperBroker, RiskEngine]:
    from config import get_settings
    settings = get_settings()
    broker = PaperBroker()
    kill = KillSwitch()
    risk = RiskEngine(
        limits=RiskLimits(
            max_daily_loss=settings.scalp_max_daily_loss,
            kelly_max_fraction=settings.scalp_kelly_fraction,
        ),
        capital=settings.scalp_capital,
        kill_switch=kill,
    )
    om = OrderManager(broker=broker)

    registry_path = Path("discovered_strategies.json")
    rules = load(registry_path)
    # Fallback no-op strategy if registry empty (e.g. fresh install)
    if not rules:
        from strategies.registry import RuleRecord
        rules = [RuleRecord(
            key="noop", feature="rsi", entry_op="<", entry_threshold=-1.0,
            exit_bars=10, stop_loss_pct=0.01, take_profit_pct=0.02,
            sharpe_net=0.0, net_pnl=0.0, trades=0, fold_metrics=[],
        )]
    strat = RuleStrategy(rules[0])
    agent = TradingAgent(
        strategy=strat,
        broker=broker,
        risk=risk,
        order_mgr=om,
        instrument_key="NSE_INDEX|Nifty 50",
        event_hook=bus.publish,
    )
    broker.listener = on_paper_fill_factory(agent)
    return agent, broker, risk


def create_app() -> FastAPI:
    api_secret = os.environ.get(API_SECRET_ENV, "change-me-please-use-long-random-string")
    shared_secret = os.environ.get(SHARED_SECRET_ENV, "change-me-phone-password")

    bus = StateBus()
    agent, broker, risk = _build_agent(bus)

    app = FastAPI(title="scalp-mobile-api", version="1.0")

    deps = ApiDeps(
        broker=broker,
        risk=risk,
        bus=bus,
        api_secret=api_secret,
        api_secret_hash=hash_secret(shared_secret),
    )
    app.include_router(build_router(deps))
    app.include_router(build_ws_router(bus, api_secret))

    # Expose the agent so external runners (main.py --mode paper) can drive it
    app.state.agent = agent
    app.state.broker = broker
    app.state.risk = risk
    app.state.bus = bus

    @app.get("/healthz")
    def health() -> dict:
        return {"ok": True, "kill_switch": risk.kill_switch.engaged()}

    return app


app = create_app()
