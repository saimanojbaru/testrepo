"""Append-only audit-trail event log.

Every signal emitted, fill placed, risk decision, and manual close lands in
this table with a UTC timestamp and structured payload. Records are NEVER
updated or deleted — the table is the legal record for after-the-fact CA
verification and SEBI Sep-2025 algo-trading audit requirements.
"""

from __future__ import annotations

import json
from datetime import datetime, timezone
from enum import Enum
from typing import Any

from sqlalchemy import JSON, DateTime, Integer, String
from sqlalchemy.ext.asyncio import AsyncSession
from sqlalchemy.orm import Mapped, mapped_column

from ..db.models import Base


class AuditEvent(str, Enum):
    SIGNAL_EMITTED = "SIGNAL_EMITTED"
    SIGNAL_REJECTED_BY_RISK = "SIGNAL_REJECTED_BY_RISK"
    TRADE_OPENED = "TRADE_OPENED"
    TRADE_CLOSED = "TRADE_CLOSED"
    KILL_SWITCH_ENGAGED = "KILL_SWITCH_ENGAGED"
    RECONCILIATION_RUN = "RECONCILIATION_RUN"
    BROKER_CONNECTED = "BROKER_CONNECTED"
    BROKER_DISCONNECTED = "BROKER_DISCONNECTED"


class AuditLogRow(Base):
    __tablename__ = "audit_log"

    id: Mapped[int] = mapped_column(Integer, primary_key=True)
    ts: Mapped[datetime] = mapped_column(
        DateTime, default=lambda: datetime.now(timezone.utc), index=True
    )
    actor: Mapped[str] = mapped_column(String(64))   # 'engine', 'user', 'risk', 'broker'
    event: Mapped[str] = mapped_column(String(48), index=True)
    instrument: Mapped[str | None] = mapped_column(String(64), nullable=True)
    payload: Mapped[dict[str, Any] | None] = mapped_column(JSON, nullable=True)


async def record(
    sess: AsyncSession,
    *,
    event: AuditEvent | str,
    actor: str = "engine",
    instrument: str | None = None,
    payload: dict | None = None,
) -> None:
    sess.add(
        AuditLogRow(
            ts=datetime.now(timezone.utc),
            actor=actor,
            event=event.value if isinstance(event, AuditEvent) else event,
            instrument=instrument,
            payload=payload,
        )
    )


async def export_csv(sess: AsyncSession, since: datetime | None = None) -> str:
    """Render the audit log as RFC 4180 CSV for archive/import elsewhere."""
    from sqlalchemy import select

    stmt = select(AuditLogRow).order_by(AuditLogRow.ts.asc())
    if since:
        stmt = stmt.where(AuditLogRow.ts >= since)
    rows = list((await sess.execute(stmt)).scalars())

    out = ["ts,actor,event,instrument,payload"]
    for r in rows:
        payload = json.dumps(r.payload, default=str) if r.payload else ""
        # CSV-escape any quotes in the JSON payload
        payload = '"' + payload.replace('"', '""') + '"' if payload else ""
        out.append(
            f"{r.ts.isoformat()},{r.actor},{r.event},"
            f"{r.instrument or ''},{payload}"
        )
    return "\n".join(out)
