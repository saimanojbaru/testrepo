"""Technical indicators — pure pandas/numpy implementations.

Kept independent of pandas-ta so tests run against deterministic golden values
rather than a third-party version-sensitive library.
"""
from __future__ import annotations

import numpy as np
import pandas as pd


def sma(series: pd.Series, window: int) -> pd.Series:
    return series.rolling(window, min_periods=window).mean()


def ema(series: pd.Series, window: int) -> pd.Series:
    return series.ewm(span=window, adjust=False, min_periods=window).mean()


def rsi(series: pd.Series, window: int = 14) -> pd.Series:
    """Wilder's RSI using simple moving averages on up/down closes.

    Returns NaN during the warm-up window. When all moves are losses (avg_up == 0)
    RSI is 0; when all are gains (avg_down == 0) RSI is 100.
    """
    delta = series.diff()
    up = delta.clip(lower=0.0)
    down = -delta.clip(upper=0.0)
    avg_up = up.rolling(window, min_periods=window).mean()
    avg_down = down.rolling(window, min_periods=window).mean()
    rs = avg_up / avg_down.replace(0.0, np.nan)
    out = 100.0 - (100.0 / (1.0 + rs))
    # When avg_down == 0 (only gains) -> 100; when avg_up == 0 (only losses) -> 0
    out = out.where(~((avg_down == 0) & avg_up.notna()), 100.0)
    out = out.where(~((avg_up == 0) & avg_down.notna()), 0.0)
    return out


def macd(series: pd.Series, fast: int = 12, slow: int = 26, signal: int = 9) -> pd.DataFrame:
    macd_line = ema(series, fast) - ema(series, slow)
    signal_line = macd_line.ewm(span=signal, adjust=False, min_periods=signal).mean()
    return pd.DataFrame(
        {"macd": macd_line, "signal": signal_line, "histogram": macd_line - signal_line}
    )


def vwap(df: pd.DataFrame) -> pd.Series:
    """Session-resetting VWAP. Expects columns [ts, high, low, close, volume]."""
    typical = (df["high"] + df["low"] + df["close"]) / 3.0
    session = df["ts"].dt.date
    pv = (typical * df["volume"]).groupby(session).cumsum()
    vol = df["volume"].groupby(session).cumsum()
    return pv / vol.replace(0.0, np.nan)


def bollinger(series: pd.Series, window: int = 20, stds: float = 2.0) -> pd.DataFrame:
    mean = sma(series, window)
    std = series.rolling(window, min_periods=window).std()
    return pd.DataFrame(
        {"mid": mean, "upper": mean + stds * std, "lower": mean - stds * std}
    )


def atr(df: pd.DataFrame, window: int = 14) -> pd.Series:
    """Average True Range using Wilder smoothing."""
    prev_close = df["close"].shift(1)
    tr = pd.concat(
        [df["high"] - df["low"], (df["high"] - prev_close).abs(), (df["low"] - prev_close).abs()],
        axis=1,
    ).max(axis=1)
    return tr.ewm(alpha=1.0 / window, adjust=False, min_periods=window).mean()


def supertrend(df: pd.DataFrame, window: int = 10, multiplier: float = 3.0) -> pd.DataFrame:
    a = atr(df, window)
    hl2 = (df["high"] + df["low"]) / 2.0
    upper = hl2 + multiplier * a
    lower = hl2 - multiplier * a
    direction = pd.Series(index=df.index, dtype=float)
    st = pd.Series(index=df.index, dtype=float)
    last_dir = 1.0
    for i in range(len(df)):
        if i == 0 or pd.isna(upper.iloc[i]):
            direction.iloc[i] = 1.0
            st.iloc[i] = lower.iloc[i] if not pd.isna(lower.iloc[i]) else np.nan
            continue
        close_i = df["close"].iloc[i]
        if close_i > upper.iloc[i - 1]:
            last_dir = 1.0
        elif close_i < lower.iloc[i - 1]:
            last_dir = -1.0
        direction.iloc[i] = last_dir
        st.iloc[i] = lower.iloc[i] if last_dir > 0 else upper.iloc[i]
    return pd.DataFrame({"supertrend": st, "direction": direction})


def adx(df: pd.DataFrame, window: int = 14) -> pd.Series:
    """Wilder's ADX."""
    up = df["high"].diff()
    down = -df["low"].diff()
    plus_dm = up.where((up > down) & (up > 0), 0.0)
    minus_dm = down.where((down > up) & (down > 0), 0.0)
    tr = atr(df, window) * window  # reverse the ema to get summed TR proxy
    plus_di = 100 * plus_dm.ewm(alpha=1.0 / window, adjust=False, min_periods=window).mean() / tr
    minus_di = 100 * minus_dm.ewm(alpha=1.0 / window, adjust=False, min_periods=window).mean() / tr
    dx = 100 * (plus_di - minus_di).abs() / (plus_di + minus_di).replace(0.0, np.nan)
    return dx.ewm(alpha=1.0 / window, adjust=False, min_periods=window).mean()
