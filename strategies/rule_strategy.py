"""Concrete rule-based Strategy built from a RuleRecord. Single-feature + threshold."""
from __future__ import annotations

from dataclasses import dataclass

import pandas as pd

from features.technical import bollinger, macd, rsi, vwap
from strategies.base import Signal, SignalDirection, Strategy
from strategies.registry import RuleRecord


@dataclass
class RuleStrategy(Strategy):
    rule: RuleRecord

    def __post_init__(self) -> None:
        self.name = f"rule_{self.rule.feature}_{self.rule.entry_op}_{self.rule.entry_threshold}"

    def prepare(self, df: pd.DataFrame) -> pd.DataFrame:
        df = df.copy()
        if "rsi" in self.rule.feature:
            df["rsi"] = rsi(df["close"], window=14)
        if self.rule.feature.startswith("macd"):
            m = macd(df["close"])
            df["macd"] = m["macd"]
            df["macd_hist"] = m["histogram"]
        if self.rule.feature.startswith("bollinger"):
            b = bollinger(df["close"], window=20, stds=2.0)
            df["bollinger_lower"] = b["lower"]
            df["bollinger_upper"] = b["upper"]
            df["bollinger_mid"] = b["mid"]
        if self.rule.feature == "vwap_gap":
            df["vwap_gap"] = df["close"] - vwap(df)
        return df

    def on_bar(self, bar: pd.Series, history: pd.DataFrame) -> Signal | None:
        col = self._column()
        val = bar.get(col)
        if val is None or pd.isna(val):
            return None
        fire = False
        op = self.rule.entry_op
        thr = self.rule.entry_threshold

        # Bollinger rules compare close to the band, not the band to a threshold.
        if self.rule.feature == "bollinger_lower":
            close = float(bar["close"])
            if op == "<" and close < val:  # close below lower band (oversold)
                fire = True
        elif self.rule.feature == "bollinger_upper":
            close = float(bar["close"])
            if op == ">" and close > val:  # close above upper band (overbought)
                fire = True
        elif op == "<" and val < thr:
            fire = True
        elif op == ">" and val > thr:
            fire = True
        elif op in {"cross_above", "cross_below"} and len(history) >= 2:
            prev = history.iloc[-2].get(col)
            if pd.isna(prev):
                return None
            if op == "cross_above" and prev <= thr < val:
                fire = True
            elif op == "cross_below" and prev >= thr > val:
                fire = True
        if not fire:
            return None

        # Direction mapping
        if self.rule.feature == "bollinger_lower":
            direction = SignalDirection.LONG     # mean-reversion buy
        elif self.rule.feature == "bollinger_upper":
            direction = SignalDirection.SHORT    # mean-reversion sell
        else:
            direction = SignalDirection.LONG if op in {">", "cross_above"} else SignalDirection.SHORT
        return Signal(
            direction=direction,
            stop_loss_pct=self.rule.stop_loss_pct,
            take_profit_pct=self.rule.take_profit_pct,
        )

    def _column(self) -> str:
        # map the rule's feature label to the df column name produced in prepare
        mapping = {
            "rsi": "rsi",
            "macd": "macd",
            "macd_hist": "macd_hist",
            "bollinger_lower": "bollinger_lower",
            "bollinger_upper": "bollinger_upper",
            "vwap_gap": "vwap_gap",
        }
        return mapping.get(self.rule.feature, self.rule.feature)
