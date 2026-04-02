"""Fundamental analysis utilities.

Extracts valuation, profitability, and balance sheet metrics
from yfinance for US, NSE, and BSE listed companies.
"""

from __future__ import annotations

import yfinance as yf

from .stock_data import resolve_ticker


def _fmt(value, decimals: int = 2):
    """Return rounded float or None."""
    if value is None:
        return None
    try:
        return round(float(value), decimals)
    except (TypeError, ValueError):
        return None


def _humanize(value) -> str | None:
    """Convert large numbers to human-readable strings (Cr / B / M)."""
    if value is None:
        return None
    try:
        v = float(value)
    except (TypeError, ValueError):
        return None

    if abs(v) >= 1e12:
        return f"{v / 1e12:.2f}T"
    if abs(v) >= 1e9:
        return f"{v / 1e9:.2f}B"
    if abs(v) >= 1e6:
        return f"{v / 1e6:.2f}M"
    return str(v)


def get_fundamentals(ticker: str, exchange: str | None = None) -> dict:
    """Return key fundamental metrics for a stock.

    Covers valuation ratios, profitability, growth, and balance sheet.
    Works for US, NSE (.NS), and BSE (.BO) tickers.

    Args:
        ticker: Ticker symbol.
        exchange: Optional — "NSE" or "BSE" for Indian stocks without suffix.

    Returns:
        Dict of fundamental metrics.
    """
    symbol = resolve_ticker(ticker, exchange)
    t = yf.Ticker(symbol)
    info = t.info

    # Revenue growth YoY
    revenue_growth = info.get("revenueGrowth")
    earnings_growth = info.get("earningsGrowth")

    result = {
        "ticker": symbol,
        "name": info.get("longName") or info.get("shortName", symbol),
        "currency": info.get("currency", "N/A"),
        "exchange": info.get("exchange", "N/A"),
        "sector": info.get("sector"),
        "industry": info.get("industry"),

        # Valuation
        "pe_ratio": _fmt(info.get("trailingPE")),
        "forward_pe": _fmt(info.get("forwardPE")),
        "pb_ratio": _fmt(info.get("priceToBook")),
        "ps_ratio": _fmt(info.get("priceToSalesTrailing12Months")),
        "ev_to_ebitda": _fmt(info.get("enterpriseToEbitda")),
        "peg_ratio": _fmt(info.get("pegRatio")),

        # Earnings
        "eps_ttm": _fmt(info.get("trailingEps")),
        "eps_forward": _fmt(info.get("forwardEps")),
        "earnings_growth_yoy": _fmt(earnings_growth, 4) if earnings_growth else None,

        # Revenue
        "revenue_ttm": _humanize(info.get("totalRevenue")),
        "revenue_growth_yoy": _fmt(revenue_growth, 4) if revenue_growth else None,
        "gross_margins": _fmt(info.get("grossMargins"), 4),
        "operating_margins": _fmt(info.get("operatingMargins"), 4),
        "profit_margins": _fmt(info.get("profitMargins"), 4),

        # Balance sheet
        "total_cash": _humanize(info.get("totalCash")),
        "total_debt": _humanize(info.get("totalDebt")),
        "debt_to_equity": _fmt(info.get("debtToEquity")),
        "current_ratio": _fmt(info.get("currentRatio")),
        "book_value_per_share": _fmt(info.get("bookValue")),

        # Dividends
        "dividend_yield": _fmt(info.get("dividendYield"), 4),
        "dividend_rate": _fmt(info.get("dividendRate")),
        "payout_ratio": _fmt(info.get("payoutRatio"), 4),

        # Returns
        "roe": _fmt(info.get("returnOnEquity"), 4),
        "roa": _fmt(info.get("returnOnAssets"), 4),

        # Market cap
        "market_cap": _humanize(info.get("marketCap")),
        "enterprise_value": _humanize(info.get("enterpriseValue")),

        # Analyst consensus
        "analyst_rating": info.get("recommendationKey"),
        "target_mean_price": _fmt(info.get("targetMeanPrice")),
        "number_of_analysts": info.get("numberOfAnalystOpinions"),
    }

    return result
