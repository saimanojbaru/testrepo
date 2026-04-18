"""
Unified data ingestion runner.
Fetches from all free sources and reports completeness.

Usage:
    python -m data.ingest.runner --symbols NIFTY,BANKNIFTY --years 5
"""

import argparse
from datetime import datetime, timedelta
from pathlib import Path
import pandas as pd

from data.ingest.nse_bhavcopy import NSEBhavcopyIngester
from data.ingest.yfinance_loader import YahooFinanceLoader
from data.ingest.upstox_historical import UpstoxHistoricalLoader


def run_ingestion(symbols: list, years: int = 5, output_dir: str = "data/processed"):
    """
    Run full ingestion pipeline across all free sources.

    Args:
        symbols: e.g. ['NIFTY', 'BANKNIFTY']
        years: How many years of history to fetch
        output_dir: Where to save processed parquet files
    """
    out = Path(output_dir)
    out.mkdir(parents=True, exist_ok=True)

    end = datetime.now()
    start = end - timedelta(days=years * 365)

    report = {}

    # 1. NSE Bhavcopy (daily F&O EOD)
    print("\n=== Source 1: NSE F&O Bhavcopy (daily EOD) ===")
    nse = NSEBhavcopyIngester()
    nse_df = nse.fetch_range(start, end, "fo", symbols)
    if not nse_df.empty:
        nse_path = out / "nse_fo_bhavcopy.parquet"
        nse_df.to_parquet(nse_path, index=False)
        report["nse_fo"] = {"rows": len(nse_df), "path": str(nse_path)}
        print(f"  Saved {len(nse_df)} rows → {nse_path}")
    else:
        report["nse_fo"] = {"rows": 0, "status": "failed"}

    # 2. Yahoo Finance (daily index + recent intraday)
    print("\n=== Source 2: Yahoo Finance (daily index) ===")
    yf = YahooFinanceLoader()
    yf_daily = yf.fetch_5_year_daily(symbols)
    if not yf_daily.empty:
        yf_path = out / "yf_daily.parquet"
        yf_daily.to_parquet(yf_path, index=False)
        report["yf_daily"] = {"rows": len(yf_daily), "path": str(yf_path)}
        print(f"  Saved {len(yf_daily)} rows → {yf_path}")

    print("\n=== Source 3: Yahoo Finance (recent intraday 5m, ~60 days) ===")
    yf_intraday = yf.fetch_recent_intraday(symbols, days=60, interval="5m")
    if not yf_intraday.empty:
        yf_path = out / "yf_intraday_5m.parquet"
        yf_intraday.to_parquet(yf_path, index=False)
        report["yf_intraday"] = {"rows": len(yf_intraday), "path": str(yf_path)}
        print(f"  Saved {len(yf_intraday)} rows → {yf_path}")

    # 3. Upstox (requires access token; skipped if not provided)
    print("\n=== Source 4: Upstox Historical (requires access token) ===")
    from config.settings import settings
    # Upstox needs a live access token; skip if not configured
    try:
        if hasattr(settings, 'upstox_access_token') and settings.upstox_access_token:
            upstox = UpstoxHistoricalLoader(access_token=settings.upstox_access_token)
            ux_daily = upstox.fetch_5_year_daily(symbols)
            if not ux_daily.empty:
                ux_path = out / "upstox_daily.parquet"
                ux_daily.to_parquet(ux_path, index=False)
                report["upstox_daily"] = {"rows": len(ux_daily), "path": str(ux_path)}
        else:
            print("  [Upstox] Skipped (no access token configured)")
    except Exception as e:
        print(f"  [Upstox] Skipped: {e}")

    # Report
    print("\n=== Ingestion Report ===")
    for source, info in report.items():
        print(f"  {source}: {info}")

    total_rows = sum(info.get("rows", 0) for info in report.values())
    print(f"\nTotal rows across sources: {total_rows:,}")

    # Completeness check
    expected_trading_days = years * 250  # Approximate
    if "nse_fo" in report:
        days_covered = len(pd.unique(nse_df["trade_date"])) if not nse_df.empty else 0
        coverage = days_covered / expected_trading_days * 100
        print(f"\nNSE F&O coverage: {days_covered}/{expected_trading_days} days ({coverage:.1f}%)")

        if coverage < 80:
            print("  ⚠  WARNING: <80% coverage. Consider escalating to paid vendor (TrueData/GDFL).")
        else:
            print("  ✓  Good coverage from free sources.")

    return report


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--symbols", default="NIFTY,BANKNIFTY")
    parser.add_argument("--years", type=int, default=5)
    parser.add_argument("--output", default="data/processed")
    args = parser.parse_args()

    symbols = args.symbols.split(",")
    run_ingestion(symbols, args.years, args.output)
