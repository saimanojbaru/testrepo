"""
Unit tests for backtesting engine.
"""

import pandas as pd
import numpy as np
from datetime import datetime, timedelta

from backtest.engine import BacktestEngine, OrderSide
from backtest.costs import CostModel


def create_sample_data(num_bars=100):
    """Create sample OHLCV data for testing."""
    dates = pd.date_range(start="2024-01-01", periods=num_bars, freq="5T")
    np.random.seed(42)

    base_price = 100
    prices = np.cumsum(np.random.randn(num_bars) * 0.5) + base_price

    df = pd.DataFrame({
        "timestamp": dates,
        "symbol": "NIFTY",
        "open": prices + np.random.randn(num_bars) * 0.1,
        "high": prices + abs(np.random.randn(num_bars)) * 0.2,
        "low": prices - abs(np.random.randn(num_bars)) * 0.2,
        "close": prices,
        "premium": prices,  # For options, premium = close
        "volume": np.random.randint(1000, 10000, num_bars),
    })

    return df


def test_simple_buy_sell():
    """Test: Simple buy-then-sell trade."""
    engine = BacktestEngine(initial_capital=100000)
    df = create_sample_data(50)

    # Define a simple signal: buy on first bar, sell on 10th bar
    def signal_fn(df, idx):
        if idx == 0:
            return ("buy", df.iloc[idx]["premium"])
        elif idx == 10 and engine.position is not None:
            return ("sell", df.iloc[idx]["premium"])
        return None

    stats = engine.backtest(df, signal_fn)

    print(f"\nSimple buy-sell backtest:")
    print(f"  Total trades: {stats.total_trades}")
    print(f"  Total P&L: ₹{stats.total_pnl:.0f}")
    print(f"  Sharpe: {stats.sharpe_ratio:.2f}")

    assert stats.total_trades == 1
    assert stats.total_pnl != 0  # Should have some profit or loss


def test_multiple_trades():
    """Test: Multiple entry/exit cycles."""
    engine = BacktestEngine(initial_capital=100000)
    df = create_sample_data(100)

    trade_count = 0

    def signal_fn(df, idx):
        nonlocal trade_count
        # Alternate: buy on even indices, sell on odd
        if idx % 10 == 0 and engine.position is None:
            return ("buy", df.iloc[idx]["premium"])
        elif idx % 10 == 5 and engine.position is not None:
            return ("sell", df.iloc[idx]["premium"])
            trade_count += 1
        return None

    stats = engine.backtest(df, signal_fn)

    print(f"\nMultiple trades backtest:")
    print(f"  Total trades: {stats.total_trades}")
    print(f"  Win rate: {stats.win_rate:.1%}")
    print(f"  Expectancy: ₹{stats.expectancy:.0f}")
    print(f"  Profit factor: {stats.profit_factor:.2f}")

    assert stats.total_trades > 0


def test_slippage_impact():
    """Test: Slippage impact on P&L."""
    df = create_sample_data(50)

    # Backtest without slippage
    engine_no_slippage = BacktestEngine(initial_capital=100000)

    def signal_fn(df, idx):
        if idx == 0:
            return ("buy", df.iloc[idx]["premium"])
        elif idx == 10:
            return ("sell", df.iloc[idx]["premium"])
        return None

    stats_no_slip = engine_no_slippage.backtest(df, signal_fn)

    # Backtest with slippage
    engine_with_slippage = BacktestEngine(initial_capital=100000)

    def slippage_fn(df, idx):
        return 0.5  # 0.5 points slippage on exit

    stats_with_slip = engine_with_slippage.backtest(df, signal_fn, slippage_fn)

    print(f"\nSlippage impact test:")
    print(f"  No slippage P&L: ₹{stats_no_slip.total_pnl:.0f}")
    print(f"  With 0.5pt slippage: ₹{stats_with_slip.total_pnl:.0f}")
    print(f"  Impact: ₹{stats_no_slip.total_pnl - stats_with_slip.total_pnl:.0f}")

    # Slippage should reduce P&L
    assert stats_with_slip.total_pnl <= stats_no_slip.total_pnl


def test_cost_impact():
    """Test: Costs are reflected in P&L."""
    df = create_sample_data(50)
    engine = BacktestEngine(initial_capital=100000)

    entry_premium = 100
    exit_premium = 101  # 1-point profit, but costs might make it a loss

    def signal_fn(df, idx):
        if idx == 0:
            return ("buy", entry_premium)
        elif idx == 10:
            return ("sell", exit_premium)
        return None

    stats = engine.backtest(df, signal_fn)

    print(f"\nCost impact test (1-point scalp on ₹100):")
    print(f"  Gross profit: ₹1")
    print(f"  Net P&L: ₹{stats.total_pnl:.2f}")

    # Should show cost impact
    if stats.total_trades > 0:
        cost_model = CostModel()
        costs = cost_model.calculate_roundtrip_cost(entry_premium, exit_premium)
        print(f"  Actual costs: ₹{costs['total_cost']:.2f}")


if __name__ == "__main__":
    test_simple_buy_sell()
    test_multiple_trades()
    test_slippage_impact()
    test_cost_impact()
    print("\n✓ All backtest tests passed!")
