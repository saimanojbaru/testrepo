"""Equity Research MCP Server.

Exposes 6 tools for equity research directly to Claude:
  1. get_stock_data       - Current price snapshot (US / NSE / BSE)
  2. get_historical_data  - OHLCV history
  3. get_index_data       - Nifty 50 / Sensex index levels
  4. get_fundamentals     - Valuation, profitability, balance sheet
  5. analyze_sentiment    - News headline sentiment scoring
  6. generate_report      - Full Markdown equity research report

Usage:
    mcp dev equity_research_mcp/server.py
"""

import json
from typing import Any

from mcp.server.fastmcp import FastMCP

from equity_research_mcp.tools.stock_data import (
    get_stock_data as _get_stock_data,
    get_historical_data as _get_historical_data,
    get_index_data as _get_index_data,
)
from equity_research_mcp.tools.fundamental import get_fundamentals as _get_fundamentals
from equity_research_mcp.tools.sentiment import analyze_sentiment as _analyze_sentiment
from equity_research_mcp.tools.reports import generate_report as _generate_report

mcp = FastMCP("equity-research")


# ── Tool 1: Stock snapshot ────────────────────────────────────────────────────

@mcp.tool()
def get_stock_data(ticker: str, exchange: str = "") -> str:
    """Fetch the current price snapshot for a stock.

    Returns price, day change, 52-week range, volume, and market cap.

    Supported markets:
    - US stocks: plain ticker (e.g. "AAPL", "MSFT")
    - NSE (India): append .NS or set exchange="NSE" (e.g. "RELIANCE.NS")
    - BSE (India): append .BO or set exchange="BSE" (e.g. "RELIANCE.BO")

    Args:
        ticker: Ticker symbol.
        exchange: Optional exchange — "NSE", "BSE", or "" for US.
    """
    result = _get_stock_data(ticker, exchange or None)
    return json.dumps(result, indent=2)


# ── Tool 2: Historical data ───────────────────────────────────────────────────

@mcp.tool()
def get_historical_data(ticker: str, period: str = "1y", exchange: str = "") -> str:
    """Fetch OHLCV historical data for a stock.

    Args:
        ticker: Ticker symbol (e.g. "TCS.NS", "AAPL").
        period: One of: "1d", "5d", "1mo", "3mo", "6mo", "1y", "2y", "5y",
                "10y", "ytd", "max". Default: "1y".
        exchange: Optional — "NSE" or "BSE" for Indian stocks without suffix.
    """
    records = _get_historical_data(ticker, period, exchange or None)
    return json.dumps({"ticker": ticker, "period": period, "records": records}, indent=2)


# ── Tool 3: Index data (Nifty / Sensex) ──────────────────────────────────────

@mcp.tool()
def get_index_data(index: str, period: str = "1mo") -> str:
    """Fetch current level and performance for a market index.

    Supported indices:
    - "nifty" or "nifty50" → Nifty 50 (^NSEI)
    - "sensex" or "bse sensex" → BSE Sensex (^BSESN)
    - Raw symbol like "^NSEI" or "^BSESN" also accepted.

    Args:
        index: Index name or symbol.
        period: Historical period for performance stats. Default: "1mo".
    """
    result = _get_index_data(index, period)
    return json.dumps(result, indent=2)


# ── Tool 4: Fundamentals ──────────────────────────────────────────────────────

@mcp.tool()
def get_fundamentals(ticker: str, exchange: str = "") -> str:
    """Fetch fundamental metrics for a stock.

    Returns valuation ratios (P/E, P/B, EV/EBITDA), profitability margins,
    earnings, revenue, balance sheet, dividends, and analyst consensus.

    Works for US, NSE (.NS), and BSE (.BO) listed companies.

    Args:
        ticker: Ticker symbol (e.g. "INFY.NS", "HDFCBANK.NS", "AAPL").
        exchange: Optional — "NSE" or "BSE".
    """
    result = _get_fundamentals(ticker, exchange or None)
    return json.dumps(result, indent=2)


# ── Tool 5: Sentiment analysis ────────────────────────────────────────────────

@mcp.tool()
def analyze_sentiment(text: str) -> str:
    """Analyse a news headline or research excerpt for equity sentiment.

    Returns a sentiment label (bullish / neutral / bearish), a numeric score
    in [-1.0, 1.0], and lists of matched bullish and bearish keywords.

    Includes Indian market terminology (FII flows, SEBI, NPA, capex, etc.).

    Args:
        text: News headline, earnings snippet, or research note.
    """
    result = _analyze_sentiment(text)
    return json.dumps(result, indent=2)


# ── Tool 6: Full research report ─────────────────────────────────────────────

@mcp.tool()
def generate_report(
    ticker: str,
    exchange: str = "",
    headlines: str = "",
) -> str:
    """Generate a comprehensive Markdown equity research report.

    Combines price snapshot, valuation, profitability, balance sheet,
    dividends, analyst consensus, and optional news sentiment.

    Args:
        ticker: Ticker symbol (e.g. "RELIANCE.NS", "TCS", "AAPL").
        exchange: Optional — "NSE" or "BSE" for Indian stocks without suffix.
        headlines: Optional JSON array string of recent news headlines to
                   include in sentiment section.
                   Example: '["TCS beats Q4 estimates", "IT sector weak outlook"]'
    """
    parsed_headlines: list[str] | None = None
    if headlines.strip():
        try:
            parsed_headlines = json.loads(headlines)
            if not isinstance(parsed_headlines, list):
                parsed_headlines = None
        except json.JSONDecodeError:
            parsed_headlines = None

    report = _generate_report(ticker, exchange or None, parsed_headlines)
    return report


if __name__ == "__main__":
    mcp.run()
