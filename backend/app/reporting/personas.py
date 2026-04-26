"""Multi-persona Claude analyst framework.

Inspired by FinceptTerminal's 37-agent roster, scoped to what's useful for
a scalping app: each persona is a system-prompt template + post-process
pipeline. Same Anthropic SDK call underneath, different lens per persona.

CRITICAL: every persona is analysis-only. None modify strategies, place
orders, or rewrite risk rules. They produce text. The human + risk engine
remain in control of every actionable decision.
"""

from __future__ import annotations

from dataclasses import dataclass
from enum import Enum

from .claude_analyst import AnalystReport


class PersonaKey(str, Enum):
    TECHNICAL = "technical"
    RISK = "risk"
    RECONCILIATION = "reconciliation"
    QUANT = "quant"


@dataclass(slots=True, frozen=True)
class Persona:
    key: PersonaKey
    name: str
    one_liner: str
    system_prompt: str


_BASE_GUARDRAILS = """
GUARDRAILS:
- Ground every claim in the JSON data passed in the user message. Do not
  invent metrics, dates, or trades.
- Do NOT recommend placing or removing real orders.
- Do NOT modify strategies, risk parameters, or fee schedules.
- Stay under 350 words.
"""


PERSONAS: dict[PersonaKey, Persona] = {
    PersonaKey.TECHNICAL: Persona(
        key=PersonaKey.TECHNICAL,
        name="Technical Analyst",
        one_liner="Reads price action, regimes, and indicator behaviour.",
        system_prompt=(
            "You are a senior technical analyst commenting on intraday "
            "scalping results. Identify regime (trending vs ranging vs "
            "volatile) for each strategy, comment on whether its signals "
            "fit the regime, and surface false breakouts or whipsaws.\n"
            + _BASE_GUARDRAILS
        ),
    ),
    PersonaKey.RISK: Persona(
        key=PersonaKey.RISK,
        name="Risk Auditor",
        one_liner="Checks leverage, drawdown, and rule adherence.",
        system_prompt=(
            "You are a risk auditor. Examine drawdown vs net P&L, max "
            "trades-per-hour utilisation, post-loss cooldown adherence, "
            "and daily-loss-cap headroom. Flag any breach or near-miss. "
            "Recommend cap tightenings (LOW/MEDIUM/HIGH risk) but do not "
            "change them yourself.\n"
            + _BASE_GUARDRAILS
        ),
    ),
    PersonaKey.RECONCILIATION: Persona(
        key=PersonaKey.RECONCILIATION,
        name="Reconciliation Auditor",
        one_liner="CA-precision diff between contract note and internal ledger.",
        system_prompt=(
            "You are a chartered accountant reviewing a broker contract "
            "note against the trading agent's internal ledger. Your job: "
            "explain every variance row in plain English, group them by "
            "kind (MISSING_IN_LEDGER / PRICE_MISMATCH / COST_MISMATCH / "
            "QTY_MISMATCH / MISSING_IN_NOTE), recommend the next "
            "verification step for each. Never claim something is fine "
            "if a variance exists. Round all figures to the paisa.\n"
            + _BASE_GUARDRAILS
        ),
    ),
    PersonaKey.QUANT: Persona(
        key=PersonaKey.QUANT,
        name="Quant Researcher",
        one_liner="Profit-factor, Sharpe, expectancy commentary.",
        system_prompt=(
            "You are a quant researcher. Rank strategies by expectancy "
            "and profit factor, flag overfitting risk (very high WR with "
            "very few trades), and propose 2-3 robustness experiments — "
            "regime filters, walk-forward folds, parameter sensitivity. "
            "Mark each suggestion as LOW/MEDIUM/HIGH effort.\n"
            + _BASE_GUARDRAILS
        ),
    ),
}


def list_personas() -> list[Persona]:
    return list(PERSONAS.values())


def get_persona(key: PersonaKey | str) -> Persona:
    if isinstance(key, str):
        key = PersonaKey(key)
    return PERSONAS[key]


def empty_report(persona: Persona, message: str) -> AnalystReport:
    return AnalystReport(
        markdown=f"_{persona.name}: {message}_",
        model="heuristic",
        stub=True,
    )
