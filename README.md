# Equity Research MCP Plugin

A Claude MCP (Model Context Protocol) server for equity research. Supports **US markets**, **NSE India**, **BSE India**, **Nifty 50**, and **Sensex**.

## Tools

| Tool | Description |
|------|-------------|
| `get_stock_data` | Current price, day change, 52-week range, volume, market cap |
| `get_historical_data` | OHLCV data for any period (1d to max) |
| `get_index_data` | Nifty 50 / Sensex level and period performance |
| `get_fundamentals` | P/E, EPS, revenue, margins, balance sheet, analyst rating |
| `analyze_sentiment` | Score news headlines as bullish / neutral / bearish |
| `generate_report` | Full Markdown equity research report |

## Market Coverage

| Market | Format | Example |
|--------|--------|---------|
| US | Plain ticker | `AAPL`, `TSLA` |
| NSE (India) | `.NS` suffix or `exchange="NSE"` | `RELIANCE.NS`, `TCS.NS` |
| BSE (India) | `.BO` suffix or `exchange="BSE"` | `RELIANCE.BO` |
| Nifty 50 | `"nifty"` or `"^NSEI"` | — |
| Sensex | `"sensex"` or `"^BSESN"` | — |

## Installation

```bash
pip install -e .
```

## Running the Server

```bash
mcp dev equity_research_mcp/server.py
```

Or install globally and add to Claude's MCP config:

```json
{
  "mcpServers": {
    "equity-research": {
      "command": "equity-research-mcp"
    }
  }
}
```

## Example Usage in Claude

Once the server is running, Claude can call tools like:

- *"What is the current price of Reliance Industries on NSE?"*
  → `get_stock_data("RELIANCE.NS")`

- *"Show me Nifty 50 performance over the last month."*
  → `get_index_data("nifty", "1mo")`

- *"Generate a research report for TCS."*
  → `generate_report("TCS.NS")`

- *"Is this headline bullish or bearish? 'TCS beats Q4 estimates, raises guidance'"*
  → `analyze_sentiment("TCS beats Q4 estimates, raises guidance")`

## Disclaimer

This plugin uses publicly available data from Yahoo Finance. It is for informational purposes only and does not constitute financial or investment advice. Consult a SEBI-registered investment advisor before making investment decisions.
