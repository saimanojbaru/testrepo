"""Pydantic schemas shared between REST + WS endpoints and the Flutter app."""
from __future__ import annotations

import datetime as dt
from typing import Literal

from pydantic import BaseModel, Field


class LoginRequest(BaseModel):
    secret: str


class LoginResponse(BaseModel):
    token: str
    expires_at: dt.datetime


class PositionView(BaseModel):
    instrument_key: str
    quantity: int
    avg_price: float
    last_price: float
    unrealized_pnl: float
    realized_pnl: float = 0.0


class StateSnapshot(BaseModel):
    capital: float
    daily_pnl: float
    weekly_pnl: float
    realized_pnl: float
    unrealized_pnl: float
    open_positions: int
    trades_today: int
    kill_switch_engaged: bool
    regime: str | None = None
    positions: list[PositionView] = Field(default_factory=list)


class KillSwitchRequest(BaseModel):
    engage: bool
    reason: str = "manual"


class RiskConfigPatch(BaseModel):
    max_daily_loss: float | None = None
    max_open_positions: int | None = None
    kelly_max_fraction: float | None = None


class TradeEvent(BaseModel):
    kind: Literal["fill", "close", "signal", "risk_violation", "regime_shift", "kill_switch", "bar"]
    ts: dt.datetime
    payload: dict


class WsMessage(BaseModel):
    type: Literal["snapshot", "delta", "heartbeat"]
    data: dict
