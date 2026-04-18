"""Authenticated WebSocket that streams the agent state to connected phones."""

from __future__ import annotations

import asyncio
import logging
from datetime import datetime

from fastapi import APIRouter, Query, WebSocket, WebSocketDisconnect, status

from mobile_api import auth
from mobile_api.schemas import WsEnvelope
from mobile_api.state_bus import bus

logger = logging.getLogger(__name__)
router = APIRouter()

HEARTBEAT_SECONDS = 10


@router.websocket("/ws/stream")
async def ws_stream(websocket: WebSocket, token: str = Query(default="")) -> None:
    try:
        auth.require_token_query(token)
    except Exception:
        await websocket.close(code=status.WS_1008_POLICY_VIOLATION)
        return

    await websocket.accept()

    snapshot = bus.snapshot()
    await websocket.send_json(
        WsEnvelope(type="snapshot", payload=snapshot.model_dump(mode="json")).model_dump()
    )

    queue = await bus.subscribe()
    heartbeat_task = asyncio.create_task(_heartbeat(websocket))

    try:
        while True:
            envelope = await queue.get()
            await websocket.send_json(envelope.model_dump())
    except WebSocketDisconnect:
        pass
    except Exception:
        logger.exception("ws_stream error")
    finally:
        heartbeat_task.cancel()
        bus.unsubscribe(queue)


async def _heartbeat(websocket: WebSocket) -> None:
    try:
        while True:
            await asyncio.sleep(HEARTBEAT_SECONDS)
            await websocket.send_json(
                WsEnvelope(
                    type="heartbeat",
                    payload={"time": datetime.now().isoformat()},
                ).model_dump()
            )
    except Exception:
        return
