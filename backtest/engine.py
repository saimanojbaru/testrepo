"""Event-driven backtester — deterministic, cost-aware, single-instrument-at-a-time.

Loops over bars, calls strategy.on_bar(), opens/closes positions with a next-bar
fill model. Costs from costs/zerodha_upstox.py are charged on every leg.
"""
from __future__ import annotations

from dataclasses import dataclass, field
from typing import TYPE_CHECKING

import pandas as pd

from backtest.metrics import Metrics, compute_metrics
from backtest.slippage import SlippageConfig, estimate_slippage
from costs import CostConfig, Trade, TradeSide, compute_costs
from strategies.base import SignalDirection, Strategy

if TYPE_CHECKING:
    pass


@dataclass(frozen=True)
class BacktestConfig:
    qty_per_trade: int = 50          # 1 lot of Nifty options = 50
    assume_spread_pct: float = 0.004 # 0.4% default spread when fixture lacks quotes
    avg_daily_volume: int = 1_000_000
    stop_loss_pct: float | None = None
    take_profit_pct: float | None = None
    max_bars_in_trade: int = 20      # auto-exit after N bars to cap exposure
    cost_cfg: CostConfig = field(default_factory=CostConfig)
    slip_cfg: SlippageConfig = field(default_factory=SlippageConfig)


@dataclass
class _OpenPosition:
    direction: SignalDirection
    entry_ts: pd.Timestamp
    entry_price: float
    entry_cost: float
    qty: int
    bars_held: int = 0
    stop_loss_pct: float | None = None
    take_profit_pct: float | None = None


@dataclass
class TradeRecord:
    entry_ts: pd.Timestamp
    exit_ts: pd.Timestamp
    direction: str
    entry_price: float
    exit_price: float
    qty: int
    gross_pnl: float
    costs: float
    net_pnl: float
    exit_reason: str


def _fill_price(bar: pd.Series, direction: SignalDirection, cfg: BacktestConfig, sign: int) -> float:
    """Next-bar open fill plus slippage. `sign` = +1 on entry, -1 on exit."""
    px = float(bar["open"])
    spread = px * cfg.assume_spread_pct
    vol = abs(float(bar.get("close", px)) - px) / max(px, 1e-9)
    slip = estimate_slippage(
        premium=px,
        spread=spread,
        vol=vol,
        size=cfg.qty_per_trade,
        avg_daily_volume=cfg.avg_daily_volume,
        cfg=cfg.slip_cfg,
    )
    if direction is SignalDirection.LONG:
        return px + slip * sign
    return px - slip * sign


def run(
    df: pd.DataFrame,
    strategy: Strategy,
    cfg: BacktestConfig = BacktestConfig(),
) -> tuple[pd.DataFrame, Metrics]:
    """Run a backtest. Returns (trade_records, metrics)."""
    prepared = strategy.prepare(df).reset_index(drop=True)
    if "ts" not in prepared.columns or "open" not in prepared.columns:
        raise ValueError("dataframe must have ts + OHLC columns")

    position: _OpenPosition | None = None
    records: list[TradeRecord] = []

    for i in range(len(prepared) - 1):  # last bar cannot open (no next-bar fill)
        bar = prepared.iloc[i]
        next_bar = prepared.iloc[i + 1]

        if position is None:
            sig = strategy.on_bar(bar, prepared.iloc[: i + 1])
            if sig is None or sig.direction is SignalDirection.FLAT:
                continue
            entry_px = _fill_price(next_bar, sig.direction, cfg, sign=+1)
            entry_leg = Trade(
                side=TradeSide.BUY if sig.direction is SignalDirection.LONG else TradeSide.SELL,
                qty=cfg.qty_per_trade,
                premium=entry_px,
            )
            entry_cost = compute_costs(entry_leg, cfg.cost_cfg).total
            position = _OpenPosition(
                direction=sig.direction,
                entry_ts=next_bar["ts"],
                entry_price=entry_px,
                entry_cost=entry_cost,
                qty=cfg.qty_per_trade,
                stop_loss_pct=sig.stop_loss_pct or cfg.stop_loss_pct,
                take_profit_pct=sig.take_profit_pct or cfg.take_profit_pct,
            )
            continue

        # position open — check exits
        position.bars_held += 1
        exit_reason: str | None = None
        ref = float(bar["close"])
        ret = (ref - position.entry_price) / position.entry_price * (
            1 if position.direction is SignalDirection.LONG else -1
        )
        if position.stop_loss_pct is not None and ret <= -position.stop_loss_pct:
            exit_reason = "stop_loss"
        elif position.take_profit_pct is not None and ret >= position.take_profit_pct:
            exit_reason = "take_profit"
        elif position.bars_held >= cfg.max_bars_in_trade:
            exit_reason = "time_exit"
        elif i == len(prepared) - 2:
            exit_reason = "end_of_data"

        if exit_reason is None:
            continue

        exit_px = _fill_price(next_bar, position.direction, cfg, sign=-1)
        exit_leg = Trade(
            side=TradeSide.SELL if position.direction is SignalDirection.LONG else TradeSide.BUY,
            qty=position.qty,
            premium=exit_px,
        )
        exit_cost = compute_costs(exit_leg, cfg.cost_cfg).total
        direction_sign = 1 if position.direction is SignalDirection.LONG else -1
        gross = direction_sign * (exit_px - position.entry_price) * position.qty
        total_cost = position.entry_cost + exit_cost
        records.append(
            TradeRecord(
                entry_ts=position.entry_ts,
                exit_ts=next_bar["ts"],
                direction=position.direction.value,
                entry_price=round(position.entry_price, 2),
                exit_price=round(exit_px, 2),
                qty=position.qty,
                gross_pnl=round(gross, 2),
                costs=round(total_cost, 2),
                net_pnl=round(gross - total_cost, 2),
                exit_reason=exit_reason,
            )
        )
        position = None

    trades_df = pd.DataFrame([r.__dict__ for r in records])
    pnl_series = trades_df["net_pnl"] if not trades_df.empty else pd.Series(dtype=float)
    return trades_df, compute_metrics(pnl_series)
