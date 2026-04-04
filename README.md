# Commodity Price Tracker — Local Dev Setup

A live dashboard that calculates real-time import landed prices for Indian commodities (Gold, Silver, Crude Oil, Natural Gas, Copper, and more). It combines international benchmark prices with forex rates and customs duties to compute Indian import costs. No backend required — runs entirely in the browser.

## Prerequisites

- [Node.js](https://nodejs.org/) (for `npx`)
- Git

## Quick Start

Run the setup script to clone and serve the app:

```bash
bash setup-local-dev.sh
```

Then open http://localhost:3000 in your browser.

## Manual Steps

```bash
git clone https://github.com/MrChartist/commodity-price-tracker.git
cd commodity-price-tracker
npx serve .
```

## What It Does

- Tracks live prices for: Gold, Silver, Platinum, Crude Oil, Natural Gas, Copper, Aluminium, Zinc, Nickel, Lead
- Fetches data from Yahoo Finance and Metals.live APIs
- Renders interactive charts via TradingView's Lightweight Charts library
- Supports dark/light theme toggle
- Includes a `docs.html` educational glossary page

## Project Structure

| File | Description |
|------|-------------|
| `index.html` | Main dashboard UI |
| `app.js` | Pricing calculation engine (no external dependencies) |
| `style.css` | Styles including dark/light theme |
| `docs.html` | Educational documentation and glossary |
