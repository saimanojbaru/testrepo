"""Load/save discovered strategy rules from disk."""
from __future__ import annotations

import json
from dataclasses import asdict, dataclass
from pathlib import Path
from typing import Any

REGISTRY_DEFAULT_PATH = Path("discovered_strategies.json")


@dataclass(frozen=True)
class RuleRecord:
    key: str
    feature: str           # 'rsi', 'macd_hist', 'bollinger_lower', ...
    entry_op: str          # '<', '>', 'cross_above', 'cross_below'
    entry_threshold: float
    exit_bars: int
    stop_loss_pct: float
    take_profit_pct: float
    sharpe_net: float
    net_pnl: float
    trades: int
    fold_metrics: list[dict[str, Any]]

    def to_dict(self) -> dict[str, Any]:
        return asdict(self)


def save(rules: list[RuleRecord], path: Path = REGISTRY_DEFAULT_PATH) -> None:
    payload = {"strategies": [r.to_dict() for r in rules]}
    path.write_text(json.dumps(payload, indent=2, default=str))


def load(path: Path = REGISTRY_DEFAULT_PATH) -> list[RuleRecord]:
    if not path.exists():
        return []
    payload = json.loads(path.read_text())
    return [RuleRecord(**r) for r in payload.get("strategies", [])]
