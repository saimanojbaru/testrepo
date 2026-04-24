"""Strategy Lab API routes."""

from __future__ import annotations

from pathlib import Path

from fastapi import APIRouter, BackgroundTasks, Depends, HTTPException
from pydantic import BaseModel, Field
from sqlalchemy.ext.asyncio import AsyncSession

from ..db.repositories import insert_lab_result, lab_results
from ..lab.runner import run_lab
from ..reporting.claude_analyst import ClaudeAnalyst
from .deps import get_session

router = APIRouter()


class LabRunRequest(BaseModel):
    csv_path: str = Field(..., description="Path to OHLC CSV (1-min candles)")
    instrument: str | None = None
    analyze: bool = True


class LabRunResponse(BaseModel):
    run_id: str
    strategies: list[dict]
    claude_stub: bool | None = None
    claude_markdown: str | None = None


@router.post("/strategy-lab/run", response_model=LabRunResponse)
async def strategy_lab_run(
    req: LabRunRequest,
    background: BackgroundTasks,
    sess: AsyncSession = Depends(get_session),
) -> LabRunResponse:
    if not Path(req.csv_path).exists():
        raise HTTPException(404, f"CSV not found: {req.csv_path}")

    result = run_lab(csv_path=req.csv_path, instrument=req.instrument)
    await insert_lab_result(
        sess,
        run_id=result.run_id,
        started_at=result.started_at,
        finished_at=result.finished_at,
        metrics=result.strategy_metrics,
    )
    report = None
    if req.analyze:
        analyst = ClaudeAnalyst()
        report = analyst.analyze(result.strategy_metrics, result.trades)

    return LabRunResponse(
        run_id=result.run_id,
        strategies=[m.to_dict() for m in result.ranked()],
        claude_stub=report.stub if report else None,
        claude_markdown=report.markdown if report else None,
    )


@router.get("/strategy-lab/results")
async def strategy_lab_results(
    run_id: str | None = None, sess: AsyncSession = Depends(get_session)
) -> dict:
    rows = await lab_results(sess, run_id=run_id)
    return {
        "results": [
            {
                "run_id": r.run_id,
                "strategy": r.strategy,
                "total_trades": r.total_trades,
                "win_rate": r.win_rate,
                "net_pnl": r.net_pnl,
                "profit_factor": r.profit_factor,
                "max_drawdown": r.max_drawdown,
                "expectancy": r.expectancy,
                "finished_at": r.finished_at.isoformat(),
            }
            for r in rows
        ]
    }
