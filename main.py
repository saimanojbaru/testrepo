"""
Main entry point for the scalping agent.

Usage:
    python main.py --mode backtest
    python main.py --mode paper
    python main.py --mode discover      # Run strategy discovery
    python main.py --mode ingest        # Run data ingestion
"""

import argparse
import logging
import sys


def setup_logging():
    logging.basicConfig(
        level=logging.INFO,
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    )


def mode_ingest(args):
    """Run data ingestion pipeline."""
    from data.ingest.runner import run_ingestion
    symbols = args.symbols.split(",") if args.symbols else ["NIFTY", "BANKNIFTY"]
    run_ingestion(symbols, years=args.years)


def mode_discover(args):
    """Run strategy discovery on historical data."""
    import pandas as pd
    from pathlib import Path
    from strategies.discover import StrategyDiscoverer
    from features.technical import add_technical_features

    data_path = Path(args.data_path or "data/processed/yf_daily.parquet")
    if not data_path.exists():
        print(f"ERROR: Data file not found: {data_path}")
        print("Run: python main.py --mode ingest first")
        sys.exit(1)

    df = pd.read_parquet(data_path)
    df = add_technical_features(df)
    df = df.dropna().reset_index(drop=True)

    print(f"Loaded {len(df)} bars with {len(df.columns)} features")

    discoverer = StrategyDiscoverer(df)
    discovered = discoverer.discover_optuna(n_trials=args.n_trials)

    print(f"\nDiscovered {len(discovered)} strategies passing gate")
    for i, s in enumerate(discovered[:5]):
        print(f"\n  #{i+1}: {s.name}")
        print(f"    Params: {s.params}")
        print(f"    Avg Sharpe: {s.avg_test_sharpe:.2f}")
        print(f"    Trades: {s.total_trades}")
        print(f"    Net P&L: ₹{s.net_pnl:.0f}")

    discoverer.save_discovered(discovered)


def mode_backtest(args):
    """Run a backtest with discovered strategies."""
    print("Backtest mode — not yet implemented end-to-end.")
    print("Use strategies/discover.py directly for now.")


def mode_paper(args):
    """Run paper trading against live quotes."""
    print("Paper trading mode.")
    print("Requires: Upstox access token, TimescaleDB running, discovered_strategies.json")
    print("Not yet fully wired — stubs are in execution/agent.py")


def main():
    parser = argparse.ArgumentParser(description="Scalping Agent for Indian F&O")
    parser.add_argument("--mode", required=True, choices=["ingest", "discover", "backtest", "paper"])
    parser.add_argument("--symbols", default="NIFTY,BANKNIFTY")
    parser.add_argument("--years", type=int, default=5)
    parser.add_argument("--n-trials", type=int, default=50)
    parser.add_argument("--data-path", default=None)
    args = parser.parse_args()

    setup_logging()

    dispatch = {
        "ingest": mode_ingest,
        "discover": mode_discover,
        "backtest": mode_backtest,
        "paper": mode_paper,
    }
    dispatch[args.mode](args)


if __name__ == "__main__":
    main()
