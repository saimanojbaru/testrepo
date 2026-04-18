"""
NSE Bhavcopy ingestion (free, daily EOD).
Downloads historical F&O bhavcopies from NSE archives.

URL pattern: https://archives.nseindia.com/content/historical/DERIVATIVES/YYYY/MMM/fo{DDMMMYYYY}bhav.csv.zip
"""

import os
import io
import zipfile
import requests
import pandas as pd
from datetime import datetime, timedelta
from pathlib import Path
from typing import Optional, List


NSE_FO_BASE_URL = "https://archives.nseindia.com/content/historical/DERIVATIVES"
NSE_EQ_BASE_URL = "https://archives.nseindia.com/content/historical/EQUITIES"

HEADERS = {
    "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36",
    "Accept": "application/zip, text/csv",
    "Accept-Language": "en-US,en;q=0.9",
}


class NSEBhavcopyIngester:
    """
    Fetches daily F&O bhavcopies from NSE.
    Data includes: symbol, expiry, strike, option_type, open, high, low, close, settle, OI, volume.
    """

    def __init__(self, cache_dir: str = "data/raw/nse_bhavcopy"):
        self.cache_dir = Path(cache_dir)
        self.cache_dir.mkdir(parents=True, exist_ok=True)

    def _build_url(self, date: datetime, segment: str = "fo") -> str:
        """Build NSE bhavcopy URL for given date."""
        year = date.strftime("%Y")
        month = date.strftime("%b").upper()
        date_str = date.strftime("%d%b%Y").upper()

        if segment == "fo":
            return f"{NSE_FO_BASE_URL}/{year}/{month}/fo{date_str}bhav.csv.zip"
        else:
            return f"{NSE_EQ_BASE_URL}/{year}/{month}/cm{date_str}bhav.csv.zip"

    def _cache_path(self, date: datetime, segment: str) -> Path:
        """Local cache path for a given date."""
        return self.cache_dir / f"{segment}_{date.strftime('%Y%m%d')}.csv"

    def fetch_date(
        self, date: datetime, segment: str = "fo", use_cache: bool = True
    ) -> Optional[pd.DataFrame]:
        """
        Fetch bhavcopy for a single date.

        Args:
            date: Target date
            segment: 'fo' for F&O or 'eq' for equities
            use_cache: Use cached file if exists

        Returns:
            DataFrame or None if not available (weekend/holiday)
        """
        cache_file = self._cache_path(date, segment)

        if use_cache and cache_file.exists():
            return pd.read_csv(cache_file)

        url = self._build_url(date, segment)
        try:
            resp = requests.get(url, headers=HEADERS, timeout=30)
            if resp.status_code != 200:
                return None

            with zipfile.ZipFile(io.BytesIO(resp.content)) as zf:
                csv_name = zf.namelist()[0]
                with zf.open(csv_name) as f:
                    df = pd.read_csv(f)

            df.to_csv(cache_file, index=False)
            return df

        except Exception as e:
            print(f"  [NSE] Failed {date.strftime('%Y-%m-%d')}: {e}")
            return None

    def fetch_range(
        self,
        start_date: datetime,
        end_date: datetime,
        segment: str = "fo",
        symbols: Optional[List[str]] = None,
    ) -> pd.DataFrame:
        """
        Fetch bhavcopies across a date range.

        Args:
            start_date, end_date: Date range (inclusive)
            segment: 'fo' or 'eq'
            symbols: Filter to specific symbols (e.g. ['NIFTY', 'BANKNIFTY'])

        Returns:
            Combined DataFrame with all days
        """
        current = start_date
        all_dfs = []

        while current <= end_date:
            # Skip weekends
            if current.weekday() >= 5:
                current += timedelta(days=1)
                continue

            df = self.fetch_date(current, segment)
            if df is not None:
                df["trade_date"] = current.strftime("%Y-%m-%d")
                if symbols and "SYMBOL" in df.columns:
                    df = df[df["SYMBOL"].isin(symbols)]
                all_dfs.append(df)
                print(f"  [NSE] {current.strftime('%Y-%m-%d')}: {len(df)} rows")

            current += timedelta(days=1)

        if not all_dfs:
            return pd.DataFrame()

        return pd.concat(all_dfs, ignore_index=True)

    def fetch_5_year_history(
        self, symbols: List[str] = None, end_date: datetime = None
    ) -> pd.DataFrame:
        """
        Fetch 5 years of daily F&O data (daily resolution; free).

        Args:
            symbols: e.g. ['NIFTY', 'BANKNIFTY']
            end_date: End date (defaults to today)

        Returns:
            5 years of F&O bhavcopy data
        """
        end_date = end_date or datetime.now()
        start_date = end_date - timedelta(days=5 * 365)

        print(f"  [NSE] Fetching 5 years: {start_date.date()} to {end_date.date()}")
        return self.fetch_range(start_date, end_date, "fo", symbols)


if __name__ == "__main__":
    # Quick test
    ingester = NSEBhavcopyIngester()

    # Fetch 5 days of F&O data as a smoke test
    end = datetime(2024, 12, 31)
    start = end - timedelta(days=5)

    df = ingester.fetch_range(start, end, "fo", symbols=["NIFTY", "BANKNIFTY"])
    print(f"\nFetched {len(df)} rows")
    if not df.empty:
        print(df.head())
