"""Meta-controller — picks the best strategy for the current regime.

Inputs:
  * RegimeClassifier (Phase 4) — labels the current bar.
  * A mapping {regime -> list[RuleRecord]} from Phase 3 discovery.
Output:
  * A single Strategy instance that will be consulted for the next signal.
"""
from __future__ import annotations

from dataclasses import dataclass
from typing import Mapping

import pandas as pd

from regime.classifier import RegimeClassifier, RegimeLabel
from strategies.base import Signal, Strategy
from strategies.registry import RuleRecord
from strategies.rule_strategy import RuleStrategy


@dataclass
class MetaController(Strategy):
    classifier: RegimeClassifier
    regime_to_rules: Mapping[RegimeLabel, list[RuleRecord]]
    fallback_rule: RuleRecord | None = None

    name: str = "meta_controller"

    def prepare(self, df: pd.DataFrame) -> pd.DataFrame:
        """Meta must prepare features for *every* rule it might route to, plus the
        regime classifier's feature matrix.
        """
        from regime.classifier import build_feature_matrix
        feat = build_feature_matrix(df)
        out = df.copy()
        for col in feat.columns:
            out[col] = feat[col]
        # Run through every child strategy's prepare to accumulate indicators
        rules: list[RuleRecord] = list(self.regime_to_rules.values()[0] if self.regime_to_rules else [])
        seen: set[str] = set()
        for rules_for_regime in self.regime_to_rules.values():
            for r in rules_for_regime:
                if r.feature in seen:
                    continue
                seen.add(r.feature)
                sub = RuleStrategy(r).prepare(out)
                # copy any new cols
                for col in sub.columns:
                    if col not in out.columns:
                        out[col] = sub[col]
        if self.fallback_rule and self.fallback_rule.feature not in seen:
            sub = RuleStrategy(self.fallback_rule).prepare(out)
            for col in sub.columns:
                if col not in out.columns:
                    out[col] = sub[col]
        return out

    def on_bar(self, bar: pd.Series, history: pd.DataFrame) -> Signal | None:
        regime = self.classifier.predict_bar(bar)
        rules = list(self.regime_to_rules.get(regime, []))
        if not rules:
            if self.fallback_rule is None:
                return None
            rules = [self.fallback_rule]

        # Consult the top-ranked rule for that regime.
        top = rules[0]
        child = RuleStrategy(top)
        return child.on_bar(bar, history)
