"""Performance metrics computed from a list of ClosedTrade objects."""

from __future__ import annotations

from dataclasses import asdict, dataclass
from typing import Iterable

from ..domain.trades import ClosedTrade


@dataclass(slots=True)
class Metrics:
    strategy: str
    total_trades: int
    win_rate: float
    net_pnl: float
    gross_pnl: float
    costs: float
    max_drawdown: float
    profit_factor: float
    avg_profit: float
    avg_loss: float
    expectancy: float

    def to_dict(self) -> dict:
        return asdict(self)


def compute(trades: Iterable[ClosedTrade], strategy: str = "all") -> Metrics:
    trades = list(trades)
    if not trades:
        return Metrics(
            strategy=strategy,
            total_trades=0,
            win_rate=0.0,
            net_pnl=0.0,
            gross_pnl=0.0,
            costs=0.0,
            max_drawdown=0.0,
            profit_factor=0.0,
            avg_profit=0.0,
            avg_loss=0.0,
            expectancy=0.0,
        )

    wins = [t for t in trades if t.net_pnl > 0]
    losses = [t for t in trades if t.net_pnl <= 0]
    gross = sum(t.gross_pnl for t in trades)
    costs = sum(t.costs for t in trades)
    net = gross - costs

    profit_sum = sum(t.net_pnl for t in wins)
    loss_sum = abs(sum(t.net_pnl for t in losses))
    profit_factor = profit_sum / loss_sum if loss_sum > 0 else float("inf") if profit_sum > 0 else 0.0

    # Max drawdown on the equity curve
    equity = 0.0
    peak = 0.0
    max_dd = 0.0
    for t in trades:
        equity += t.net_pnl
        peak = max(peak, equity)
        drawdown = peak - equity
        max_dd = max(max_dd, drawdown)

    avg_profit = profit_sum / len(wins) if wins else 0.0
    avg_loss = -loss_sum / len(losses) if losses else 0.0
    win_rate = len(wins) / len(trades)
    expectancy = win_rate * avg_profit + (1 - win_rate) * avg_loss

    return Metrics(
        strategy=strategy,
        total_trades=len(trades),
        win_rate=round(win_rate, 4),
        net_pnl=round(net, 2),
        gross_pnl=round(gross, 2),
        costs=round(costs, 2),
        max_drawdown=round(max_dd, 2),
        profit_factor=round(profit_factor, 3) if profit_factor != float("inf") else 9999.0,
        avg_profit=round(avg_profit, 2),
        avg_loss=round(avg_loss, 2),
        expectancy=round(expectancy, 2),
    )
