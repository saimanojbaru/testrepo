"""Zerodha / Upstox fee schedule for Indian F&O options (post Oct-2024 rates).

All rates are per-leg unless noted. Amounts in INR, rounded at the very end.

Per-leg charges on an options order of `quantity` contracts (lots * lot_size)
at `premium` INR:
  * Brokerage      = min(flat_per_order, premium_turnover * pct_brokerage)
                     For Zerodha/Upstox intraday options: flat=20, pct=0.0003
                     -> effectively flat 20 for any premium * qty > ~66,667
  * Exchange txn   = premium_turnover * 0.0503% (NSE F&O options)
  * SEBI turnover  = premium_turnover * 0.0001%
  * Stamp duty     = premium_turnover * 0.003%   (BUY side only)
  * STT            = premium_turnover * 0.1%     (SELL side only, premium — not notional)
  * GST            = 18% * (brokerage + exchange + SEBI)

`net_pnl(open_trade, close_trade)` returns gross P&L minus costs on BOTH legs.
"""
from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

# Published schedules (Oct-2024 regime):
FLAT_BROKERAGE_PER_ORDER = 20.0
PCT_BROKERAGE = 0.0003  # 0.03%
PCT_EXCHANGE_TXN = 0.0503 / 100.0  # 0.0503% of premium turnover
PCT_SEBI = 0.0001 / 100.0  # 0.0001% of turnover
PCT_STAMP_DUTY = 0.003 / 100.0  # 0.003% on buy-side
PCT_STT = 0.1 / 100.0  # 0.1% on sell-side premium (Oct-2024)
GST_RATE = 0.18


class TradeSide(str, Enum):
    BUY = "BUY"
    SELL = "SELL"


class TradeType(str, Enum):
    OPTION = "OPTION"
    FUTURE = "FUTURE"


@dataclass(frozen=True)
class Trade:
    """A single executed leg."""
    side: TradeSide
    qty: int            # number of contracts (lots * lot_size), in units
    premium: float      # option premium per unit, INR
    trade_type: TradeType = TradeType.OPTION

    @property
    def turnover(self) -> float:
        return abs(self.premium) * self.qty


@dataclass(frozen=True)
class CostConfig:
    flat_brokerage: float = FLAT_BROKERAGE_PER_ORDER
    pct_brokerage: float = PCT_BROKERAGE
    pct_exchange_txn: float = PCT_EXCHANGE_TXN
    pct_sebi: float = PCT_SEBI
    pct_stamp_duty: float = PCT_STAMP_DUTY
    pct_stt: float = PCT_STT
    gst_rate: float = GST_RATE


@dataclass(frozen=True)
class CostBreakdown:
    brokerage: float
    exchange_txn: float
    sebi: float
    stamp_duty: float
    stt: float
    gst: float

    @property
    def total(self) -> float:
        return self.brokerage + self.exchange_txn + self.sebi + self.stamp_duty + self.stt + self.gst


def compute_costs(trade: Trade, cfg: CostConfig = CostConfig()) -> CostBreakdown:
    """Costs for a single executed leg. Options-specific today; futures deferred."""
    if trade.trade_type is not TradeType.OPTION:
        raise NotImplementedError("Only OPTION leg costs are modelled in MVP.")

    turnover = trade.turnover
    brokerage = min(cfg.flat_brokerage, turnover * cfg.pct_brokerage)
    exchange = turnover * cfg.pct_exchange_txn
    sebi = turnover * cfg.pct_sebi
    stamp = turnover * cfg.pct_stamp_duty if trade.side is TradeSide.BUY else 0.0
    stt = turnover * cfg.pct_stt if trade.side is TradeSide.SELL else 0.0
    gst = (brokerage + exchange + sebi) * cfg.gst_rate
    return CostBreakdown(
        brokerage=round(brokerage, 4),
        exchange_txn=round(exchange, 4),
        sebi=round(sebi, 6),
        stamp_duty=round(stamp, 4),
        stt=round(stt, 4),
        gst=round(gst, 4),
    )


def net_pnl(
    entry: Trade,
    exit: Trade,
    cfg: CostConfig = CostConfig(),
) -> tuple[float, CostBreakdown, CostBreakdown]:
    """Gross P&L minus costs on both legs. Returns (net_pnl, entry_costs, exit_costs).

    The caller is responsible for setting the correct side on each leg (BUY on entry
    for long positions, SELL on exit; reversed for shorts).
    """
    if entry.qty != exit.qty:
        raise ValueError("entry and exit quantities must match")
    direction = 1 if entry.side is TradeSide.BUY else -1
    gross = direction * (exit.premium - entry.premium) * entry.qty
    ec = compute_costs(entry, cfg)
    xc = compute_costs(exit, cfg)
    return round(gross - ec.total - xc.total, 2), ec, xc
