"""
Cost model for Indian options trading (Zerodha + Upstox fee schedules).
All costs per leg (entry + exit must be calculated separately).
"""

from dataclasses import dataclass
from typing import Literal

@dataclass
class CostBreakdown:
    """Cost breakdown for a single trade leg."""
    brokerage: float
    stt: float
    exchange_charges: float
    gst: float
    sebi_charges: float
    total: float

    def total_percent(self, premium: float) -> float:
        """Cost as percentage of premium."""
        if premium == 0:
            return 0
        return (self.total / premium) * 100


class CostModel:
    """
    Cost model for Zerodha + Upstox options trading.
    Reference: Zerodha charge structure (as of Oct 2024).
    """

    # Zerodha brokerage: ₹20/order or 0.03% (whichever is lower) for options
    ZERODHA_BROKERAGE_PER_ORDER = 20  # Fixed ₹20
    ZERODHA_BROKERAGE_PERCENT = 0.0003  # 0.03% cap for options

    # Upstox brokerage: Free for first 50 orders/month, then ₹20/order
    UPSTOX_BROKERAGE_PER_ORDER = 0  # Assume free tier

    # STT (Securities Transaction Tax) on options premium:
    # Sell-side: 0.05% of premium (from Oct 2024)
    # Buy-side: None (except for index options: 0.017% buy-side since June 2024)
    STT_SELL_SIDE = 0.0005  # 0.05%
    STT_BUY_SIDE_INDEX = 0.00017  # 0.017% for index options (Nifty, Bank Nifty)
    STT_BUY_SIDE_STOCK = 0.0  # No STT on stock options buy-side

    # Exchange charges (NSE + BSE):
    # ~0.002% to 0.0039% depending on segment
    EXCHANGE_CHARGES_PERCENT = 0.000030  # ~0.003% (conservative estimate)

    # SEBI charges: ₹100 per crore of turnover (0.00001%)
    SEBI_CHARGES_PERCENT = 0.000001  # 0.0001%

    # GST on brokerage + charges: 18%
    GST_PERCENT = 0.18

    # Stamp duty: ₹10 per ₹1000 of turnover (0.001%)
    STAMP_DUTY_PERCENT = 0.00001  # ~0.001%

    def __init__(self, broker: Literal["zerodha", "upstox"] = "zerodha"):
        self.broker = broker

    def calculate_costs_per_leg(
        self,
        premium: float,
        side: Literal["buy", "sell"],
        instrument_type: Literal["index_option", "stock_option", "future"] = "index_option",
        broker: str = None,
    ) -> CostBreakdown:
        """
        Calculate total costs for a single trade leg (entry or exit).

        Args:
            premium: Option premium or notional value
            side: 'buy' or 'sell'
            instrument_type: 'index_option', 'stock_option', or 'future'
            broker: Override broker setting; uses self.broker if None

        Returns:
            CostBreakdown with all costs
        """
        broker = broker or self.broker

        # Brokerage
        if broker == "upstox":
            brokerage = self.UPSTOX_BROKERAGE_PER_ORDER
        else:  # zerodha
            brokerage = min(self.ZERODHA_BROKERAGE_PER_ORDER, premium * self.ZERODHA_BROKERAGE_PERCENT)

        # STT (sell-side only)
        if side == "sell":
            stt = premium * self.STT_SELL_SIDE
        elif side == "buy" and instrument_type == "index_option":
            stt = premium * self.STT_BUY_SIDE_INDEX
        else:
            stt = 0

        # Exchange charges + SEBI
        exchange = premium * self.EXCHANGE_CHARGES_PERCENT
        sebi = premium * self.SEBI_CHARGES_PERCENT

        # GST on brokerage + exchange + SEBI
        taxable_charges = brokerage + exchange + sebi
        gst = taxable_charges * self.GST_PERCENT

        # Stamp duty
        stamp = premium * self.STAMP_DUTY_PERCENT

        total = brokerage + stt + exchange + sebi + gst + stamp

        return CostBreakdown(
            brokerage=brokerage,
            stt=stt,
            exchange_charges=exchange + sebi,
            gst=gst,
            sebi_charges=stamp,  # Grouping stamp duty here for simplicity
            total=total,
        )

    def calculate_roundtrip_cost(
        self,
        entry_premium: float,
        exit_premium: float,
        instrument_type: Literal["index_option", "stock_option"] = "index_option",
        broker: str = None,
    ) -> dict:
        """
        Calculate total cost for a roundtrip (entry + exit).

        Returns dict with:
        - entry_cost: cost to enter (buy)
        - exit_cost: cost to exit (sell)
        - total_cost: sum
        - total_percent: total as % of entry premium
        """
        entry = self.calculate_costs_per_leg(entry_premium, "buy", instrument_type, broker)
        exit = self.calculate_costs_per_leg(exit_premium, "sell", instrument_type, broker)

        total = entry.total + exit.total
        percent = (total / entry_premium * 100) if entry_premium > 0 else 0

        return {
            "entry_cost": entry.total,
            "exit_cost": exit.total,
            "total_cost": total,
            "total_percent": percent,
            "entry_breakdown": entry,
            "exit_breakdown": exit,
        }

    def min_profit_for_viability(
        self,
        entry_premium: float,
        min_sharpe: float = 1.5,
        instrument_type: Literal["index_option", "stock_option"] = "index_option",
    ) -> float:
        """
        Minimum profit points required for a scalp to be viable after costs.

        Args:
            entry_premium: Entry premium (INR)
            min_sharpe: Minimum Sharpe ratio required (default 1.5)
            instrument_type: 'index_option' or 'stock_option'

        Returns:
            Minimum points (INR) exit premium should be above entry
        """
        # Cost for roundtrip
        costs = self.calculate_roundtrip_cost(entry_premium, entry_premium, instrument_type)
        total_cost_pct = costs["total_percent"] / 100

        # For breakeven + 1.5x safety margin: exit should be at least entry + costs * 1.5
        min_exit = entry_premium * (1 + total_cost_pct * min_sharpe)
        min_profit = min_exit - entry_premium

        return min_profit


if __name__ == "__main__":
    # Example: 1-point scalp on ₹100 premium
    cost_model = CostModel(broker="zerodha")

    entry_premium = 100
    exit_premium = 101  # 1-point profit
    costs = cost_model.calculate_roundtrip_cost(entry_premium, exit_premium, "index_option")

    print(f"Entry premium: ₹{entry_premium}")
    print(f"Exit premium: ₹{exit_premium}")
    print(f"Gross profit: ₹{exit_premium - entry_premium}")
    print(f"Total cost: ₹{costs['total_cost']:.2f} ({costs['total_percent']:.2f}%)")
    print(f"Net profit: ₹{(exit_premium - entry_premium) - costs['total_cost']:.2f}")

    # What's the minimum exit price for viability?
    min_exit = entry_premium + cost_model.min_profit_for_viability(entry_premium)
    print(f"\nMinimum viable exit: ₹{min_exit:.2f} (profit: ₹{min_exit - entry_premium:.2f})")
