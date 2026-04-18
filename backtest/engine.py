"""
Event-driven backtesting engine for options scalping.
Supports walk-forward validation and cost-aware P&L calculation.
"""

from dataclasses import dataclass, field
from typing import Callable, List, Optional, Tuple
from datetime import datetime, timedelta
import pandas as pd
import numpy as np
from enum import Enum

from backtest.costs import CostModel


class OrderSide(Enum):
    BUY = "buy"
    SELL = "sell"


class OrderStatus(Enum):
    PENDING = "pending"
    FILLED = "filled"
    CANCELLED = "cancelled"


@dataclass
class Order:
    """Represents a single order."""
    timestamp: datetime
    symbol: str
    side: OrderSide
    premium: float
    quantity: int = 1
    status: OrderStatus = OrderStatus.PENDING
    filled_at: Optional[float] = None
    filled_timestamp: Optional[datetime] = None

    def fill(self, filled_premium: float, filled_timestamp: datetime):
        self.filled_at = filled_premium
        self.filled_timestamp = filled_timestamp
        self.status = OrderStatus.FILLED


@dataclass
class Trade:
    """Represents a completed roundtrip (entry + exit)."""
    entry_order: Order
    exit_order: Order
    entry_slippage: float = 0.0  # Points
    exit_slippage: float = 0.0  # Points
    pnl_gross: float = 0.0
    pnl_net: float = 0.0
    costs: dict = field(default_factory=dict)

    def calculate_pnl(self, cost_model: CostModel, instrument_type: str = "index_option"):
        """Calculate P&L including costs and slippage."""
        entry_fill = self.entry_order.filled_at + self.entry_slippage
        exit_fill = self.exit_order.filled_at - self.exit_slippage  # Negative slippage on exit

        self.pnl_gross = (exit_fill - entry_fill) * self.entry_order.quantity
        self.costs = cost_model.calculate_roundtrip_cost(
            entry_fill, exit_fill, instrument_type
        )
        self.pnl_net = self.pnl_gross - self.costs["total_cost"]


@dataclass
class BacktestStats:
    """Statistics from a backtest run."""
    total_trades: int = 0
    winning_trades: int = 0
    losing_trades: int = 0
    total_pnl: float = 0.0
    net_pnl: float = 0.0
    gross_return: float = 0.0
    net_return: float = 0.0
    sharpe_ratio: float = 0.0
    sortino_ratio: float = 0.0
    max_drawdown: float = 0.0
    win_rate: float = 0.0
    expectancy: float = 0.0  # Average P&L per trade
    profit_factor: float = 0.0
    trades: List[Trade] = field(default_factory=list)

    def calculate(self, initial_capital: float, risk_free_rate: float = 0.06):
        """Calculate stats from trades."""
        if not self.trades:
            return

        self.total_trades = len(self.trades)
        self.winning_trades = sum(1 for t in self.trades if t.pnl_net > 0)
        self.losing_trades = sum(1 for t in self.trades if t.pnl_net < 0)
        self.win_rate = self.winning_trades / self.total_trades if self.total_trades > 0 else 0

        pnls = np.array([t.pnl_net for t in self.trades])
        self.total_pnl = pnls.sum()
        self.net_return = self.total_pnl / initial_capital if initial_capital > 0 else 0

        # Sharpe ratio
        if len(pnls) > 1:
            daily_returns = pnls / initial_capital
            excess_returns = daily_returns - risk_free_rate / 252
            self.sharpe_ratio = (
                np.mean(excess_returns) / np.std(excess_returns) * np.sqrt(252)
                if np.std(excess_returns) > 0
                else 0
            )

        # Expectancy
        self.expectancy = self.total_pnl / self.total_trades if self.total_trades > 0 else 0

        # Profit factor (sum of wins / sum of losses)
        wins = pnls[pnls > 0].sum()
        losses = abs(pnls[pnls < 0].sum())
        self.profit_factor = wins / losses if losses > 0 else 0

    def __str__(self):
        return f"""
BacktestStats:
  Total trades: {self.total_trades}
  Win rate: {self.win_rate:.1%}
  Net P&L: ₹{self.total_pnl:,.0f}
  Net return: {self.net_return:.2%}
  Sharpe: {self.sharpe_ratio:.2f}
  Max DD: {self.max_drawdown:.2%}
  Expectancy: ₹{self.expectancy:.0f}
  Profit factor: {self.profit_factor:.2f}
        """


