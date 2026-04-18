"""Pydantic models shared between REST and WebSocket layers."""

from datetime import datetime
from typing import List, Literal, Optional

from pydantic import BaseModel, Field


class LoginRequest(BaseModel):
    device_id: str
    shared_secret: str


class LoginResponse(BaseModel):
    token: str
    expires_at: datetime


class RiskStatus(BaseModel):
    daily_pnl: float
    trades_today: int
    open_positions: int
    halted: bool
    halt_reason: str
    kill_switch_active: bool
    daily_loss_cap: float
    trading_capital: float


class PositionDto(BaseModel):
    instrument_key: str
    symbol: str
    quantity: int
    average_price: float
    last_price: float
    unrealized_pnl: float


class TradeEvent(BaseModel):
    timestamp: datetime
    kind: Literal["signal", "fill", "exit", "regime", "risk", "kill_switch", "heartbeat"]
    message: str
    symbol: Optional[str] = None
    side: Optional[str] = None
    quantity: Optional[int] = None
    price: Optional[float] = None
    pnl: Optional[float] = None
    regime: Optional[int] = None
    strategy: Optional[str] = None


class StateSnapshot(BaseModel):
    server_time: datetime
    paper_mode: bool
    symbol: str
    risk: RiskStatus
    positions: List[PositionDto]
    recent_events: List[TradeEvent]
    pnl_curve: List[float] = Field(default_factory=list)


class RiskConfigPatch(BaseModel):
    trading_capital: Optional[float] = None
    max_loss_per_day: Optional[float] = None
    max_open_positions: Optional[int] = None
    kelly_fraction: Optional[float] = None


class KillSwitchRequest(BaseModel):
    reason: str = "Manual trigger from mobile"


class KillSwitchResponse(BaseModel):
    active: bool
    reason: str
    squared_off: bool


class WsEnvelope(BaseModel):
    """All WebSocket messages are wrapped in this envelope."""
    type: Literal["snapshot", "event", "pnl", "risk", "heartbeat"]
    payload: dict
