"""Strategy lab runner.

Responsibilities:
  - Load historical candles
  - Backtest each registered strategy
  - Compute metrics
  - Persist results
  - Optionally request a Claude analysis
"""

from __future__ import annotations

from dataclasses import dataclass, field
from datetime import datetime, timezone
from pathlib import Path

from loguru import logger

from ..backtest.data_loader import load_ohlc_csv
from ..backtest.engine import Backtester
from ..backtest.metrics import Metrics, compute
from ..domain.signals import Candle
from ..domain.strategies import build_all
from ..domain.trades import ClosedTrade


@dataclass(slots=True)
class LabResult:
    run_id: str
    started_at: datetime
    finished_at: datetime
    candle_count: int
    strategy_metrics: list[Metrics] = field(default_factory=list)
    trades: dict[str, list[ClosedTrade]] = field(default_factory=dict)

    def ranked(self) -> list[Metrics]:
        return sorted(
            self.strategy_metrics,
            key=lambda m: (m.profit_factor, m.net_pnl),
            reverse=True,
        )

    def to_dict(self) -> dict:
        return {
            "run_id": self.run_id,
            "started_at": self.started_at.isoformat(),
            "finished_at": self.finished_at.isoformat(),
            "candle_count": self.candle_count,
            "ranking": [m.to_dict() for m in self.ranked()],
        }


def run_lab(
    candles: list[Candle] | None = None,
    csv_path: str | Path | None = None,
    instrument: str | None = None,
) -> LabResult:
    if candles is None:
        if csv_path is None:
            raise ValueError("Either candles or csv_path must be provided")
        candles = load_ohlc_csv(csv_path, instrument=instrument)

    run_id = datetime.now(timezone.utc).strftime("lab_%Y%m%d_%H%M%S")
    started = datetime.now(timezone.utc)
    logger.info(f"Lab {run_id}: {len(candles)} candles, {len(build_all())} strategies")

    engine = Backtester()
    metrics_list: list[Metrics] = []
    trades_map: dict[str, list[ClosedTrade]] = {}

    for strategy in build_all():
        result = engine.run(strategy, candles)
        m = compute(result.trades, strategy=strategy.name)
        metrics_list.append(m)
        trades_map[strategy.name] = result.trades
        logger.info(
            f"  {strategy.name}: {m.total_trades} trades · "
            f"PnL ₹{m.net_pnl} · PF {m.profit_factor} · WR {m.win_rate}"
        )

    return LabResult(
        run_id=run_id,
        started_at=started,
        finished_at=datetime.now(timezone.utc),
        candle_count=len(candles),
        strategy_metrics=metrics_list,
        trades=trades_map,
    )
