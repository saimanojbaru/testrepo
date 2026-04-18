"""
Base Strategy interface.
All strategies (rules, ML, RL, hybrid) implement this contract.
"""

from abc import ABC, abstractmethod
from dataclasses import dataclass
from typing import Optional, Literal
import pandas as pd


@dataclass
class Signal:
    """Signal emitted by a strategy."""
    timestamp: pd.Timestamp
    action: Literal["buy", "sell", "hold"]
    confidence: float  # 0..1
    target_price: Optional[float] = None
    stop_loss: Optional[float] = None
    take_profit: Optional[float] = None
    strategy_name: str = ""
    metadata: dict = None


@dataclass
class Context:
    """Context passed to strategy on each bar."""
    df: pd.DataFrame           # All bars up to and including current
    current_index: int          # Index of current bar
    position: Optional[dict] = None   # Current open position, if any
    regime: Optional[str] = None       # Current regime label
    capital: float = 100000


class Strategy(ABC):
    """Abstract base class for all strategies."""

    def __init__(self, name: str, params: dict = None):
        self.name = name
        self.params = params or {}

    @abstractmethod
    def on_bar(self, ctx: Context) -> Optional[Signal]:
        """
        Called on each bar. Return Signal or None.
        """
        pass

    def reset(self):
        """Reset any internal state at start of new backtest."""
        pass

    def __repr__(self):
        return f"{self.__class__.__name__}(name={self.name}, params={self.params})"


class ParametricRuleStrategy(Strategy):
    """
    A rule strategy defined by a parametric function.
    Used by the strategy discovery engine (Optuna).

    Params include:
    - feature_name: str (e.g. 'rsi_14')
    - entry_threshold: float
    - exit_threshold: float
    - direction: 'long' or 'short'
    - holding_period: int (max bars to hold)
    """

    def __init__(self, name: str, params: dict):
        super().__init__(name, params)
        self.bars_held = 0

    def on_bar(self, ctx: Context) -> Optional[Signal]:
        df = ctx.df
        i = ctx.current_index
        params = self.params

        feature = params.get("feature_name", "rsi_14")
        entry_thresh = params.get("entry_threshold", 30)
        exit_thresh = params.get("exit_threshold", 70)
        holding_period = params.get("holding_period", 10)

        if feature not in df.columns or i == 0:
            return None

        val = df.iloc[i].get(feature)
        if pd.isna(val):
            return None

        # Entry logic
        if ctx.position is None:
            if val < entry_thresh:
                self.bars_held = 0
                return Signal(
                    timestamp=df.iloc[i]["timestamp"],
                    action="buy",
                    confidence=0.7,
                    target_price=df.iloc[i]["close"],
                    strategy_name=self.name,
                )

        # Exit logic
        else:
            self.bars_held += 1
            if val > exit_thresh or self.bars_held >= holding_period:
                return Signal(
                    timestamp=df.iloc[i]["timestamp"],
                    action="sell",
                    confidence=0.7,
                    target_price=df.iloc[i]["close"],
                    strategy_name=self.name,
                )

        return None

    def reset(self):
        self.bars_held = 0
