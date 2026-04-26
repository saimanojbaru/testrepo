"""Indian F&O cost calculator tests — paisa-precision."""

from __future__ import annotations

from app.costs.india_fno import compute_costs, round_trip_cost


def test_brokerage_capped_at_flat_20_when_turnover_high():
    # turnover 100 * 7500 = 7,50,000 → 0.03% = ₹225, cap is ₹20
    c = compute_costs(side="BUY", premium=100, qty=7500)
    assert c.brokerage == 20.0


def test_brokerage_uses_pct_when_turnover_low():
    # turnover 1 * 100 = 100 → 0.03% = ₹0.03, less than ₹20
    c = compute_costs(side="BUY", premium=1, qty=100)
    assert c.brokerage == 0.03


def test_stt_only_on_sell_side():
    buy = compute_costs(side="BUY", premium=100, qty=75)
    sell = compute_costs(side="SELL", premium=100, qty=75)
    assert buy.stt == 0.0
    # 100 * 75 * 0.001 = 7.50
    assert sell.stt == 7.5


def test_stamp_duty_only_on_buy_side():
    buy = compute_costs(side="BUY", premium=100, qty=1000)
    sell = compute_costs(side="SELL", premium=100, qty=1000)
    assert buy.stamp_duty > 0
    assert sell.stamp_duty == 0.0


def test_gst_is_18_percent_of_brokerage_plus_charges():
    c = compute_costs(side="BUY", premium=100, qty=10000)
    # GST = 18% of (brokerage + transaction + sebi)
    expected_base = c.brokerage + c.transaction_charges + c.sebi
    assert abs(c.gst - round(expected_base * 0.18, 2)) < 0.02


def test_round_trip_combines_buy_and_sell():
    rt = round_trip_cost(buy_premium=100, sell_premium=110, qty=75)
    leg_buy = compute_costs(side="BUY", premium=100, qty=75)
    leg_sell = compute_costs(side="SELL", premium=110, qty=75)
    assert abs(rt.total - (leg_buy.total + leg_sell.total)) < 0.05


def test_paisa_rounding_holds():
    c = compute_costs(side="SELL", premium=123.456789, qty=75)
    # Every field must have at most 2 decimal places
    for v in (c.brokerage, c.stt, c.transaction_charges, c.sebi, c.gst, c.total):
        assert round(v, 2) == v
