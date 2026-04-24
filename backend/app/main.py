"""FastAPI application entrypoint.

Run:
  uvicorn app.main:app --host 0.0.0.0 --port 8000

Optional environment:
  UPSTOX_ACCESS_TOKEN — enables live WebSocket tick subscription
  ANTHROPIC_API_KEY   — enables Claude analyst; otherwise heuristic fallback
  TELEGRAM_BOT_TOKEN + TELEGRAM_CHAT_ID — enables Telegram reports
"""

from __future__ import annotations

import asyncio
from contextlib import asynccontextmanager

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from loguru import logger
from sqlalchemy.ext.asyncio import AsyncSession

from .api import routes_lab, routes_performance, routes_signals
from .api.deps import set_engine
from .config import settings
from .db.repositories import insert_signal, insert_trade
from .db.session import init_db, session_scope
from .engine.signal_engine import SignalEngine
from .scheduler import build_scheduler
from .upstox.ws_client import UpstoxWebSocket


async def _persist_signal(sess: AsyncSession, sig) -> None:
    await insert_signal(sess, sig)


@asynccontextmanager
async def lifespan(app: FastAPI):
    logger.info("initialising database")
    await init_db()

    engine = SignalEngine()
    set_engine(engine)

    # Observer: persist every emitted signal
    async def on_signal_async(sig) -> None:
        async with session_scope() as sess:
            await insert_signal(sess, sig)

    def on_signal(sig) -> None:
        asyncio.create_task(on_signal_async(sig))

    engine.subscribe(on_signal)

    # Observer: persist closed trades (wrap paper-trader close)
    original_close = engine.trader._close

    def close_with_persistence(*args, **kwargs):
        closed = original_close(*args, **kwargs)
        asyncio.create_task(_persist_closed(closed))
        return closed

    engine.trader._close = close_with_persistence  # type: ignore[method-assign]

    # Upstox live feed if credentials present
    upstox_task: asyncio.Task | None = None
    if settings.upstox_access_token:
        ws = UpstoxWebSocket()
        upstox_task = asyncio.create_task(ws.run(engine.on_tick))
        logger.info("Upstox WS task started")
    else:
        logger.warning("UPSTOX_ACCESS_TOKEN not set; backend running without live feed")

    # Scheduler for reports
    scheduler = build_scheduler(engine)
    scheduler.start()
    logger.info("scheduler started")

    try:
        yield
    finally:
        scheduler.shutdown()
        if upstox_task:
            upstox_task.cancel()
        logger.info("shutdown complete")


async def _persist_closed(closed) -> None:
    async with session_scope() as sess:
        await insert_trade(sess, closed)


app = FastAPI(
    title="Scalping Backend",
    version="0.1.0",
    lifespan=lifespan,
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)
app.include_router(routes_signals.router)
app.include_router(routes_lab.router)
app.include_router(routes_performance.router)


@app.get("/health")
async def health() -> dict:
    return {"status": "ok", "version": app.version}
