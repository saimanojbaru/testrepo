from __future__ import annotations

import pandas as pd
import pytest

from features.microstructure import bid_ask_spread, l1_imbalance, micro_price, mid_price, relative_spread


def _quotes():
    return pd.DataFrame(
        {
            "bid": [99.0, 99.5, 100.0],
            "ask": [100.0, 100.1, 100.2],
            "bid_qty": [100, 200, 150],
            "ask_qty": [100, 100, 50],
        }
    )


def test_spread_metrics():
    q = _quotes()
    sp = bid_ask_spread(q["bid"], q["ask"])
    assert (sp >= 0).all()
    rel = relative_spread(q["bid"], q["ask"])
    assert rel.iloc[0] == pytest.approx(1.0 / 99.5, abs=1e-4)


def test_l1_imbalance_sign():
    q = _quotes()
    imb = l1_imbalance(q["bid_qty"], q["ask_qty"])
    assert imb.iloc[0] == pytest.approx(0.0)
    assert imb.iloc[1] > 0
    assert imb.iloc[2] > 0


def test_micro_price_between_bid_ask():
    q = _quotes()
    mp = micro_price(q["bid"], q["ask"], q["bid_qty"], q["ask_qty"])
    mid = mid_price(q["bid"], q["ask"])
    for i in range(len(q)):
        assert q["bid"].iloc[i] <= mp.iloc[i] <= q["ask"].iloc[i]
        # When sizes differ, micro price drifts away from mid
        if q["bid_qty"].iloc[i] != q["ask_qty"].iloc[i]:
            assert mp.iloc[i] != pytest.approx(mid.iloc[i])
