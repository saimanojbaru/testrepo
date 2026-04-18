"""
Technical indicators for scalping.
All indicators are deterministic and testable.
"""

import pandas as pd
import numpy as np
from typing import Optional


def rsi(series: pd.Series, period: int = 14) -> pd.Series:
    """Relative Strength Index."""
    delta = series.diff()
    gain = delta.clip(lower=0)
    loss = -delta.clip(upper=0)

    avg_gain = gain.rolling(window=period).mean()
    avg_loss = loss.rolling(window=period).mean()

    rs = avg_gain / avg_loss
    rsi_val = 100 - (100 / (1 + rs))
    return rsi_val


def macd(series: pd.Series, fast: int = 12, slow: int = 26, signal: int = 9) -> tuple:
    """MACD with signal line and histogram."""
    ema_fast = series.ewm(span=fast).mean()
    ema_slow = series.ewm(span=slow).mean()
    macd_line = ema_fast - ema_slow
    signal_line = macd_line.ewm(span=signal).mean()
    histogram = macd_line - signal_line
    return macd_line, signal_line, histogram


def ema(series: pd.Series, period: int) -> pd.Series:
    """Exponential Moving Average."""
    return series.ewm(span=period).mean()


def sma(series: pd.Series, period: int) -> pd.Series:
    """Simple Moving Average."""
    return series.rolling(window=period).mean()


def atr(high: pd.Series, low: pd.Series, close: pd.Series, period: int = 14) -> pd.Series:
    """Average True Range."""
    tr1 = high - low
    tr2 = abs(high - close.shift())
    tr3 = abs(low - close.shift())
    tr = pd.concat([tr1, tr2, tr3], axis=1).max(axis=1)
    return tr.rolling(window=period).mean()


def adx(high: pd.Series, low: pd.Series, close: pd.Series, period: int = 14) -> pd.Series:
    """Average Directional Index (simplified)."""
    plus_dm = high.diff().clip(lower=0)
    minus_dm = (-low.diff()).clip(lower=0)

    plus_di = 100 * (plus_dm.rolling(window=period).mean() /
                     atr(high, low, close, period))
    minus_di = 100 * (minus_dm.rolling(window=period).mean() /
                      atr(high, low, close, period))

    di_diff = abs(plus_di - minus_di)
    di_sum = plus_di + minus_di

    adx_val = 100 * (di_diff / di_sum)
    return adx_val.rolling(window=period).mean()


def vwap(df: pd.DataFrame, period: int = 20) -> pd.Series:
    """
    Volume Weighted Average Price.
    Requires columns: high, low, close, volume
    """
    if "volume" not in df.columns:
        return df["close"]

    typical_price = (df["high"] + df["low"] + df["close"]) / 3
    cumulative_tp_volume = (typical_price * df["volume"]).rolling(window=period).sum()
    cumulative_volume = df["volume"].rolling(window=period).sum()

    return cumulative_tp_volume / cumulative_volume


def bollinger_bands(
    series: pd.Series, period: int = 20, std_dev: float = 2.0
) -> tuple:
    """Bollinger Bands."""
    sma_val = sma(series, period)
    std = series.rolling(window=period).std()
    upper = sma_val + (std * std_dev)
    lower = sma_val - (std * std_dev)
    return upper, sma_val, lower


def realized_volatility(returns: pd.Series, period: int = 20) -> pd.Series:
    """Realized volatility from returns."""
    return returns.rolling(window=period).std() * np.sqrt(252)


def add_technical_features(df: pd.DataFrame) -> pd.DataFrame:
    """
    Add common technical features to a DataFrame.
    Input columns: timestamp, open, high, low, close, volume
    """
    df = df.copy()

    # Simple features
    df["rsi_14"] = rsi(df["close"], 14)
    df["ema_9"] = ema(df["close"], 9)
    df["ema_21"] = ema(df["close"], 21)
    df["sma_50"] = sma(df["close"], 50)

    # MACD
    macd_line, signal, hist = macd(df["close"])
    df["macd"] = macd_line
    df["macd_signal"] = signal
    df["macd_histogram"] = hist

    # Volatility
    df["atr_14"] = atr(df["high"], df["low"], df["close"], 14)
    df["adx_14"] = adx(df["high"], df["low"], df["close"], 14)

    # Price action
    df["vwap"] = vwap(df)
    upper, mid, lower = bollinger_bands(df["close"], 20, 2.0)
    df["bb_upper"] = upper
    df["bb_middle"] = mid
    df["bb_lower"] = lower

    # Returns-based
    df["returns"] = df["close"].pct_change()
    df["realized_vol"] = realized_volatility(df["returns"], 20)

    return df
