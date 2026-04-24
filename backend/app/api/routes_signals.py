"""Signal API routes."""

from __future__ import annotations

from fastapi import APIRouter, Depends, WebSocket, WebSocketDisconnect
from sqlalchemy.ext.asyncio import AsyncSession

from ..db.repositories import recent_signals
from ..engine.signal_engine import SignalEngine
from .deps import get_engine, get_session

router = APIRouter()


@router.get("/signals/live")
async def signals_live(engine: SignalEngine = Depends(get_engine)) -> dict:
    return {"signals": [s.to_dict() for s in engine.recent_signals]}


@router.get("/signals/history")
async def signals_history(
    limit: int = 50, sess: AsyncSession = Depends(get_session)
) -> dict:
    rows = await recent_signals(sess, limit=limit)
    return {
        "signals": [
            {
                "id": r.id,
                "created_at": r.created_at.isoformat(),
                "instrument": r.instrument,
                "strategy": r.strategy,
                "action": r.action,
                "entry_price": r.entry_price,
                "stop_loss": r.stop_loss,
                "target": r.target,
                "confidence": r.confidence,
            }
            for r in rows
        ]
    }


@router.websocket("/ws/signals")
async def signals_ws(ws: WebSocket, engine: SignalEngine = Depends(get_engine)) -> None:
    await ws.accept()
    queue: list[dict] = []

    def observer(sig) -> None:
        queue.append(sig.to_dict())

    engine.subscribe(observer)
    try:
        while True:
            while queue:
                await ws.send_json(queue.pop(0))
            await ws.send_json({"heartbeat": True})
    except WebSocketDisconnect:
        return
