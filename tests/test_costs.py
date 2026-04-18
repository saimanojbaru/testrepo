"""
Unit tests for cost model.
Validates that costs are calculated correctly for Zerodha and Upstox.
"""

from backtest.costs import CostModel


def test_zerodha_1point_scalp_on_100_premium():
    """Test: 1-point scalp on ₹100 Nifty option premium."""
    cost_model = CostModel(broker="zerodha")

    entry_premium = 100
    exit_premium = 101
    costs = cost_model.calculate_roundtrip_cost(entry_premium, exit_premium, "index_option")

    # Verify costs are calculated
    assert costs["total_cost"] > 0
    assert costs["total_percent"] > 0

    # Net profit after costs
    gross_profit = exit_premium - entry_premium  # ₹1
    net_profit = gross_profit - costs["total_cost"]

    print(f"\n1-point scalp on ₹100 premium:")
    print(f"  Gross profit: ₹{gross_profit}")
    print(f"  Total cost: ₹{costs['total_cost']:.2f} ({costs['total_percent']:.2f}%)")
    print(f"  Net profit: ₹{net_profit:.2f}")

    # Expected: likely negative or break-even without 1.5x margin
    assert net_profit < gross_profit


def test_min_viability_for_scalp():
    """Test: Calculate minimum points needed for viable scalp."""
    cost_model = CostModel(broker="zerodha")

    entry_premium = 100
    min_points = cost_model.min_profit_for_viability(entry_premium, min_sharpe=1.5)

    print(f"\nMinimum viable scalp on ₹{entry_premium} premium:")
    print(f"  Min profit points: ₹{min_points:.2f}")
    print(f"  Min viable exit: ₹{entry_premium + min_points:.2f}")

    # Verify that min_points is positive and represents 1.5x cost margin
    assert min_points > 0
    # At 0.15% costs and 1.5x margin, min is ~0.22 points for ₹100 premium
    assert min_points >= 0.2


def test_cost_breakdown():
    """Test: Verify cost breakdown components."""
    cost_model = CostModel(broker="zerodha")

    entry = cost_model.calculate_costs_per_leg(100, "buy", "index_option")
    exit = cost_model.calculate_costs_per_leg(101, "sell", "index_option")

    print(f"\nCost breakdown for ₹100 (buy) and ₹101 (sell):")
    print(f"  Entry brokerage: ₹{entry.brokerage:.2f}")
    print(f"  Entry STT: ₹{entry.stt:.2f}")
    print(f"  Entry total: ₹{entry.total:.2f}")
    print(f"  Exit brokerage: ₹{exit.brokerage:.2f}")
    print(f"  Exit STT: ₹{exit.stt:.2f} (sell-side STT)")
    print(f"  Exit total: ₹{exit.total:.2f}")
    print(f"  Roundtrip total: ₹{entry.total + exit.total:.2f}")

    # Verify components
    assert entry.brokerage > 0
    assert entry.total > 0
    assert exit.stt > 0  # Sell-side STT
    assert exit.total > entry.total  # Sell side has more costs


def test_upstox_vs_zerodha():
    """Test: Compare costs between brokers."""
    zerodha = CostModel(broker="zerodha")
    upstox = CostModel(broker="upstox")

    entry_premium = 100
    exit_premium = 101

    z_costs = zerodha.calculate_roundtrip_cost(entry_premium, exit_premium, "index_option")
    u_costs = upstox.calculate_roundtrip_cost(entry_premium, exit_premium, "index_option")

    print(f"\nZerodha vs Upstox for ₹100 → ₹101 scalp:")
    print(f"  Zerodha: ₹{z_costs['total_cost']:.2f} ({z_costs['total_percent']:.2f}%)")
    print(f"  Upstox: ₹{u_costs['total_cost']:.2f} ({u_costs['total_percent']:.2f}%)")

    # Upstox should be cheaper (no brokerage in free tier)
    assert u_costs["total_cost"] < z_costs["total_cost"]


if __name__ == "__main__":
    test_zerodha_1point_scalp_on_100_premium()
    test_min_viability_for_scalp()
    test_cost_breakdown()
    test_upstox_vs_zerodha()
    print("\n✓ All cost tests passed!")
