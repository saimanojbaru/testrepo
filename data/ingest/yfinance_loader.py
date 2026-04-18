"""
Yahoo Finance loader for free index data (Nifty 50, Bank Nifty).
Free intraday (limited to ~60 days) and EOD historical (5+ years).

Yahoo symbols:
- ^NSEI    -> Nifty 50 index
- ^NSEBANK -> Nifty Bank index
- ^INDIAVIX -> India VIX
"""

import pandas as pd
from datetime import datetime, timedelta
from typing import Optional, List

try:
    import yfinance as yf
    YFINANCE_AVAILABLE = True
except ImportError:
    YFINANCE_AVAILABLE = False


YFINANCE_SYMBOL_MAP = {
    "NIFTY": "^NSEI",
    "BANKNIFTY": "^NSEBANK",
    "INDIAVIX": "^INDIAVIX",
}


class YahooFinanceLoader:
    """
    Free data loader for Indian indices via Yahoo Finance.
    Use for: underlying index history (5+ years daily, 60 days intraday).
    Does NOT provide options chain data (that needs Quandl/paid vendor).
    """

    def __init__(self):
        if not YFINANCE_AVAILABLE:
            print("  [YF] Warning: yfinance not installed. Run: pip install yfinance")

    def fetch_index(
        self,
        symbol: str,
        start: datetime,
        end: datetime,
        interval: str = "1d",
    ) -> Optional[pd.DataFrame]:
        """
        Fetch OHLCV for an index.

        Args:
            symbol: 'NIFTY', 'BANKNIFTY', or 'INDIAVIX'
            start, end: Date range
            interval: '1m', '5m', '15m', '30m', '1h', '1d' (1m/5m limited to 60 days)

        Returns:
            DataFrame with OHLCV columns
        """
        if not YFINANCE_AVAILABLE:
            return None

        yf_symbol = YFINANCE_SYMBOL_MAP.get(symbol, symbol)

        try:
            ticker = yf.Ticker(yf_symbol)
            df = ticker.history(start=start, end=end, interval=interval)

            if df.empty:
                return None

            df = df.reset_index()
            df.columns = [c.lower() for c in df.columns]

            # Standardize column names
            if "date" in df.columns:
                df = df.rename(columns={"date": "timestamp"})
            elif "datetime" in df.columns:
                df = df.rename(columns={"datetime": "timestamp"})

            df["symbol"] = symbol
            return df

        except Exception as e:
            print(f"  [YF] Failed {symbol}: {e}")
            return None

    def fetch_5_year_daily(self, symbols: List[str] = None) -> pd.DataFrame:
        """
        Fetch 5 years of daily data for index symbols.
        """
        symbols = symbols or ["NIFTY", "BANKNIFTY", "INDIAVIX"]
        end = datetime.now()
        start = end - timedelta(days=5 * 365)

        all_dfs = []
        for symbol in symbols:
            print(f"  [YF] Fetching {symbol} daily {start.date()} to {end.date()}")
            df = self.fetch_index(symbol, start, end, "1d")
            if df is not None and not df.empty:
                all_dfs.append(df)
                print(f"  [YF] Got {len(df)} bars for {symbol}")

        if not all_dfs:
            return pd.DataFrame()

        return pd.concat(all_dfs, ignore_index=True)

    def fetch_recent_intraday(
        self, symbols: List[str] = None, days: int = 30, interval: str = "5m"
    ) -> pd.DataFrame:
        """
        Fetch recent intraday data (max ~60 days for 5m/1m intervals).
        """
        symbols = symbols or ["NIFTY", "BANKNIFTY"]
        end = datetime.now()
        start = end - timedelta(days=days)

        all_dfs = []
        for symbol in symbols:
            df = self.fetch_index(symbol, start, end, interval)
            if df is not None and not df.empty:
                all_dfs.append(df)

        if not all_dfs:
            return pd.DataFrame()

        return pd.concat(all_dfs, ignore_index=True)


if __name__ == "__main__":
    loader = YahooFinanceLoader()
    df = loader.fetch_5_year_daily(["NIFTY", "BANKNIFTY"])
    print(f"\nTotal rows: {len(df)}")
    if not df.empty:
        print(df.head())
        print(f"\nColumns: {df.columns.tolist()}")
