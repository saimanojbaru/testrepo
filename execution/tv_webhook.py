"""TradingView webhook receiver — Pine Script alerts -> agent signals.

Security: HMAC-SHA256 on a shared secret (TV supports `{{secret}}` in alert body).
Each alert payload has: symbol, direction (LONG|SHORT|FLAT), stop_loss_pct, take_profit_pct.

Signals are pushed to the agent's internal queue via `handler` callback.
"""
from __future__ import annotations

import hmac
import hashlib
from dataclasses import dataclass
from typing import Callable

from fastapi import FastAPI, HTTPException, Request
from pydantic import BaseModel

from strategies.base import Signal, SignalDirection


class TVAlert(BaseModel):
    secret: str
    symbol: str
    direction: str
    stop_loss_pct: float | None = None
    take_profit_pct: float | None = None
    confidence: float = 1.0


@dataclass
class WebhookHandler:
    expected_secret_hash: str   # hex sha256 digest of the real secret
    on_signal: Callable[[str, Signal], None]

    def verify(self, alert: TVAlert) -> bool:
        presented = hashlib.sha256(alert.secret.encode()).hexdigest()
        return hmac.compare_digest(presented, self.expected_secret_hash)


def make_app(handler: WebhookHandler) -> FastAPI:
    app = FastAPI(title="scalp-tv-webhook")

    @app.post("/tv/alert")
    async def tv_alert(req: Request) -> dict:
        try:
            payload = await req.json()
            alert = TVAlert(**payload)
        except Exception as exc:
            raise HTTPException(status_code=400, detail=f"bad payload: {exc}")
        if not handler.verify(alert):
            raise HTTPException(status_code=401, detail="bad secret")
        try:
            direction = SignalDirection(alert.direction.upper())
        except ValueError:
            raise HTTPException(status_code=400, detail=f"bad direction: {alert.direction}")
        signal = Signal(
            direction=direction,
            stop_loss_pct=alert.stop_loss_pct,
            take_profit_pct=alert.take_profit_pct,
            confidence=alert.confidence,
        )
        handler.on_signal(alert.symbol, signal)
        return {"status": "ok"}

    @app.get("/healthz")
    async def health() -> dict:
        return {"ok": True}

    return app
