"""Claude-powered analyst.

Claude is used ONLY to explain results — never to place trades or modify
strategies. The system prompt pins it to data-grounded commentary:
  - no hallucinated numbers (we pass exact metrics in context)
  - no forward-looking decisions
  - structured output: best/worst, why, common failure modes, tuning ideas
"""

from __future__ import annotations

import json
import textwrap
from dataclasses import dataclass
from typing import Iterable

from loguru import logger

from ..backtest.metrics import Metrics
from ..config import settings
from ..domain.trades import ClosedTrade

try:
    from anthropic import Anthropic  # type: ignore
except Exception:  # pragma: no cover
    Anthropic = None  # type: ignore


DEFAULT_SYSTEM = """You are a quantitative trading analyst. Your job is to explain the
results of today's scalping strategy backtests to a human trader.

STRICT RULES:
- Ground every statement in the numbers you are given. Do not invent trades,
  dates, or metrics.
- Do NOT recommend placing or removing real orders.
- Do NOT rewrite strategies. You may suggest parameter tuning ideas in plain
  English, but the final call stays with the trader.
- Output must follow the sections listed in the user message.
- Keep it under 400 words.
"""


@dataclass(slots=True)
class AnalystReport:
    markdown: str
    model: str
    stub: bool


class ClaudeAnalyst:
    def __init__(self, api_key: str | None = None, model: str | None = None) -> None:
        self.api_key = api_key or settings.anthropic_api_key
        self.model = model or settings.claude_model

    @property
    def configured(self) -> bool:
        return bool(self.api_key and Anthropic is not None)

    def analyze(
        self,
        metrics: list[Metrics],
        trades_by_strategy: dict[str, list[ClosedTrade]] | None = None,
        *,
        system_prompt: str | None = None,
        user_prompt_override: str | None = None,
    ) -> AnalystReport:
        if not self.configured:
            md = self._heuristic_fallback(metrics, trades_by_strategy or {})
            return AnalystReport(markdown=md, model="heuristic", stub=True)
        return self._ask_claude(
            metrics,
            trades_by_strategy or {},
            system_prompt=system_prompt,
            user_prompt_override=user_prompt_override,
        )

    def analyze_freeform(
        self,
        *,
        system_prompt: str,
        user_prompt: str,
    ) -> AnalystReport:
        """Drive Claude with arbitrary prompts (used by reconciliation persona)."""
        if not self.configured:
            return AnalystReport(
                markdown="_ANTHROPIC_API_KEY not set — set it for AI commentary._",
                model="heuristic",
                stub=True,
            )
        client = Anthropic(api_key=self.api_key)
        resp = client.messages.create(
            model=self.model,
            max_tokens=1024,
            system=system_prompt,
            messages=[{"role": "user", "content": user_prompt}],
        )
        text = "".join(block.text for block in resp.content if block.type == "text")
        return AnalystReport(markdown=text, model=self.model, stub=False)

    def _ask_claude(
        self,
        metrics: list[Metrics],
        trades: dict[str, list[ClosedTrade]],
        *,
        system_prompt: str | None = None,
        user_prompt_override: str | None = None,
    ) -> AnalystReport:
        client = Anthropic(api_key=self.api_key)
        summary = _metrics_summary(metrics, trades)
        user = user_prompt_override or textwrap.dedent(
            f"""
            Analyze today's scalping strategy results and produce:
              1. **Best & worst**: name them and explain why with specific numbers.
              2. **Failure modes**: identify any of {{false breakouts, overtrading,
                 poor risk-reward, regime mismatch}} that apply. Cite numbers.
              3. **Tuning ideas**: 2–4 bullet points. Concrete filters or
                 parameter adjustments. Mark each as LOW / MEDIUM / HIGH risk.
              4. **Skip rules**: when should the trader avoid these strategies?

            DATA:
            {summary}
            """
        ).strip()
        resp = client.messages.create(
            model=self.model,
            max_tokens=1024,
            system=system_prompt or DEFAULT_SYSTEM,
            messages=[{"role": "user", "content": user}],
        )
        text = "".join(block.text for block in resp.content if block.type == "text")
        return AnalystReport(markdown=text, model=self.model, stub=False)

    def _heuristic_fallback(
        self,
        metrics: list[Metrics],
        trades: dict[str, list[ClosedTrade]],
    ) -> str:
        if not metrics:
            return "_No trades produced today — nothing to analyze._"
        best = max(metrics, key=lambda m: m.profit_factor)
        worst = min(metrics, key=lambda m: m.profit_factor)
        lines = [
            f"**Best**: `{best.strategy}` (PF {best.profit_factor}, PnL ₹{best.net_pnl}, "
            f"win rate {best.win_rate * 100:.1f}%).",
            f"**Worst**: `{worst.strategy}` (PF {worst.profit_factor}, PnL ₹{worst.net_pnl}).",
        ]
        for m in metrics:
            flags = _diagnose(m, trades.get(m.strategy, []))
            if flags:
                lines.append(f"- `{m.strategy}` flags: {', '.join(flags)}")
        lines.append(
            "_Tuning ideas unavailable — set ANTHROPIC_API_KEY for Claude commentary._"
        )
        return "\n".join(lines)


def _metrics_summary(
    metrics: list[Metrics], trades: dict[str, list[ClosedTrade]]
) -> str:
    body = [m.to_dict() for m in metrics]
    for b in body:
        t = trades.get(b["strategy"], [])
        b["sample_trades"] = [t.to_dict() for t in t[:5]]
    return json.dumps(body, indent=2)


def _diagnose(m: Metrics, trades: Iterable[ClosedTrade]) -> list[str]:
    flags: list[str] = []
    if m.total_trades >= 20 and m.win_rate < 0.35:
        flags.append("low win rate — false breakouts?")
    if m.total_trades >= 40:
        flags.append("overtrading (>40 trades/day)")
    if m.avg_profit < abs(m.avg_loss):
        flags.append("poor risk-reward (avg loss > avg profit)")
    if m.max_drawdown > 1000 and m.net_pnl < m.max_drawdown:
        flags.append("drawdown exceeds profits")
    _ = trades
    return flags
