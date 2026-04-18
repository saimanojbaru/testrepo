"""REST endpoints consumed by the Flutter app."""

from __future__ import annotations

from datetime import datetime
from typing import Optional

from fastapi import APIRouter, Depends, HTTPException, status

from mobile_api import auth
from mobile_api.schemas import (
    KillSwitchRequest,
    KillSwitchResponse,
    LoginRequest,
    LoginResponse,
    RiskConfigPatch,
    StateSnapshot,
    TradeEvent,
)
from mobile_api.state_bus import bus

router = APIRouter()


def _agent_context():
    """Resolved lazily so tests can mount the router without a running agent."""
    from mobile_api.server import get_context

    ctx = get_context()
    if ctx is None:
        raise HTTPException(
            status_code=status.HTTP_503_SERVICE_UNAVAILABLE,
            detail="Trading agent has not been started on the backend yet.",
        )
    return ctx


@router.post("/login", response_model=LoginResponse)
def login(req: LoginRequest) -> LoginResponse:
    if not auth.verify_login_secret(req.shared_secret):
        raise HTTPException(status_code=status.HTTP_401_UNAUTHORIZED, detail="Bad shared secret")
    token, expires_at = auth.issue_token(req.device_id)
    return LoginResponse(token=token, expires_at=expires_at)


@router.get("/state", response_model=StateSnapshot)
def get_state(_: dict = Depends(auth.require_bearer)) -> StateSnapshot:
    return bus.snapshot()


@router.post("/kill-switch", response_model=KillSwitchResponse)
def kill_switch(
    req: KillSwitchRequest,
    _: dict = Depends(auth.require_bearer),
) -> KillSwitchResponse:
    ctx = _agent_context()
    ctx.risk_engine.activate_kill_switch(req.reason)
    squared = False
    try:
        squared = bool(ctx.broker.square_off_all())
    except Exception:
        squared = False

    bus.publish_event(
        TradeEvent(
            timestamp=datetime.now(),
            kind="kill_switch",
            message=f"Kill switch activated: {req.reason}",
        ),
        push=True,
    )
    return KillSwitchResponse(active=True, reason=req.reason, squared_off=squared)


@router.post("/kill-switch/clear")
def clear_kill_switch(_: dict = Depends(auth.require_bearer)) -> dict:
    ctx = _agent_context()
    ctx.risk_engine.clear_kill_switch()
    ctx.risk_engine.daily_pnl.halted = False
    ctx.risk_engine.daily_pnl.halt_reason = ""
    return {"cleared": True}


@router.post("/risk-config")
def patch_risk_config(
    patch: RiskConfigPatch,
    _: dict = Depends(auth.require_bearer),
) -> dict:
    ctx = _agent_context()
    cfg = ctx.risk_engine.config
    changes: dict = {}
    for field in ("trading_capital", "max_loss_per_day", "max_open_positions", "kelly_fraction"):
        value = getattr(patch, field)
        if value is not None:
            setattr(cfg, field, value)
            changes[field] = value
    return {"applied": changes}


@router.get("/health")
def health() -> dict:
    return {"ok": True, "time": datetime.now().isoformat()}