class BacktestEngine:
    """
    Event-driven backtesting engine.
    Simulates market execution with slippage + costs.
    """

    def __init__(self, initial_capital: float = 100000, cost_model: CostModel = None):
        self.initial_capital = initial_capital
        self.cost_model = cost_model or CostModel(broker="zerodha")
        self.trades: List[Trade] = []
        self.pending_orders: List[Order] = []
        self.position: Optional[Order] = None  # Current open position

    def reset(self):
        """Reset engine for new backtest."""
        self.trades = []
        self.pending_orders = []
        self.position = None

    def place_order(
        self,
        timestamp: datetime,
        symbol: str,
        side: OrderSide,
        premium: float,
        quantity: int = 1,
    ) -> Order:
        """Place an order (entry or exit)."""
        order = Order(
            timestamp=timestamp,
            symbol=symbol,
            side=side,
            premium=premium,
            quantity=quantity,
        )
        self.pending_orders.append(order)
        return order

    def fill_order(
        self,
        order: Order,
        filled_premium: float,
        filled_timestamp: datetime,
        slippage: float = 0.0,
    ):
        """
        Fill a pending order.
        Slippage in points (for sell orders, negative = worse fill).
        """
        order.fill(filled_premium, filled_timestamp)

        if order.side == OrderSide.BUY:
            # Entry
            self.position = order
        elif order.side == OrderSide.SELL and self.position:
            # Exit: close position and record trade
            trade = Trade(
                entry_order=self.position,
                exit_order=order,
                entry_slippage=0,  # Assume perfect entry fill
                exit_slippage=slippage,
            )
            trade.calculate_pnl(self.cost_model)
            self.trades.append(trade)
            self.position = None

        # Remove from pending
        self.pending_orders.remove(order)

    def backtest(
        self,
        df: pd.DataFrame,
        signal_fn: Callable[[pd.DataFrame, int], Optional[Tuple[str, float]]],
        slippage_fn: Callable[[pd.DataFrame, int], float] = None,
    ) -> BacktestStats:
        """
        Run a backtest using historical data.

        Args:
            df: DataFrame with columns ['timestamp', 'symbol', 'premium', ...]
            signal_fn: Function(df, row_index) -> (side: 'buy'/'sell', premium: float) or None
            slippage_fn: Function(df, row_index) -> slippage_points

        Returns:
            BacktestStats with results
        """
        self.reset()

        if slippage_fn is None:
            slippage_fn = lambda df, i: 0  # No slippage by default

        for i in range(len(df)):
            row = df.iloc[i]
            timestamp = row.get("timestamp", None)
            symbol = row.get("symbol", "NIFTY")
            premium = row.get("premium", 0)

            # Generate signal
            signal = signal_fn(df, i)
            if signal is None:
                continue

            side_str, entry_premium = signal
            side = OrderSide.BUY if side_str.lower() == "buy" else OrderSide.SELL

            # Entry
            if side == OrderSide.BUY and self.position is None:
                self.place_order(timestamp, symbol, side, entry_premium)
                self.fill_order(
                    self.pending_orders[-1],
                    entry_premium,
                    timestamp,
                    slippage=0,
                )

            # Exit
            elif side == OrderSide.SELL and self.position:
                self.place_order(timestamp, symbol, side, premium)
                slippage = slippage_fn(df, i)
                self.fill_order(
                    self.pending_orders[-1],
                    premium,
                    timestamp,
                    slippage=slippage,
                )

        # Calculate stats
        stats = BacktestStats(trades=self.trades)
        stats.calculate(self.initial_capital)

        return stats

    def walk_forward_backtest(
        self,
        df: pd.DataFrame,
        signal_fn: Callable,
        train_months: int = 6,
        test_months: int = 1,
        slippage_fn: Callable = None,
    ) -> List[BacktestStats]:
        """
        Walk-forward validation: train on N months, test on 1 month, roll forward.

        Returns:
            List of BacktestStats for each test window
        """
        results = []

        # Convert to datetime
        df["timestamp"] = pd.to_datetime(df["timestamp"])
        df = df.sort_values("timestamp")

        start_date = df["timestamp"].min()
        end_date = df["timestamp"].max()

        current_train_start = start_date

        while current_train_start < end_date:
            train_end = current_train_start + pd.DateOffset(months=train_months)
            test_end = train_end + pd.DateOffset(months=test_months)

            if test_end > end_date:
                break

            # Split data
            train_df = df[(df["timestamp"] >= current_train_start) & (df["timestamp"] < train_end)]
            test_df = df[(df["timestamp"] >= train_end) & (df["timestamp"] < test_end)]

            if len(test_df) == 0:
                break

            # Train (placeholder: in real use, would train model here)
            # For now, just run strategy on test data
            stats = self.backtest(test_df, signal_fn, slippage_fn)
            results.append(stats)

            # Roll forward
            current_train_start = train_end

        return results
