"""Stock data fetching utilities using yfinance.

Supports US markets, NSE (.NS suffix), BSE (.BO suffix),
Nifty 50 (^NSEI), and Sensex (^BSESN).
"""

from __future__ import annotations

import yfinance as yf


INDEX_MAP = {
    "nifty": "^NSEI",
    "nifty50": "^NSEI",
    "nifty 50": "^NSEI",
    "sensex": "^BSESN",
    "bse sensex": "^BSESN",
}


def resolve_ticker(ticker: str, exchange: str | None = None) -> str:
    """Return the yfinance-compatible ticker symbol.

    Args:
        ticker: Raw ticker symbol (e.g. "RELIANCE", "AAPL", "^NSEI").
        exchange: Optional exchange hint — "NSE", "BSE", or None for US/auto.

    Returns:
        Resolved ticker string ready for yfinance.
    """
    # Already fully qualified or is an index symbol
    if ticker.startswith("^") or "." in ticker:
        return ticker

    lower = ticker.lower()
    if lower in INDEX_MAP:
        return INDEX_MAP[lower]

    if exchange:
        exchange = exchange.upper()
        if exchange == "NSE":
            return f"{ticker}.NS"
        if exchange == "BSE":
            return f"{ticker}.BO"

    return ticker


def get_stock_data(ticker: str, exchange: str | None = None) -> dict:
    """Fetch current snapshot data for a stock.

    Args:
        ticker: Ticker symbol (e.g. "AAPL", "RELIANCE.NS", "TCS").
        exchange: Optional — "NSE" or "BSE" for Indian stocks without suffix.

    Returns:
        Dict with price, currency, volume, 52-week high/low, market cap.
    """
    symbol = resolve_ticker(ticker, exchange)
    t = yf.Ticker(symbol)
    info = t.info

    result = {
        "ticker": symbol,
        "name": info.get("longName") or info.get("shortName", symbol),
        "currency": info.get("currency", "N/A"),
        "current_price": info.get("currentPrice") or info.get("regularMarketPrice"),
        "previous_close": info.get("previousClose") or info.get("regularMarketPreviousClose"),
        "open": info.get("open") or info.get("regularMarketOpen"),
        "day_high": info.get("dayHigh") or info.get("regularMarketDayHigh"),
        "day_low": info.get("dayLow") or info.get("regularMarketDayLow"),
        "volume": info.get("volume") or info.get("regularMarketVolume"),
        "avg_volume": info.get("averageVolume"),
        "week_52_high": info.get("fiftyTwoWeekHigh"),
        "week_52_low": info.get("fiftyTwoWeekLow"),
        "market_cap": info.get("marketCap"),
        "exchange": info.get("exchange", "N/A"),
    }

    # Compute day change
    price = result["current_price"]
    prev = result["previous_close"]
    if price and prev:
        result["day_change"] = round(price - prev, 4)
        result["day_change_pct"] = round((price - prev) / prev * 100, 2)
    else:
        result["day_change"] = None
        result["day_change_pct"] = None

    return result


def get_historical_data(ticker: str, period: str = "1y", exchange: str | None = None) -> list[dict]:
    """Fetch OHLCV historical data.

    Args:
        ticker: Ticker symbol.
        period: yfinance period string — "1d", "5d", "1mo", "3mo", "6mo",
                "1y", "2y", "5y", "10y", "ytd", "max".
        exchange: Optional — "NSE" or "BSE".

    Returns:
        List of dicts with date, open, high, low, close, volume.
    """
    symbol = resolve_ticker(ticker, exchange)
    t = yf.Ticker(symbol)
    hist = t.history(period=period)

    if hist.empty:
        return []

    records = []
    for date, row in hist.iterrows():
        records.append({
            "date": date.strftime("%Y-%m-%d"),
            "open": round(float(row["Open"]), 4),
            "high": round(float(row["High"]), 4),
            "low": round(float(row["Low"]), 4),
            "close": round(float(row["Close"]), 4),
            "volume": int(row["Volume"]),
        })
    return records


def get_index_data(index: str, period: str = "1mo") -> dict:
    """Fetch current level and recent performance for Nifty 50 or Sensex.

    Args:
        index: "nifty", "nifty50", "sensex", or a raw symbol like "^NSEI".
        period: Historical period for performance calculation.

    Returns:
        Dict with index name, current level, day change, period change.
    """
    symbol = resolve_ticker(index)
    if not symbol.startswith("^"):
        # Fallback: treat as raw yfinance symbol
        symbol = index

    index_names = {
        "^NSEI": "Nifty 50",
        "^BSESN": "BSE Sensex",
    }

    t = yf.Ticker(symbol)
    info = t.info
    hist = t.history(period=period)

    current = info.get("regularMarketPrice") or info.get("currentPrice")
    prev_close = info.get("regularMarketPreviousClose") or info.get("previousClose")

    result = {
        "index": index_names.get(symbol, symbol),
        "symbol": symbol,
        "current_level": current,
        "previous_close": prev_close,
        "currency": info.get("currency", "INR"),
    }

    if current and prev_close:
        result["day_change"] = round(current - prev_close, 2)
        result["day_change_pct"] = round((current - prev_close) / prev_close * 100, 2)
    else:
        result["day_change"] = None
        result["day_change_pct"] = None

    if not hist.empty:
        period_start = float(hist["Close"].iloc[0])
        period_end = float(hist["Close"].iloc[-1])
        result["period_return_pct"] = round((period_end - period_start) / period_start * 100, 2)
        result["period_high"] = round(float(hist["High"].max()), 2)
        result["period_low"] = round(float(hist["Low"].min()), 2)
    else:
        result["period_return_pct"] = None
        result["period_high"] = None
        result["period_low"] = None

    return result
