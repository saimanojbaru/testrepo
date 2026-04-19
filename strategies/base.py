"""Pluggable strategy interface — backtester, live engine, and discovery all consume this."""
from __future__ import annotations

from abc import ABC, abstractmethod
from dataclasses import dataclass
from enum import Enum

import pandas as pd


class SignalDirection(str, Enum):
    LONG = "LONG"
    SHORT = "SHORT"
    FLAT = "FLAT"


@dataclass(frozen=True)
class Signal:
    direction: SignalDirection
    stop_loss_pct: float | None = None
    take_profit_pct: float | None = None
    confidence: float = 1.0


class Strategy(ABC):
    """Base class for rule-based or learned strategies.

    `prepare` runs once over the full dataframe to precompute features (vectorized).
    `on_bar` is called for each bar during backtest/live; returns None or a Signal.
    """

    name: str = "unnamed"

    def prepare(self, df: pd.DataFrame) -> pd.DataFrame:
        """Override to precompute features. Default: no-op."""
        return df

    @abstractmethod
    def on_bar(self, bar: pd.Series, history: pd.DataFrame) -> Signal | None:
        ...
