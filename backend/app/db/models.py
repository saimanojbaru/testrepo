"""SQLAlchemy 2.x ORM models.

Tables:
  signals             — every signal emitted by the engine
  trades              — paper-trade lifecycle (open + close merged)
  strategy_results    — a lab run's per-strategy metrics
  users               — placeholder for mobile-app auth
"""

from __future__ import annotations

from datetime import datetime, timezone
from typing import Any

from sqlalchemy import JSON, DateTime, Float, ForeignKey, Integer, String
from sqlalchemy.orm import DeclarativeBase, Mapped, mapped_column, relationship


class Base(DeclarativeBase):
    pass


def _now() -> datetime:
    return datetime.now(timezone.utc)


class SignalRow(Base):
    __tablename__ = "signals"

    id: Mapped[int] = mapped_column(primary_key=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)
    instrument: Mapped[str] = mapped_column(String(64))
    strategy: Mapped[str] = mapped_column(String(64))
    action: Mapped[str] = mapped_column(String(8))
    entry_price: Mapped[float] = mapped_column(Float)
    stop_loss: Mapped[float] = mapped_column(Float)
    target: Mapped[float] = mapped_column(Float)
    confidence: Mapped[float] = mapped_column(Float)
    notes: Mapped[dict[str, Any] | None] = mapped_column(JSON, nullable=True)


class TradeRow(Base):
    __tablename__ = "trades"

    id: Mapped[int] = mapped_column(primary_key=True)
    instrument: Mapped[str] = mapped_column(String(64))
    strategy: Mapped[str] = mapped_column(String(64))
    side: Mapped[str] = mapped_column(String(8))
    entry: Mapped[float] = mapped_column(Float)
    exit_price: Mapped[float] = mapped_column(Float)
    qty: Mapped[int] = mapped_column(Integer, default=1)
    gross_pnl: Mapped[float] = mapped_column(Float)
    costs: Mapped[float] = mapped_column(Float)
    net_pnl: Mapped[float] = mapped_column(Float)
    reason: Mapped[str] = mapped_column(String(16))
    opened_at: Mapped[datetime] = mapped_column(DateTime)
    closed_at: Mapped[datetime] = mapped_column(DateTime)


class StrategyResultRow(Base):
    __tablename__ = "strategy_results"

    id: Mapped[int] = mapped_column(primary_key=True)
    run_id: Mapped[str] = mapped_column(String(64), index=True)
    strategy: Mapped[str] = mapped_column(String(64))
    total_trades: Mapped[int] = mapped_column(Integer)
    win_rate: Mapped[float] = mapped_column(Float)
    net_pnl: Mapped[float] = mapped_column(Float)
    gross_pnl: Mapped[float] = mapped_column(Float)
    costs: Mapped[float] = mapped_column(Float)
    max_drawdown: Mapped[float] = mapped_column(Float)
    profit_factor: Mapped[float] = mapped_column(Float)
    avg_profit: Mapped[float] = mapped_column(Float)
    avg_loss: Mapped[float] = mapped_column(Float)
    expectancy: Mapped[float] = mapped_column(Float)
    started_at: Mapped[datetime] = mapped_column(DateTime)
    finished_at: Mapped[datetime] = mapped_column(DateTime)


class UserRow(Base):
    __tablename__ = "users"

    id: Mapped[int] = mapped_column(primary_key=True)
    username: Mapped[str] = mapped_column(String(64), unique=True)
    api_token: Mapped[str] = mapped_column(String(128), unique=True)
    created_at: Mapped[datetime] = mapped_column(DateTime, default=_now)
