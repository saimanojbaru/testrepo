"""
Upstox historical candle loader (free via Upstox API).
Supports 1minute, 30minute, day intervals.

Upstox free tier:
- Historical candles: up to 1 year intraday, multi-year daily
- Live WebSocket: real-time quotes for subscribed instruments
"""

import requests
import pandas as pd
from datetime import datetime, timedelta
from typing import Optional, List
from pathlib import Path


UPSTOX_HIST_URL = "https://api.upstox.com/v2/historical-candle/{instrument_key}/{interval}/{to_date}/{from_date}"


class UpstoxHistoricalLoader:
    """
    Fetches historical candles from Upstox API.
    Requires a valid access token (free tier).

    Instrument keys for Nifty/Bank Nifty indices:
    - Nifty 50:       NSE_INDEX|Nifty 50
    - Bank Nifty:     NSE_INDEX|Nifty Bank
    - India VIX:      NSE_INDEX|India VIX
    """

    INSTRUMENT_KEYS = {
        "NIFTY": "NSE_INDEX|Nifty 50",
        "BANKNIFTY": "NSE_INDEX|Nifty Bank",
        "INDIAVIX": "NSE_INDEX|India VIX",
    }

    def __init__(self, access_token: Optional[str] = None, cache_dir: str = "data/raw/upstox"):
        self.access_token = access_token
        self.cache_dir = Path(cache_dir)
        self.cache_dir.mkdir(parents=True, exist_ok=True)
        self.headers = {"Accept": "application/json"}
        if access_token:
            self.headers["Authorization"] = f"Bearer {access_token}"

    def _cache_file(self, symbol: str, interval: str, from_date: str, to_date: str) -> Path:
        return self.cache_dir / f"{symbol}_{interval}_{from_date}_{to_date}.csv"

    def fetch_candles(
        self,
        symbol: str,
        interval: str,
        from_date: datetime,
        to_date: datetime,
        use_cache: bool = True,
    ) -> Optional[pd.DataFrame]:
        """
        Fetch historical candles for a symbol.

        Args:
            symbol: e.g. 'NIFTY', 'BANKNIFTY' (mapped via INSTRUMENT_KEYS)
            interval: '1minute', '30minute', 'day'
            from_date, to_date: Date range

        Returns:
            DataFrame with timestamp, open, high, low, close, volume
        """
        instrument = self.INSTRUMENT_KEYS.get(symbol, symbol)
        from_str = from_date.strftime("%Y-%m-%d")
        to_str = to_date.strftime("%Y-%m-%d")

        cache = self._cache_file(symbol, interval, from_str, to_str)
        if use_cache and cache.exists():
            return pd.read_csv(cache)

        url = UPSTOX_HIST_URL.format(
            instrument_key=instrument,
            interval=interval,
            to_date=to_str,
            from_date=from_str,
        )

        try:
            resp = requests.get(url, headers=self.headers, timeout=30)
            data = resp.json()

            if data.get("status") != "success":
                print(f"  [Upstox] {symbol} {interval}: {data.get('errors', 'unknown error')}")
                return None

            candles = data.get("data", {}).get("candles", [])
            if not candles:
                return None

            df = pd.DataFrame(
                candles,
                columns=["timestamp", "open", "high", "low", "close", "volume", "oi"],
            )
            df["timestamp"] = pd.to_datetime(df["timestamp"])
            df["symbol"] = symbol
            df = df.sort_values("timestamp").reset_index(drop=True)

            df.to_csv(cache, index=False)
            return df

        except Exception as e:
            print(f"  [Upstox] Failed {symbol}: {e}")
            return None

    def fetch_5_year_daily(self, symbols: List[str] = None) -> pd.DataFrame:
        """Fetch 5 years of daily candles."""
        symbols = symbols or ["NIFTY", "BANKNIFTY"]
        end = datetime.now()
        start = end - timedelta(days=5 * 365)

        all_dfs = []
        for symbol in symbols:
            print(f"  [Upstox] Fetching {symbol} day {start.date()} to {end.date()}")
            df = self.fetch_candles(symbol, "day", start, end)
            if df is not None:
                all_dfs.append(df)
                print(f"  [Upstox] Got {len(df)} candles")

        if not all_dfs:
            return pd.DataFrame()

        return pd.concat(all_dfs, ignore_index=True)

    def fetch_1year_intraday(
        self, symbols: List[str] = None, interval: str = "1minute"
    ) -> pd.DataFrame:
        """Fetch up to 1 year of intraday candles (chunked)."""
        symbols = symbols or ["NIFTY", "BANKNIFTY"]
        end = datetime.now()

        all_dfs = []
        # Upstox returns max ~30 days per request for 1minute, chunk accordingly
        chunk_days = 30 if interval == "1minute" else 90

        for symbol in symbols:
            current = end
            while current > end - timedelta(days=365):
                chunk_start = current - timedelta(days=chunk_days)
                df = self.fetch_candles(symbol, interval, chunk_start, current)
                if df is not None:
                    all_dfs.append(df)
                current = chunk_start

        if not all_dfs:
            return pd.DataFrame()

        return pd.concat(all_dfs, ignore_index=True).drop_duplicates(subset=["timestamp", "symbol"])


if __name__ == "__main__":
    loader = UpstoxHistoricalLoader()
    df = loader.fetch_5_year_daily(["NIFTY"])
    print(f"\nFetched {len(df)} daily bars")
    if not df.empty:
        print(df.head())
