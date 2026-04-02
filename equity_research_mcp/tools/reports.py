"""Equity research report generator.

Combines stock data, fundamentals, and sentiment into
a structured Markdown report for US, NSE, and BSE stocks.
"""

from __future__ import annotations

from datetime import date

from .fundamental import get_fundamentals
from .sentiment import analyze_multiple
from .stock_data import get_stock_data, get_historical_data, resolve_ticker


def _pct(value) -> str:
    if value is None:
        return "N/A"
    return f"{value:+.2f}%"


def _val(value, suffix: str = "") -> str:
    if value is None:
        return "N/A"
    return f"{value}{suffix}"


def _exchange_label(symbol: str) -> str:
    if symbol.endswith(".NS"):
        return "NSE"
    if symbol.endswith(".BO"):
        return "BSE"
    if symbol.startswith("^"):
        return "Index"
    return "US"


def generate_report(
    ticker: str,
    exchange: str | None = None,
    sentiment_headlines: list[str] | None = None,
) -> str:
    """Generate a Markdown equity research report for a given ticker.

    Args:
        ticker: Ticker symbol (e.g. "AAPL", "RELIANCE.NS", "TCS").
        exchange: Optional — "NSE" or "BSE" for Indian stocks without suffix.
        sentiment_headlines: Optional list of recent news headlines to score.

    Returns:
        Markdown-formatted equity research report as a string.
    """
    symbol = resolve_ticker(ticker, exchange)
    exch = _exchange_label(symbol)

    snap = get_stock_data(symbol)
    fund = get_fundamentals(symbol)
    hist_1y = get_historical_data(symbol, period="1y")

    # Performance calculation from 1-year history
    perf_1y = None
    if len(hist_1y) >= 2:
        start_price = hist_1y[0]["close"]
        end_price = hist_1y[-1]["close"]
        if start_price:
            perf_1y = round((end_price - start_price) / start_price * 100, 2)

    currency = snap.get("currency", "")
    name = snap.get("name", symbol)
    price = snap.get("current_price")
    day_chg_pct = snap.get("day_change_pct")

    lines: list[str] = []

    # ── Header ────────────────────────────────────────────────────────────────
    lines.append(f"# Equity Research Report: {name} ({symbol})")
    lines.append(f"**Exchange:** {exch} | **Date:** {date.today().isoformat()}")
    lines.append("")

    # ── Price Snapshot ────────────────────────────────────────────────────────
    lines.append("## Price Snapshot")
    lines.append(f"| Metric | Value |")
    lines.append(f"|--------|-------|")
    lines.append(f"| Current Price | {currency} {_val(price)} |")
    lines.append(f"| Day Change | {_pct(day_chg_pct)} |")
    lines.append(f"| Day High / Low | {_val(snap.get('day_high'))} / {_val(snap.get('day_low'))} |")
    lines.append(f"| 52-Week High / Low | {_val(snap.get('week_52_high'))} / {_val(snap.get('week_52_low'))} |")
    lines.append(f"| Volume | {_val(snap.get('volume'))} |")
    lines.append(f"| Market Cap | {_val(fund.get('market_cap'))} |")
    lines.append(f"| 1-Year Return | {_pct(perf_1y)} |")
    lines.append("")

    # ── Valuation Metrics ─────────────────────────────────────────────────────
    lines.append("## Valuation")
    lines.append("| Metric | Value |")
    lines.append("|--------|-------|")
    lines.append(f"| P/E Ratio (TTM) | {_val(fund.get('pe_ratio'))} |")
    lines.append(f"| Forward P/E | {_val(fund.get('forward_pe'))} |")
    lines.append(f"| P/B Ratio | {_val(fund.get('pb_ratio'))} |")
    lines.append(f"| P/S Ratio | {_val(fund.get('ps_ratio'))} |")
    lines.append(f"| EV/EBITDA | {_val(fund.get('ev_to_ebitda'))} |")
    lines.append(f"| PEG Ratio | {_val(fund.get('peg_ratio'))} |")
    lines.append(f"| Enterprise Value | {_val(fund.get('enterprise_value'))} |")
    lines.append("")

    # ── Profitability & Growth ────────────────────────────────────────────────
    lines.append("## Profitability & Growth")
    lines.append("| Metric | Value |")
    lines.append("|--------|-------|")
    lines.append(f"| Revenue (TTM) | {_val(fund.get('revenue_ttm'))} |")
    lines.append(f"| Revenue Growth YoY | {_pct(None if fund.get('revenue_growth_yoy') is None else fund['revenue_growth_yoy'] * 100)} |")
    lines.append(f"| Gross Margin | {_pct(None if fund.get('gross_margins') is None else fund['gross_margins'] * 100)} |")
    lines.append(f"| Operating Margin | {_pct(None if fund.get('operating_margins') is None else fund['operating_margins'] * 100)} |")
    lines.append(f"| Net Profit Margin | {_pct(None if fund.get('profit_margins') is None else fund['profit_margins'] * 100)} |")
    lines.append(f"| EPS (TTM) | {_val(fund.get('eps_ttm'))} |")
    lines.append(f"| Forward EPS | {_val(fund.get('eps_forward'))} |")
    lines.append(f"| Earnings Growth YoY | {_pct(None if fund.get('earnings_growth_yoy') is None else fund['earnings_growth_yoy'] * 100)} |")
    lines.append(f"| ROE | {_pct(None if fund.get('roe') is None else fund['roe'] * 100)} |")
    lines.append(f"| ROA | {_pct(None if fund.get('roa') is None else fund['roa'] * 100)} |")
    lines.append("")

    # ── Balance Sheet ─────────────────────────────────────────────────────────
    lines.append("## Balance Sheet")
    lines.append("| Metric | Value |")
    lines.append("|--------|-------|")
    lines.append(f"| Total Cash | {_val(fund.get('total_cash'))} |")
    lines.append(f"| Total Debt | {_val(fund.get('total_debt'))} |")
    lines.append(f"| Debt / Equity | {_val(fund.get('debt_to_equity'))} |")
    lines.append(f"| Current Ratio | {_val(fund.get('current_ratio'))} |")
    lines.append(f"| Book Value / Share | {_val(fund.get('book_value_per_share'))} |")
    lines.append("")

    # ── Dividends ─────────────────────────────────────────────────────────────
    div_yield = fund.get("dividend_yield")
    if div_yield:
        lines.append("## Dividends")
        lines.append("| Metric | Value |")
        lines.append("|--------|-------|")
        lines.append(f"| Dividend Yield | {_pct(div_yield * 100)} |")
        lines.append(f"| Dividend Rate | {_val(fund.get('dividend_rate'))} |")
        lines.append(f"| Payout Ratio | {_pct(None if fund.get('payout_ratio') is None else fund['payout_ratio'] * 100)} |")
        lines.append("")

    # ── Analyst Consensus ─────────────────────────────────────────────────────
    rating = fund.get("analyst_rating")
    target = fund.get("target_mean_price")
    num_analysts = fund.get("number_of_analysts")
    if rating or target:
        lines.append("## Analyst Consensus")
        lines.append("| Metric | Value |")
        lines.append("|--------|-------|")
        if rating:
            lines.append(f"| Recommendation | {rating.upper()} |")
        if target:
            lines.append(f"| Mean Price Target | {currency} {target} |")
        if num_analysts:
            lines.append(f"| Number of Analysts | {num_analysts} |")
        if price and target:
            upside = round((target - price) / price * 100, 2)
            lines.append(f"| Implied Upside | {_pct(upside)} |")
        lines.append("")

    # ── Sector & Industry ─────────────────────────────────────────────────────
    sector = fund.get("sector")
    industry = fund.get("industry")
    if sector or industry:
        lines.append("## Company Profile")
        if sector:
            lines.append(f"- **Sector:** {sector}")
        if industry:
            lines.append(f"- **Industry:** {industry}")
        lines.append("")

    # ── Sentiment Analysis ────────────────────────────────────────────────────
    if sentiment_headlines:
        sentiment_result = analyze_multiple(sentiment_headlines)
        agg = sentiment_result["aggregate_sentiment"].upper()
        score = sentiment_result["aggregate_score"]
        lines.append("## News Sentiment Analysis")
        lines.append(f"**Aggregate Sentiment:** {agg} (score: {score:.2f})")
        lines.append("")
        lines.append("| # | Headline | Sentiment | Score |")
        lines.append("|---|----------|-----------|-------|")
        for i, item in enumerate(sentiment_result["items"], 1):
            preview = item["text_preview"][:80].replace("|", "\\|")
            lines.append(f"| {i} | {preview} | {item['sentiment'].upper()} | {item['score']:.2f} |")
        lines.append("")

    # ── Disclaimer ────────────────────────────────────────────────────────────
    lines.append("---")
    lines.append(
        "_This report is generated automatically using publicly available data. "
        "It is for informational purposes only and does not constitute financial advice. "
        "Please consult a SEBI-registered investment advisor before making investment decisions._"
    )

    return "\n".join(lines)
