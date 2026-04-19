from __future__ import annotations

import pandas as pd
import pytest

from features.options import bs_greeks, iv_rank, max_pain, put_call_ratio


def test_bs_call_greeks_atm():
    g = bs_greeks(spot=100.0, strike=100.0, t_years=30 / 365, rate=0.06, iv=0.20, option_type="CE")
    assert 0.45 < g.delta < 0.60
    assert g.gamma > 0
    assert g.vega > 0
    assert g.theta < 0  # long calls decay


def test_bs_put_greeks_atm():
    g = bs_greeks(spot=100.0, strike=100.0, t_years=30 / 365, rate=0.06, iv=0.20, option_type="PE")
    assert -0.55 < g.delta < -0.40
    assert g.gamma > 0


def test_bs_zero_time_all_zero():
    g = bs_greeks(spot=100.0, strike=100.0, t_years=0.0, rate=0.06, iv=0.20, option_type="CE")
    assert g.delta == 0 and g.gamma == 0 and g.vega == 0 and g.theta == 0


def test_put_call_ratio():
    chain = pd.DataFrame(
        [
            {"strike": 100, "option_type": "CE", "open_interest": 1000},
            {"strike": 100, "option_type": "PE", "open_interest": 2000},
        ]
    )
    assert put_call_ratio(chain) == pytest.approx(2.0)


def test_max_pain_returns_a_strike():
    chain = pd.DataFrame(
        [
            {"strike": 100.0, "option_type": "CE", "open_interest": 5000},
            {"strike": 100.0, "option_type": "PE", "open_interest": 5000},
            {"strike": 110.0, "option_type": "CE", "open_interest": 3000},
            {"strike": 110.0, "option_type": "PE", "open_interest": 1000},
            {"strike": 90.0,  "option_type": "CE", "open_interest": 1000},
            {"strike": 90.0,  "option_type": "PE", "open_interest": 3000},
        ]
    )
    mp = max_pain(chain)
    assert mp in {90.0, 100.0, 110.0}


def test_iv_rank_range():
    s = pd.Series([0.10, 0.12, 0.15, 0.20, 0.18, 0.25, 0.22, 0.14, 0.30, 0.28] * 3)
    r = iv_rank(s, lookback=10).dropna()
    assert ((r >= 0 - 1e-9) & (r <= 100 + 1e-9)).all()
