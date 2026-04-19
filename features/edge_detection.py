"""Filter engineered features / candidate signals against a minimum cost-net edge.

Used during strategy discovery to prune feature combinations that don't clear
trading costs.
"""
from __future__ import annotations

from dataclasses import dataclass

import numpy as np
import pandas as pd

from costs import Trade, TradeSide, compute_costs

# Default: require net Sharpe > 1.0 after costs before a feature combo is worth exploring.
MIN_EDGE_SHARPE = 1.0


@dataclass(frozen=True)
class EdgeProbe:
    trades: int
    gross_pnl: float
    net_pnl: float
    sharpe_net: float
    passes: bool


def probe_edge(
    signal: pd.Series,
    returns: pd.Series,
    notional_per_trade: float,
    min_sharpe: float = MIN_EDGE_SHARPE,
) -> EdgeProbe:
    """Quick cost-aware evaluation: does this signal's naive returns beat costs?

    `signal`: -1, 0, or +1 per bar.
    `returns`: next-bar log return of the underlying/premium.
    `notional_per_trade`: premium * qty used to scale per-leg costs.
    """
    mask = signal != 0
    trade_count = int(mask.sum())
    if trade_count == 0:
        return EdgeProbe(0, 0.0, 0.0, 0.0, False)

    gross = (signal.shift(0) * returns).fillna(0.0)
    gross_sum = float(gross.sum() * notional_per_trade)

    # Approximate round-trip cost as 2 legs on the same notional (buy then sell)
    entry = Trade(side=TradeSide.BUY, qty=1, premium=notional_per_trade)
    exit = Trade(side=TradeSide.SELL, qty=1, premium=notional_per_trade)
    rt_cost = compute_costs(entry).total + compute_costs(exit).total
    cost_total = rt_cost * trade_count
    net = gross_sum - cost_total

    per_trade = gross * notional_per_trade
    std = float(per_trade[mask].std() or 0.0)
    sharpe_gross = float(per_trade[mask].mean() / std * np.sqrt(252)) if std > 0 else 0.0
    net_per_trade = per_trade[mask].sum() / trade_count - rt_cost
    sharpe_net = sharpe_gross * (net_per_trade / (per_trade[mask].mean() or 1.0)) if per_trade[mask].mean() else 0.0

    passes = sharpe_net >= min_sharpe and net > 0
    return EdgeProbe(
        trades=trade_count,
        gross_pnl=round(gross_sum, 2),
        net_pnl=round(net, 2),
        sharpe_net=round(sharpe_net, 3),
        passes=passes,
    )
