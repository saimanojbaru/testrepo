"""Reconciliation + audit-trail endpoints."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone

from fastapi import APIRouter, Depends, HTTPException, UploadFile
from fastapi.responses import PlainTextResponse
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from ..audit.audit_log import AuditLogRow, export_csv
from ..audit.reconciliation import (
    LedgerRow,
    parse_contract_note,
    reconcile,
)
from ..costs.india_fno import compute_costs, round_trip_cost
from ..db.models import TradeRow
from ..reporting.claude_analyst import ClaudeAnalyst
from ..reporting.personas import PersonaKey, get_persona, list_personas
from .deps import get_session

router = APIRouter()


class CostRequest(BaseModel):
    side: str
    premium: float
    qty: int
    exchange: str = "NSE"


@router.post("/costs/single")
async def costs_single(req: CostRequest) -> dict:
    return compute_costs(
        side=req.side, premium=req.premium, qty=req.qty, exchange=req.exchange
    ).to_dict()


class RoundTripRequest(BaseModel):
    buy_premium: float
    sell_premium: float
    qty: int
    exchange: str = "NSE"


@router.post("/costs/round-trip")
async def costs_round_trip(req: RoundTripRequest) -> dict:
    return round_trip_cost(
        buy_premium=req.buy_premium,
        sell_premium=req.sell_premium,
        qty=req.qty,
        exchange=req.exchange,
    ).to_dict()


@router.post("/reconciliation/upload")
async def reconciliation_upload(
    file: UploadFile,
    sess: AsyncSession = Depends(get_session),
) -> dict:
    raw = (await file.read()).decode("utf-8", errors="replace")
    note_rows = parse_contract_note(raw)
    if not note_rows:
        raise HTTPException(400, "Could not parse any rows from the CSV")

    stmt = select(TradeRow).order_by(TradeRow.closed_at.asc())
    db_rows = list((await sess.execute(stmt)).scalars())
    ledger = [
        LedgerRow(
            trade_id=str(r.id),
            instrument=r.instrument,
            side="BUY" if r.side == "LONG" else "SELL",
            qty=r.qty,
            price=r.exit_price,  # we compare exit fills; could expand to per-leg later
            charges=r.costs,
            ts=r.closed_at,
        )
        for r in db_rows
    ]

    report = reconcile(note_rows, ledger)
    return report.to_dict()


@router.get("/audit-trail/export.csv", response_class=PlainTextResponse)
async def export_audit_csv(
    hours: int = 168,
    sess: AsyncSession = Depends(get_session),
) -> str:
    since = datetime.now(timezone.utc) - timedelta(hours=hours)
    return await export_csv(sess, since=since)


@router.get("/audit-trail")
async def list_audit(
    limit: int = 100,
    sess: AsyncSession = Depends(get_session),
) -> dict:
    stmt = select(AuditLogRow).order_by(AuditLogRow.ts.desc()).limit(limit)
    rows = list((await sess.execute(stmt)).scalars())
    return {
        "events": [
            {
                "id": r.id,
                "ts": r.ts.isoformat(),
                "actor": r.actor,
                "event": r.event,
                "instrument": r.instrument,
                "payload": r.payload,
            }
            for r in rows
        ]
    }


@router.get("/personas")
async def personas_index() -> dict:
    return {
        "personas": [
            {
                "key": p.key.value,
                "name": p.name,
                "one_liner": p.one_liner,
            }
            for p in list_personas()
        ]
    }


class PersonaRequest(BaseModel):
    persona: PersonaKey
    context: str  # arbitrary user-supplied analysis context (markdown)


@router.post("/personas/run")
async def run_persona(req: PersonaRequest) -> dict:
    persona = get_persona(req.persona)
    analyst = ClaudeAnalyst()
    report = analyst.analyze_freeform(
        system_prompt=persona.system_prompt,
        user_prompt=req.context,
    )
    return {
        "persona": persona.key.value,
        "name": persona.name,
        "stub": report.stub,
        "model": report.model,
        "markdown": report.markdown,
    }
