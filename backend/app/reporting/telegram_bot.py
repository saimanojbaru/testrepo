"""Telegram bot for hourly / daily performance reports.

No-op if TELEGRAM_BOT_TOKEN or TELEGRAM_CHAT_ID are not configured — so local
development and tests never accidentally DM someone.
"""

from __future__ import annotations

import httpx
from loguru import logger

from ..backtest.metrics import Metrics
from ..config import settings
from ..lab.runner import LabResult


class TelegramReporter:
    API = "https://api.telegram.org/bot{token}/sendMessage"

    def __init__(
        self,
        token: str | None = None,
        chat_id: str | None = None,
    ) -> None:
        self.token = token or settings.telegram_bot_token
        self.chat_id = chat_id or settings.telegram_chat_id

    @property
    def configured(self) -> bool:
        return bool(self.token and self.chat_id)

    async def send(self, text: str) -> None:
        if not self.configured:
            logger.debug("telegram not configured; skipping: %s", text[:80])
            return
        async with httpx.AsyncClient(timeout=10) as client:
            r = await client.post(
                self.API.format(token=self.token),
                json={
                    "chat_id": self.chat_id,
                    "text": text,
                    "parse_mode": "Markdown",
                    "disable_web_page_preview": True,
                },
            )
            r.raise_for_status()

    async def hourly(self, metrics: list[Metrics]) -> None:
        if not metrics:
            return
        best = max(metrics, key=lambda m: (m.profit_factor, m.net_pnl))
        await self.send(hourly_markdown(best, metrics))

    async def daily(
        self,
        result: LabResult,
        claude_analysis: str | None = None,
    ) -> None:
        ranking = result.ranked()
        await self.send(daily_markdown(ranking, claude_analysis))


def hourly_markdown(best: Metrics, metrics: list[Metrics]) -> str:
    lines = [
        "*Hourly update*",
        f"⭐ Best: *{best.strategy}*",
        f"PnL ₹{best.net_pnl}  ·  WR {best.win_rate * 100:.1f}%  ·  PF {best.profit_factor}",
        "",
    ]
    for m in metrics:
        arrow = "🟢" if m.net_pnl >= 0 else "🔴"
        lines.append(
            f"{arrow} `{m.strategy}` ₹{m.net_pnl}  ({m.total_trades} trades, "
            f"PF {m.profit_factor})"
        )
    return "\n".join(lines)


def daily_markdown(metrics: list[Metrics], claude_analysis: str | None) -> str:
    if not metrics:
        return "No trades today."
    best = metrics[0]
    worst = metrics[-1]
    lines = [
        "*Daily scalping report*",
        "",
        f"🥇 Best: *{best.strategy}* · ₹{best.net_pnl} (PF {best.profit_factor})",
        f"🥶 Worst: *{worst.strategy}* · ₹{worst.net_pnl} (PF {worst.profit_factor})",
        "",
        "*Ranking*",
    ]
    for i, m in enumerate(metrics, 1):
        lines.append(
            f"{i}. `{m.strategy}` — ₹{m.net_pnl} · WR {m.win_rate * 100:.1f}% "
            f"· DD ₹{m.max_drawdown} · {m.total_trades} trades"
        )
    if claude_analysis:
        lines += ["", "*Analyst notes*", claude_analysis]
    return "\n".join(lines)
