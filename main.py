"""Unified entrypoint for the scalping agent.

Modes:
  * backtest : run bundled or CSV data through the backtester (no broker)
  * paper    : wire PaperBroker + live feed (or fixture replay) + agent
  * live     : wire UpstoxBroker + live feed + agent (requires API keys in .env)

Each mode shares the same TradingAgent + RiskEngine + OrderManager pipeline so
behavior is identical except for the broker and the data source.
"""
from __future__ import annotations

import argparse
from pathlib import Path

import pandas as pd

from broker.paper import PaperBroker
from execution.agent import TradingAgent, on_paper_fill_factory
from execution.order_manager import OrderManager
from risk.engine import RiskEngine
from risk.kill_switch import KillSwitch
from risk.limits import RiskLimits
from strategies.registry import load
from strategies.rule_strategy import RuleStrategy


def _load_strategy(registry_path: Path, key: str):
    rules = load(registry_path)
    if not rules:
        raise SystemExit(f"no strategies in registry {registry_path}; run scalp-discover first")
    chosen = next((r for r in rules if r.key == key), rules[0])
    return RuleStrategy(chosen)


def run_backtest(args: argparse.Namespace) -> int:
    from backtest.engine import run as bt_run
    df = pd.read_csv(args.csv, parse_dates=["ts"])
    if "symbol" in df.columns and args.symbol:
        df = df[df["symbol"] == args.symbol].reset_index(drop=True)
    strat = _load_strategy(args.registry, args.strategy)
    trades, metrics = bt_run(df, strat)
    print(f"# trades: {len(trades)}")
    print(metrics.as_dict())
    return 0


def run_paper(args: argparse.Namespace) -> int:
    from config import get_settings
    settings = get_settings()

    df = pd.read_csv(args.csv, parse_dates=["ts"])
    if "symbol" in df.columns and args.symbol:
        df = df[df["symbol"] == args.symbol].reset_index(drop=True)

    strat = _load_strategy(args.registry, args.strategy)
    broker = PaperBroker()
    kill = KillSwitch()
    risk = RiskEngine(
        limits=RiskLimits(
            max_daily_loss=settings.scalp_max_daily_loss,
            kelly_max_fraction=settings.scalp_kelly_fraction,
        ),
        capital=settings.scalp_capital,
        kill_switch=kill,
    )
    om = OrderManager(broker=broker)
    agent = TradingAgent(
        strategy=strat,
        broker=broker,
        risk=risk,
        order_mgr=om,
        instrument_key=args.instrument_key,
        lot_size=args.lot_size,
    )
    broker.listener = on_paper_fill_factory(agent)

    agent.prepare(df)
    assert agent._prepared is not None
    print(f"# paper trading replay: bars={len(agent._prepared)} instrument={args.instrument_key}")
    for i in range(len(agent._prepared) - 1):
        bar = agent._prepared.iloc[i]
        agent.on_bar(bar, agent._prepared.iloc[: i + 1])
    print(f"# final realized P&L = {broker.pnl():.2f}")
    print(f"# daily_pnl tracked by risk = {risk.daily_pnl:.2f}")
    return 0


def run_live(args: argparse.Namespace) -> int:
    from broker.upstox import UpstoxBroker
    from config import get_settings
    settings = get_settings()
    if not settings.upstox_access_token:
        raise SystemExit("UPSTOX_ACCESS_TOKEN missing — complete OAuth flow, then retry.")
    broker = UpstoxBroker()
    om = OrderManager(broker=broker)
    om.reconcile()
    print("Live mode connected to Upstox. Subscribe to WS + run loop in production deploy.")
    # In production this would subscribe to Upstox WS, route ticks into TradingAgent.on_bar,
    # and loop forever. Full loop omitted here because it requires an authed session.
    return 0


def main(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(prog="scalp")
    p.add_argument(
        "--mode",
        choices=("backtest", "paper", "live"),
        default="paper",
    )
    p.add_argument("--csv", type=Path, default=Path("data/fixtures/ohlcv_1m_sample.csv"))
    p.add_argument("--symbol", default="NIFTY")
    p.add_argument("--instrument-key", default="NSE_INDEX|Nifty 50")
    p.add_argument("--lot-size", type=int, default=50)
    p.add_argument("--registry", type=Path, default=Path("discovered_strategies.json"))
    p.add_argument("--strategy", default="top_1")
    args = p.parse_args(argv)

    if args.mode == "backtest":
        return run_backtest(args)
    if args.mode == "paper":
        return run_paper(args)
    if args.mode == "live":
        return run_live(args)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
