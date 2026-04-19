"""Regime features — used by discovery + (Phase 4) regime classifier."""
from __future__ import annotations

import numpy as np
import pandas as pd

from .technical import adx, atr


def realized_vol(series: pd.Series, window: int = 30) -> pd.Series:
    """Annualized realized vol from log returns (session-agnostic)."""
    log_ret = np.log(series).diff()
    return log_ret.rolling(window, min_periods=window).std() * np.sqrt(252 * 375)


def time_of_day_bucket(ts: pd.Series) -> pd.Series:
    """NSE F&O session buckets: opening_drive, morning, midday, afternoon, closing_auction."""

    def classify(t: pd.Timestamp) -> str:
        m = t.hour * 60 + t.minute
        if m < 9 * 60 + 30:
            return "opening_drive"
        if m < 11 * 60 + 0:
            return "morning"
        if m < 13 * 60 + 30:
            return "midday"
        if m < 15 * 60 + 0:
            return "afternoon"
        return "closing_auction"

    return ts.apply(classify)


def is_expiry_day(ts: pd.Series, expiries: pd.Series) -> pd.Series:
    expiry_dates = set(pd.to_datetime(expiries).dt.date.tolist())
    return ts.dt.date.isin(expiry_dates)


def trend_strength(df: pd.DataFrame, window: int = 14) -> pd.Series:
    """Short-hand regime label feature: ADX value; > 25 = trending."""
    return adx(df, window)


def vol_regime(df: pd.DataFrame, window: int = 14) -> pd.Series:
    """ATR normalized by close — compares current vol to price scale."""
    return atr(df, window) / df["close"]
