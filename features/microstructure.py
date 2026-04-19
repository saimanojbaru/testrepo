"""Microstructure features — spread, imbalance, quote dynamics."""
from __future__ import annotations

import numpy as np
import pandas as pd


def bid_ask_spread(bid: pd.Series, ask: pd.Series) -> pd.Series:
    return ask - bid


def relative_spread(bid: pd.Series, ask: pd.Series) -> pd.Series:
    mid = (bid + ask) / 2.0
    return (ask - bid) / mid.replace(0.0, np.nan)


def l1_imbalance(bid_qty: pd.Series, ask_qty: pd.Series) -> pd.Series:
    """Classic L1 order-book imbalance in [-1, 1].

    Positive = buy pressure (bid size > ask size), negative = sell pressure.
    """
    total = bid_qty + ask_qty
    return (bid_qty - ask_qty) / total.replace(0.0, np.nan)


def mid_price(bid: pd.Series, ask: pd.Series) -> pd.Series:
    return (bid + ask) / 2.0


def micro_price(bid: pd.Series, ask: pd.Series, bid_qty: pd.Series, ask_qty: pd.Series) -> pd.Series:
    """Weighted mid — size-adjusted, leans toward the heavier side of the book."""
    total = bid_qty + ask_qty
    w_ask = bid_qty / total.replace(0.0, np.nan)
    w_bid = ask_qty / total.replace(0.0, np.nan)
    return w_bid * bid + w_ask * ask
