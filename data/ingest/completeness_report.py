"""Measure data completeness vs the 5-year intraday requirement.

Decision gate: if free-source coverage is < 80%, escalate to paid vendor (TrueData/GDFL).
Report prints a machine-parseable block ending with `go_paid=True|False`.
"""
from __future__ import annotations

import argparse
import datetime as dt
from dataclasses import dataclass
from pathlib import Path

import pandas as pd

from data.ingest.nse_bhavcopy import load_sample

TARGET_YEARS = 5
TRADING_DAYS_PER_YEAR = 252
BARS_PER_SESSION = 375  # 09:15 -> 15:30 inclusive
GAP_THRESHOLD = 0.20


@dataclass(frozen=True)
class CompletenessReport:
    symbol: str
    expected_bars: int
    observed_bars: int
    gap_ratio: float
    go_paid: bool

    def format(self) -> str:
        return (
            f"{self.symbol}: observed={self.observed_bars:,} "
            f"expected={self.expected_bars:,} "
            f"gap={self.gap_ratio:.1%} "
            f"go_paid={self.go_paid}"
        )


def analyze(
    minute_df: pd.DataFrame,
    target_years: int = TARGET_YEARS,
    gap_threshold: float = GAP_THRESHOLD,
) -> list[CompletenessReport]:
    """Compare observed 1-min bar count per symbol vs a 5-year target."""
    expected = target_years * TRADING_DAYS_PER_YEAR * BARS_PER_SESSION
    reports: list[CompletenessReport] = []
    for symbol, group in minute_df.groupby("symbol"):
        observed = len(group)
        gap = max(0.0, 1.0 - observed / expected)
        reports.append(
            CompletenessReport(
                symbol=str(symbol),
                expected_bars=expected,
                observed_bars=observed,
                gap_ratio=gap,
                go_paid=gap > gap_threshold,
            )
        )
    return reports


def cli(argv: list[str] | None = None) -> int:
    p = argparse.ArgumentParser(prog="scalp-completeness")
    p.add_argument("--minute-csv", type=Path, help="1-min CSV; default=fixture")
    p.add_argument("--years", type=int, default=TARGET_YEARS)
    p.add_argument("--threshold", type=float, default=GAP_THRESHOLD)
    args = p.parse_args(argv)

    if args.minute_csv:
        minute_df = pd.read_csv(args.minute_csv, parse_dates=["ts"])
    else:
        # Fall back to bundled 1-min fixture
        fixture = Path(__file__).resolve().parents[1] / "fixtures" / "ohlcv_1m_sample.csv"
        minute_df = pd.read_csv(fixture, parse_dates=["ts"])

    reports = analyze(minute_df, target_years=args.years, gap_threshold=args.threshold)
    print(f"# completeness report — target {args.years}y @ {BARS_PER_SESSION} bars/session")
    print(f"# generated {dt.date.today().isoformat()}")
    any_paid = False
    for r in reports:
        print(r.format())
        any_paid |= r.go_paid
    print(f"# overall_go_paid={any_paid}")
    return 0 if not any_paid else 10  # non-zero exit flags escalation


if __name__ == "__main__":
    raise SystemExit(cli())
