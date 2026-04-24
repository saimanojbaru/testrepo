"""Performance dashboard endpoint — consumed by the Flutter Dashboard."""

from __future__ import annotations

from collections import defaultdict

from fastapi import APIRouter, Depends
from sqlalchemy.ext.asyncio import AsyncSession

from ..db.repositories import performance_since
from ..engine.signal_engine import SignalEngine
from .deps import get_engine, get_session

router = APIRouter()


@router.get("/performance")
async def performance(
    hours: int = 24,
    sess: AsyncSession = Depends(get_session),
    engine: SignalEngine = Depends(get_engine),
) -> dict:
    trades = await performance_since(sess, hours=hours)
    per_strategy: dict[str, dict] = defaultdict(
        lambda: {"net_pnl": 0.0, "trades": 0, "wins": 0}
    )
    total_net = 0.0
    for t in trades:
        s = per_strategy[t.strategy]
        s["net_pnl"] += t.net_pnl
        s["trades"] += 1
        if t.net_pnl > 0:
            s["wins"] += 1
        total_net += t.net_pnl

    return {
        "window_hours": hours,
        "total_trades": len(trades),
        "net_pnl": round(total_net, 2),
        "by_strategy": {
            name: {
                "net_pnl": round(v["net_pnl"], 2),
                "trades": v["trades"],
                "win_rate": round(v["wins"] / v["trades"], 4) if v["trades"] else 0.0,
            }
            for name, v in per_strategy.items()
        },
        "engine": engine.state(),
    }
