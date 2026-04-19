"""Cost model conforms to published Zerodha/Upstox F&O option schedule.

Anchors the model against a concrete, hand-computed example so any rate changes
are immediately visible.
"""
from __future__ import annotations

import pytest

from costs import Trade, TradeSide, compute_costs, net_pnl


def test_sell_leg_stt_applied_but_not_stamp_duty():
    # 1 lot of Nifty CE (50 units) @ premium 100
    sell = Trade(side=TradeSide.SELL, qty=50, premium=100.0)
    c = compute_costs(sell)

    # turnover = 5000
    # STT = 0.1% * 5000 = 5.0
    # exchange = 0.0503% * 5000 = 2.515
    # sebi = 0.0001% * 5000 = 0.005
    # brokerage = min(20, 5000 * 0.0003 = 1.5) = 1.5
    # stamp_duty on sell = 0
    # GST = 0.18 * (1.5 + 2.515 + 0.005) = 0.7236 -> round to 0.72
    assert c.stt == pytest.approx(5.0, abs=0.01)
    assert c.stamp_duty == 0.0
    assert c.exchange_txn == pytest.approx(2.515, abs=0.005)
    assert c.brokerage == pytest.approx(1.5, abs=0.01)
    assert c.gst == pytest.approx(0.72, abs=0.01)


def test_buy_leg_stamp_duty_but_no_stt():
    buy = Trade(side=TradeSide.BUY, qty=50, premium=100.0)
    c = compute_costs(buy)
    # stamp_duty = 0.003% * 5000 = 0.15
    # STT = 0 (buy side)
    assert c.stamp_duty == pytest.approx(0.15, abs=0.01)
    assert c.stt == 0.0


def test_round_trip_net_pnl_long():
    # Buy at 100, sell at 105, 50 units (1 lot)
    entry = Trade(side=TradeSide.BUY, qty=50, premium=100.0)
    exit = Trade(side=TradeSide.SELL, qty=50, premium=105.0)
    net, ec, xc = net_pnl(entry, exit)
    gross = 5.0 * 50  # = 250
    expected_net = gross - ec.total - xc.total
    assert net == pytest.approx(expected_net, abs=0.01)
    # Sanity — costs should be non-trivial relative to tiny scalp
    assert ec.total + xc.total > 5.0


def test_brokerage_caps_at_flat_20_for_large_premium():
    # Premium 1000 * 50 = 50000 turnover; 0.03% = 15 < 20 so still pct
    # Premium 1500 * 50 = 75000; 0.03% = 22.5 > 20 so caps at 20
    small = compute_costs(Trade(side=TradeSide.BUY, qty=50, premium=1000.0))
    assert small.brokerage == pytest.approx(15.0, abs=0.01)
    big = compute_costs(Trade(side=TradeSide.BUY, qty=50, premium=1500.0))
    assert big.brokerage == pytest.approx(20.0, abs=0.01)


def test_qty_mismatch_raises():
    with pytest.raises(ValueError):
        net_pnl(
            Trade(side=TradeSide.BUY, qty=50, premium=100.0),
            Trade(side=TradeSide.SELL, qty=25, premium=105.0),
        )
