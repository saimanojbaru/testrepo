"""FastAPI application exposing the scalping agent to the Flutter mobile app.

Run locally (Windows):

    run_mobile_backend.bat

Or directly:

    python -m uvicorn mobile_api.server:app --host 0.0.0.0 --port 8000
"""

from __future__ import annotations

import asyncio
import logging
import os
from dataclasses import dataclass
from typing import Optional

from dotenv import load_dotenv
from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from broker.paper import PaperBroker
from execution.agent import TradingAgent
from mobile_api import push
from mobile_api.routes_rest import router as rest_router
from mobile_api.routes_ws import router as ws_router
from mobile_api.state_bus import bus
from mobile_api.demo_runner import start_demo_agent
from risk.engine import RiskEngine, RiskConfig

logger = logging.getLogger(__name__)


@dataclass
class AgentContext:
    """Holds the live agent so REST routes can reach it."""
    agent: TradingAgent
    risk_engine: RiskEngine
    broker: PaperBroker


_context: Optional[AgentContext] = None


def get_context() -> Optional[AgentContext]:
    return _context


def set_context(ctx: AgentContext) -> None:
    global _context
    _context = ctx


def create_app() -> FastAPI:
    load_dotenv()
    logging.basicConfig(
        level=os.getenv("LOG_LEVEL", "INFO"),
        format="%(asctime)s %(levelname)s %(name)s - %(message)s",
    )

    app = FastAPI(title="Scalping Agent Mobile API", version="1.0.0")
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_credentials=False,
        allow_methods=["*"],
        allow_headers=["*"],
    )
    app.include_router(rest_router)
    app.include_router(ws_router)

    @app.on_event("startup")
    async def _startup() -> None:
        loop = asyncio.get_running_loop()
        bus.bind_loop(loop)
        bus.set_push_sender(push.send_event)

        if os.getenv("MOBILE_API_DEMO", "1") == "1":
            ctx = start_demo_agent(bus)
            set_context(ctx)
            logger.info("Demo agent started - streaming simulated P&L to mobile clients")

    @app.on_event("shutdown")
    async def _shutdown() -> None:
        logger.info("Mobile API shutting down")

    return app


app = create_app()
