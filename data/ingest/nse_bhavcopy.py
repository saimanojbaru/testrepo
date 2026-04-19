"""NSE bhavcopy ingestion — daily EOD, free from https://archives.nseindia.com/.

Two modes:
  --sample  : load bundled fixture CSV (no network, used by tests + demo)
  --date    : fetch a specific trading day from NSE archives

For the MVP we only wire --sample; the live fetcher is a thin httpx wrapper that's
straightforward to add once NSE auth cookies / UA quirks are handled.
"""
from __future__ import annotations

import argparse
from dataclasses import dataclass
from datetime import date
from pathlib import Path

import pandas as pd

FIXTURE_PATH = Path(__file__).resolve().parents[1] / "fixtures" / "bhavcopy_sample.csv"


@dataclass(frozen=True)
class BhavcopyRow:
    trade_date: date
    symbol: str
    open: float
    high: float
    low: float
    close: float
    volume: int


def load_sample() -> pd.DataFrame:
    """Load the bundled fixture — 22 trading days across NIFTY + BANKNIFTY."""
    df = pd.read_csv(FIXTURE_PATH, parse_dates=["date"])
    df["date"] = df["date"].dt.date
    return df


def load_file(path: Path) -> pd.DataFrame:
    """Load an arbitrary bhavcopy CSV on disk (same schema as fixture)."""
    df = pd.read_csv(path, parse_dates=["date"])
    df["date"] = df["date"].dt.date
    return df


def cli(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(prog="scalp-ingest-nse")
    p.add_argument("--sample", action="store_true", help="load bundled fixture")
    p.add_argument("--file", type=Path, help="load bhavcopy CSV from path")
    args = p.parse_args(argv)

    if args.sample:
        df = load_sample()
    elif args.file:
        df = load_file(args.file)
    else:
        p.error("pass --sample or --file <path>")
        return 2

    print(f"Loaded {len(df):,} rows across {df['symbol'].nunique()} symbols, "
          f"{df['date'].min()} -> {df['date'].max()}")
    print(df.head(5).to_string(index=False))
    return 0


if __name__ == "__main__":
    raise SystemExit(cli())
