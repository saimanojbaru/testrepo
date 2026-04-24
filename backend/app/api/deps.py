"""Shared API dependencies: engine + DB session."""

from __future__ import annotations

from typing import AsyncIterator

from sqlalchemy.ext.asyncio import AsyncSession

from ..db.session import session_scope
from ..engine.signal_engine import SignalEngine

# App-lifetime singleton wired in main.py
_engine: SignalEngine | None = None


def set_engine(engine: SignalEngine) -> None:
    global _engine
    _engine = engine


def get_engine() -> SignalEngine:
    if _engine is None:
        raise RuntimeError("SignalEngine not initialised")
    return _engine


async def get_session() -> AsyncIterator[AsyncSession]:
    async with session_scope() as sess:
        yield sess
