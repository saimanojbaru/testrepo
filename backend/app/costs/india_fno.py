"""Per-trade cost calculator for Indian F&O — paisa-precision.

Source: Zerodha brokerage calculator + SEBI/NSE/BSE fee schedules.
All values are deterministic, comment-cited, and round to 2 decimals (paisa)
exactly the way a CA would expect on a contract note.
"""

from __future__ import annotations

from dataclasses import dataclass, asdict
from decimal import Decimal, ROUND_HALF_UP


def _paisa(x: Decimal | float) -> float:
    """Round to 2 decimal places using banker's-friendly half-up — same as a contract note."""
    return float(Decimal(str(x)).quantize(Decimal("0.01"), rounding=ROUND_HALF_UP))


@dataclass(slots=True, frozen=True)
class FeeSchedule:
    """All charges as fractions or per-crore unless labelled."""

    brokerage_per_order: float = 20.0       # Zerodha flat ₹20/order or 0.03% (whichever is lower)
    brokerage_pct_cap: float = 0.0003       # 0.03% cap for the per-order calc
    stt_sell_premium: float = 0.001         # STT 0.1% on sell-side option premium
    transaction_charge_nse: float = 0.0003503  # 0.03503% of premium turnover (NSE F&O)
    transaction_charge_bse: float = 0.000325   # 0.0325% of premium turnover (BSE F&O)
    sebi_charges_per_cr: float = 10.0       # ₹10 per crore of turnover
    stamp_duty_buy: float = 0.00003         # 0.003% on buy-side, capped per state — flat 0.003% used here
    gst_on_brokerage_and_charges: float = 0.18  # 18% on (brokerage + transaction + SEBI)


DEFAULT_SCHEDULE = FeeSchedule()


@dataclass(slots=True, frozen=True)
class CostBreakdown:
    brokerage: float
    stt: float
    transaction_charges: float
    sebi: float
    stamp_duty: float
    gst: float
    total: float

    def to_dict(self) -> dict:
        return asdict(self)


def compute_costs(
    *,
    side: str,                  # 'BUY' or 'SELL'
    premium: float,             # absolute price per unit (the option premium)
    qty: int,                   # contract size × lots
    exchange: str = "NSE",
    schedule: FeeSchedule = DEFAULT_SCHEDULE,
) -> CostBreakdown:
    """Compute per-leg cost in INR. Apply twice (buy + sell) for round trip.

    For F&O option premium turnover: turnover = premium * qty.
    """
    side = side.upper()
    exch = exchange.upper()

    turnover = float(Decimal(str(premium)) * Decimal(str(qty)))

    # Brokerage: flat ₹20 or 0.03% of turnover, whichever is lower (per-order)
    brokerage_pct = turnover * schedule.brokerage_pct_cap
    brokerage = min(schedule.brokerage_per_order, brokerage_pct)
    brokerage = max(0.0, brokerage)  # never negative

    # STT — sell side only on premium turnover
    stt = turnover * schedule.stt_sell_premium if side == "SELL" else 0.0

    # Exchange transaction charges
    rate = (
        schedule.transaction_charge_bse
        if exch == "BSE"
        else schedule.transaction_charge_nse
    )
    txn = turnover * rate

    # SEBI: ₹10 per crore (1e7) of turnover
    sebi = turnover * (schedule.sebi_charges_per_cr / 1e7)

    # Stamp duty — buy side only
    stamp = turnover * schedule.stamp_duty_buy if side == "BUY" else 0.0

    # GST on (brokerage + transaction + SEBI)
    gst = (brokerage + txn + sebi) * schedule.gst_on_brokerage_and_charges

    total = brokerage + stt + txn + sebi + stamp + gst

    return CostBreakdown(
        brokerage=_paisa(brokerage),
        stt=_paisa(stt),
        transaction_charges=_paisa(txn),
        sebi=_paisa(sebi),
        stamp_duty=_paisa(stamp),
        gst=_paisa(gst),
        total=_paisa(total),
    )


def round_trip_cost(
    *,
    buy_premium: float,
    sell_premium: float,
    qty: int,
    exchange: str = "NSE",
    schedule: FeeSchedule = DEFAULT_SCHEDULE,
) -> CostBreakdown:
    """Combined buy+sell cost — what the contract note labels 'Total charges'."""
    buy = compute_costs(
        side="BUY",
        premium=buy_premium,
        qty=qty,
        exchange=exchange,
        schedule=schedule,
    )
    sell = compute_costs(
        side="SELL",
        premium=sell_premium,
        qty=qty,
        exchange=exchange,
        schedule=schedule,
    )
    return CostBreakdown(
        brokerage=_paisa(buy.brokerage + sell.brokerage),
        stt=_paisa(buy.stt + sell.stt),
        transaction_charges=_paisa(buy.transaction_charges + sell.transaction_charges),
        sebi=_paisa(buy.sebi + sell.sebi),
        stamp_duty=_paisa(buy.stamp_duty + sell.stamp_duty),
        gst=_paisa(buy.gst + sell.gst),
        total=_paisa(buy.total + sell.total),
    )
