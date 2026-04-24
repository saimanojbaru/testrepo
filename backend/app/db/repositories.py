"""Repository helpers — insert engine output, query for API responses."""

from __future__ import annotations

from datetime import datetime, timedelta, timezone

from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from ..backtest.metrics import Metrics
from ..domain.signals import Signal
from ..domain.trades import ClosedTrade
from .models import SignalRow, StrategyResultRow, TradeRow


async def insert_signal(sess: AsyncSession, sig: Signal) -> None:
    sess.add(
        SignalRow(
            created_at=sig.timestamp,
            instrument=sig.instrument,
            strategy=sig.strategy,
            action=sig.action.value,
            entry_price=sig.entry_price,
            stop_loss=sig.stop_loss,
            target=sig.target,
            confidence=sig.confidence,
            notes=sig.notes or None,
        )
    )


async def insert_trade(sess: AsyncSession, trade: ClosedTrade) -> None:
    sess.add(
        TradeRow(
            instrument=trade.instrument,
            strategy=trade.strategy,
            side=trade.side.value,
            entry=trade.entry,
            exit_price=trade.exit_price,
            qty=trade.qty,
            gross_pnl=trade.gross_pnl,
            costs=trade.costs,
            net_pnl=trade.net_pnl,
            reason=trade.reason.value,
            opened_at=trade.opened_at,
            closed_at=trade.closed_at,
        )
    )


async def insert_lab_result(
    sess: AsyncSession,
    run_id: str,
    started_at: datetime,
    finished_at: datetime,
    metrics: list[Metrics],
) -> None:
    for m in metrics:
        sess.add(
            StrategyResultRow(
                run_id=run_id,
                strategy=m.strategy,
                total_trades=m.total_trades,
                win_rate=m.win_rate,
                net_pnl=m.net_pnl,
                gross_pnl=m.gross_pnl,
                costs=m.costs,
                max_drawdown=m.max_drawdown,
                profit_factor=m.profit_factor,
                avg_profit=m.avg_profit,
                avg_loss=m.avg_loss,
                expectancy=m.expectancy,
                started_at=started_at,
                finished_at=finished_at,
            )
        )


async def recent_signals(
    sess: AsyncSession, limit: int = 50
) -> list[SignalRow]:
    stmt = select(SignalRow).order_by(SignalRow.created_at.desc()).limit(limit)
    return list((await sess.execute(stmt)).scalars())


async def lab_results(
    sess: AsyncSession, run_id: str | None = None
) -> list[StrategyResultRow]:
    stmt = select(StrategyResultRow).order_by(
        StrategyResultRow.finished_at.desc(), StrategyResultRow.net_pnl.desc()
    )
    if run_id:
        stmt = stmt.where(StrategyResultRow.run_id == run_id)
    return list((await sess.execute(stmt)).scalars())


async def performance_since(
    sess: AsyncSession, hours: int = 24
) -> list[TradeRow]:
    cutoff = datetime.now(timezone.utc) - timedelta(hours=hours)
    stmt = (
        select(TradeRow)
        .where(TradeRow.closed_at >= cutoff)
        .order_by(TradeRow.closed_at.desc())
    )
    return list((await sess.execute(stmt)).scalars())
