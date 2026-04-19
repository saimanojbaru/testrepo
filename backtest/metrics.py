"""Performance metrics: Sharpe_net, Sortino, max DD, win rate, expectancy, profit factor, Calmar."""
from __future__ import annotations

from dataclasses import dataclass, asdict

import numpy as np
import pandas as pd

ANNUALIZATION = 252  # trading days


@dataclass(frozen=True)
class Metrics:
    trades: int
    gross_pnl: float
    net_pnl: float
    win_rate: float
    expectancy: float
    profit_factor: float
    sharpe_net: float
    sortino: float
    max_drawdown: float
    calmar: float

    def as_dict(self) -> dict:
        return asdict(self)


def _sharpe(pnl_series: pd.Series) -> float:
    if pnl_series.empty:
        return 0.0
    std = float(pnl_series.std())
    if std == 0.0:
        return 0.0
    return float(pnl_series.mean() / std * np.sqrt(ANNUALIZATION))


def _sortino(pnl_series: pd.Series) -> float:
    if pnl_series.empty:
        return 0.0
    downside = pnl_series[pnl_series < 0]
    dstd = float(downside.std()) if not downside.empty else 0.0
    if dstd == 0.0:
        return 0.0
    return float(pnl_series.mean() / dstd * np.sqrt(ANNUALIZATION))


def _max_drawdown(equity: pd.Series) -> float:
    if equity.empty:
        return 0.0
    running_max = equity.cummax()
    dd = (equity - running_max)
    return float(dd.min())


def compute_metrics(trade_pnls: pd.Series) -> Metrics:
    """Accept a per-trade net-PnL series (INR). Returns Metrics."""
    n = int(len(trade_pnls))
    if n == 0:
        return Metrics(0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0, 0.0)

    gross = float(trade_pnls.sum())  # assumes trade_pnls already net
    wins = trade_pnls[trade_pnls > 0]
    losses = trade_pnls[trade_pnls < 0]
    win_rate = len(wins) / n
    expectancy = float(trade_pnls.mean())
    profit_factor = float(wins.sum() / abs(losses.sum())) if not losses.empty else float("inf")

    equity = trade_pnls.cumsum()
    mdd = _max_drawdown(equity)
    annual_return = gross * (ANNUALIZATION / max(n, 1))
    calmar = annual_return / abs(mdd) if mdd < 0 else float("inf")

    return Metrics(
        trades=n,
        gross_pnl=round(gross, 2),
        net_pnl=round(gross, 2),
        win_rate=round(win_rate, 4),
        expectancy=round(expectancy, 2),
        profit_factor=round(profit_factor, 3) if profit_factor != float("inf") else float("inf"),
        sharpe_net=round(_sharpe(trade_pnls), 3),
        sortino=round(_sortino(trade_pnls), 3),
        max_drawdown=round(mdd, 2),
        calmar=round(calmar, 3) if calmar != float("inf") else float("inf"),
    )
