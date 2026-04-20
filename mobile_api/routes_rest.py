"""REST endpoints: /login, /state, /kill-switch, /risk-config."""
from __future__ import annotations

import datetime as dt
from dataclasses import dataclass

from fastapi import APIRouter, Depends, HTTPException, Header

from broker.base import Broker
from mobile_api.auth import issue_token, verify_shared_secret, verify_token
from mobile_api.schemas import (
    KillSwitchRequest,
    LoginRequest,
    LoginResponse,
    PositionView,
    RiskConfigPatch,
    StateSnapshot,
)
from mobile_api.state_bus import StateBus
from risk.engine import RiskEngine


@dataclass
class ApiDeps:
    broker: Broker
    risk: RiskEngine
    bus: StateBus
    api_secret: str
    api_secret_hash: str


def build_router(deps: ApiDeps) -> APIRouter:
    router = APIRouter()

    def require_auth(authorization: str = Header(default="")) -> None:
        if not authorization.startswith("Bearer "):
            raise HTTPException(status_code=401, detail="missing bearer")
        token = authorization.removeprefix("Bearer ").strip()
        if not verify_token(token, deps.api_secret):
            raise HTTPException(status_code=401, detail="bad token")

    @router.post("/login", response_model=LoginResponse)
    def login(req: LoginRequest) -> LoginResponse:
        if not verify_shared_secret(req.secret, deps.api_secret_hash):
            raise HTTPException(status_code=401, detail="bad secret")
        token, exp = issue_token(deps.api_secret)
        return LoginResponse(token=token, expires_at=exp)

    @router.get("/state", response_model=StateSnapshot, dependencies=[Depends(require_auth)])
    def state() -> StateSnapshot:
        positions = deps.broker.positions()
        unrealized = sum(p.unrealized_pnl for p in positions)
        return StateSnapshot(
            capital=deps.risk.capital,
            daily_pnl=deps.risk.daily_pnl,
            weekly_pnl=deps.risk.weekly_pnl,
            realized_pnl=deps.broker.pnl(),
            unrealized_pnl=unrealized,
            open_positions=len([p for p in positions if p.quantity != 0]),
            trades_today=deps.risk.trades_today,
            kill_switch_engaged=deps.risk.kill_switch.engaged(),
            regime=deps.bus.snapshot().get("regime"),
            positions=[
                PositionView(
                    instrument_key=p.instrument_key,
                    quantity=p.quantity,
                    avg_price=p.avg_price,
                    last_price=p.last_price,
                    unrealized_pnl=p.unrealized_pnl,
                    realized_pnl=p.realized_pnl,
                )
                for p in positions
            ],
        )

    @router.post("/kill-switch", dependencies=[Depends(require_auth)])
    def kill_switch(req: KillSwitchRequest) -> dict:
        if req.engage:
            deps.risk.kill_switch.engage(req.reason)
            deps.broker.square_off_all()
            deps.bus.publish("kill_switch", {"engaged": True, "reason": req.reason})
        else:
            deps.risk.kill_switch.disengage()
            deps.bus.publish("kill_switch", {"engaged": False})
        return {"engaged": deps.risk.kill_switch.engaged()}

    @router.post("/risk-config", dependencies=[Depends(require_auth)])
    def risk_config(patch: RiskConfigPatch) -> dict:
        # Rebuild an immutable RiskLimits with the patched values
        from risk.limits import RiskLimits
        cur = deps.risk.limits
        new = RiskLimits(
            max_open_positions=patch.max_open_positions or cur.max_open_positions,
            max_daily_loss=patch.max_daily_loss if patch.max_daily_loss is not None else cur.max_daily_loss,
            max_weekly_loss=cur.max_weekly_loss,
            max_single_trade_loss=cur.max_single_trade_loss,
            max_lots_per_trade=cur.max_lots_per_trade,
            kelly_max_fraction=(
                patch.kelly_max_fraction if patch.kelly_max_fraction is not None else cur.kelly_max_fraction
            ),
            kelly_safety=cur.kelly_safety,
            max_trades_per_day=cur.max_trades_per_day,
            instrument_blacklist=cur.instrument_blacklist,
        )
        deps.risk.limits = new
        return new.__dict__

    return router
