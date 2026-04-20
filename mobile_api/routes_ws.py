"""WebSocket /ws/stream — snapshot + delta push to the phone."""
from __future__ import annotations

import asyncio
import json

from fastapi import APIRouter, WebSocket, WebSocketDisconnect

from mobile_api.auth import verify_token
from mobile_api.state_bus import StateBus


def build_ws_router(bus: StateBus, api_secret: str) -> APIRouter:
    router = APIRouter()

    @router.websocket("/ws/stream")
    async def ws_stream(ws: WebSocket) -> None:
        token = ws.query_params.get("token", "")
        if not verify_token(token, api_secret):
            await ws.close(code=4401)
            return
        await ws.accept()

        q = bus.subscribe()
        # initial snapshot
        await ws.send_text(json.dumps({"type": "snapshot", "data": bus.snapshot()}))

        heartbeat_task = asyncio.create_task(_heartbeat(ws))
        try:
            while True:
                event = await q.get()
                payload = event.payload
                if hasattr(payload, "__dict__"):
                    data = {
                        k: (v.isoformat() if hasattr(v, "isoformat") else v)
                        for k, v in payload.__dict__.items()
                    }
                elif isinstance(payload, dict):
                    data = payload
                else:
                    data = {"value": str(payload)}
                await ws.send_text(
                    json.dumps(
                        {
                            "type": "delta",
                            "kind": event.kind,
                            "ts": event.ts.isoformat(),
                            "data": data,
                        },
                        default=str,
                    )
                )
        except WebSocketDisconnect:
            pass
        finally:
            heartbeat_task.cancel()
            bus.unsubscribe(q)

    return router


async def _heartbeat(ws: WebSocket, interval: float = 15.0) -> None:
    try:
        while True:
            await asyncio.sleep(interval)
            await ws.send_text(json.dumps({"type": "heartbeat"}))
    except Exception:
        pass
