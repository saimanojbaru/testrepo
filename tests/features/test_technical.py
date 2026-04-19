from __future__ import annotations

import numpy as np
import pandas as pd
import pytest

from features.technical import adx, atr, bollinger, ema, macd, rsi, sma, supertrend, vwap


def _ramp(values: list[float]) -> pd.Series:
    return pd.Series(values, dtype=float)


def test_sma_matches_manual():
    s = _ramp([1, 2, 3, 4, 5])
    out = sma(s, 3)
    assert out.iloc[0] != out.iloc[0] or np.isnan(out.iloc[0])  # first two NaN
    assert out.iloc[2] == pytest.approx(2.0)
    assert out.iloc[4] == pytest.approx(4.0)


def test_ema_monotone_on_ramp():
    s = _ramp([1, 2, 3, 4, 5, 6, 7, 8, 9, 10])
    out = ema(s, 3).dropna()
    assert (out.diff().dropna() > 0).all()


def test_rsi_all_gains_pegs_at_100():
    s = _ramp(list(range(1, 30)))
    r = rsi(s, window=14).dropna()
    # Monotonic gains -> RSI should peg at 100 (avg_down == 0 branch)
    assert (r == 100.0).all()


def test_rsi_all_losses_pegs_near_zero():
    s = _ramp(list(range(30, 1, -1)))
    r = rsi(s, window=14).dropna()
    assert (r < 5.0).all()


def test_macd_signal_lines_present():
    s = _ramp(list(range(1, 60)) + list(range(60, 10, -1)))
    m = macd(s)
    assert {"macd", "signal", "histogram"}.issubset(m.columns)
    assert m["histogram"].dropna().abs().sum() > 0


def test_vwap_session_reset():
    rows = []
    base_ts = pd.Timestamp("2026-04-01 09:15", tz="Asia/Kolkata")
    for d in range(2):
        day = base_ts + pd.Timedelta(days=d)
        for i in range(5):
            rows.append(
                {
                    "ts": day + pd.Timedelta(minutes=i),
                    "high": 100 + i + d * 10,
                    "low": 99 + i + d * 10,
                    "close": 99.5 + i + d * 10,
                    "volume": 1000,
                }
            )
    df = pd.DataFrame(rows)
    v = vwap(df)
    # Day 2 first-bar VWAP should be the typical price of the first day-2 bar, not a
    # carry-through from day 1.
    assert v.iloc[5] == pytest.approx((df.loc[5, "high"] + df.loc[5, "low"] + df.loc[5, "close"]) / 3.0)


def test_bollinger_upper_lower_wider_with_more_std():
    s = _ramp(list(range(1, 50)))
    b1 = bollinger(s, window=10, stds=1.0).dropna()
    b2 = bollinger(s, window=10, stds=3.0).dropna()
    assert (b2["upper"] >= b1["upper"]).all()
    assert (b2["lower"] <= b1["lower"]).all()


def test_atr_positive_with_movement():
    rows = [{"high": 101 + i % 3, "low": 99 - i % 3, "close": 100 + i * 0.1} for i in range(60)]
    df = pd.DataFrame(rows)
    a = atr(df, window=14).dropna()
    assert (a > 0).all()


def test_supertrend_direction_flips():
    # Large swing: up then down with gap large enough to cross the trailing band
    up = [{"high": 100 + i * 2, "low": 99 + i * 2, "close": 100 + i * 2} for i in range(40)]
    down = [{"high": 180 - i * 3, "low": 179 - i * 3, "close": 180 - i * 3} for i in range(1, 60)]
    df = pd.DataFrame(up + down)
    st = supertrend(df, window=5, multiplier=1.0)
    directions = st["direction"].dropna().unique()
    assert set(directions).issuperset({1.0, -1.0})


def test_adx_nonnegative():
    rng = np.random.default_rng(42)
    rows = [{"high": 100 + rng.normal(0, 1), "low": 99 + rng.normal(0, 1), "close": 99.5 + rng.normal(0, 1)} for _ in range(60)]
    df = pd.DataFrame(rows)
    a = adx(df, window=14).dropna()
    assert (a >= 0).all()
