"""Hourly / daily scheduled reports."""

from __future__ import annotations

import asyncio
from datetime import datetime, timezone

from apscheduler.schedulers.asyncio import AsyncIOScheduler
from apscheduler.triggers.cron import CronTrigger
from loguru import logger

from .backtest.metrics import compute
from .engine.signal_engine import SignalEngine
from .reporting.claude_analyst import ClaudeAnalyst
from .reporting.telegram_bot import TelegramReporter


def build_scheduler(engine: SignalEngine) -> AsyncIOScheduler:
    sched = AsyncIOScheduler(timezone="Asia/Kolkata")
    reporter = TelegramReporter()
    analyst = ClaudeAnalyst()

    async def hourly() -> None:
        trades = engine.trader.closed_trades
        if not trades:
            logger.info("hourly: no trades yet")
            return
        by_strategy: dict[str, list] = {}
        for t in trades:
            by_strategy.setdefault(t.strategy, []).append(t)
        metrics = [compute(v, strategy=k) for k, v in by_strategy.items()]
        await reporter.hourly(metrics)

    async def daily() -> None:
        trades = engine.trader.closed_trades
        by_strategy: dict[str, list] = {}
        for t in trades:
            by_strategy.setdefault(t.strategy, []).append(t)
        metrics = [compute(v, strategy=k) for k, v in by_strategy.items()]
        # Fake a LabResult shape for the reporter.
        from .lab.runner import LabResult

        result = LabResult(
            run_id=datetime.now(timezone.utc).strftime("live_%Y%m%d"),
            started_at=datetime.now(timezone.utc),
            finished_at=datetime.now(timezone.utc),
            candle_count=0,
            strategy_metrics=metrics,
            trades=by_strategy,
        )
        report = analyst.analyze(metrics, by_strategy)
        await reporter.daily(result, claude_analysis=report.markdown)

    sched.add_job(hourly, CronTrigger(minute=5), id="hourly")
    sched.add_job(daily, CronTrigger(hour=16, minute=0), id="daily")
    return sched


async def _noop() -> None:
    await asyncio.sleep(0)
