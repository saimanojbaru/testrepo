# Scalping Agent — Quick Start

## 1. Install & Configure (5 minutes)

```bash
# Install dependencies
pip install -e .

# Create .env from template
cp .env.example .env

# Edit .env with your credentials
nano .env
```

### Required for MVP:
- `UPSTOX_API_KEY` / `UPSTOX_SECRET_KEY` — free from https://developer.upstox.com
- `TRADING_CAPITAL` — starting capital (default ₹100,000)
- `PRIMARY_BROKER=upstox` — use free API

### Optional (for later phases):
- `KITE_API_KEY` / `KITE_ACCESS_TOKEN` — Zerodha (₹2000/mo)
- `DATABASE_URL` — if using TimescaleDB (local SQLite for testing)
- `TELEGRAM_BOT_TOKEN` — alerts (free)

## 2. Get Upstox Credentials (10 minutes)

1. Go to https://developer.upstox.com
2. Sign up → Create App
3. Copy `API Key` and `API Secret` into `.env`:
   ```env
   UPSTOX_API_KEY=your_key_here
   UPSTOX_SECRET_KEY=your_secret_here
   ```

## 3. Pull 5 Years of Historical Data (10–20 minutes)

Uses **free sources only** (NSE, Yahoo Finance). No paid vendor required for initial discovery.

```bash
python main.py --mode ingest --symbols NIFTY,BANKNIFTY --years 5
```

**Output:**
- `data/raw/` → downloaded bhavcopies + Yahoo CSV
- `data/processed/` → clean parquet files (5+ years of daily OHLCV)
- Coverage report → shows % of free sources vs need for paid data

## 4. Discover Profitable Strategies (30–60 minutes)

Optuna searches feature space + walk-forward validates. Only promotes strategies with **Sharpe > 1.5** net-of-costs.

```bash
python main.py --mode discover --n-trials 100 --symbols NIFTY
```

**What it does:**
1. Grid of (feature, entry_threshold, exit_threshold) combos
2. Walk-forward backtest on each: 6-month train, 1-month test, ≥3 folds
3. Cost-aware: Zerodha fee schedule + STT applied
4. Returns: top-5 strategies with Sharpe, P&L, win-rate

**Output:**
- `discovered_strategies.json` → serialized best strategies
- Regime classifier trained on backtested P&L

## 5. (Future) Run Paper Trading

Once data + strategies are validated:

```bash
python main.py --mode paper
```

This will:
- Subscribe to live Upstox WebSocket
- Run learned regime classifier on each tick
- Execute via PaperBroker (simulated fills + slippage)
- Log all decisions + P&L for reconciliation
- 4–8 weeks to validate edge before live capital

---

## Architecture at a Glance

```
Data (NSE/Yahoo)
    ↓
Features (RSI, MACD, IV, ATR, etc.)
    ↓
Strategy Discovery (Optuna + Walk-Forward)
    ↓ (Sharpe > 1.5 gate)
Discovered Strategies
    ↓
Regime Classifier (KMeans + XGBoost)
    ↓
TradingAgent Loop:
  ├─ Regime classification
  ├─ Strategy selection
  ├─ Risk check (Kelly sizing, daily loss halt)
  ├─ PaperBroker execution (slippage + costs)
  └─ P&L reconciliation
    ↓
Live Upstox (Phase 9: ₹50k–100k capital, 1 lot)
```

---

## Key Guarantees

✓ **Cost-First:** Every strategy filtered by "is P&L > costs × 1.5?"  
✓ **Risk-First:** Daily loss cap auto-triggers kill switch  
✓ **Walk-Forward Validated:** Not backtested once, tested across independent windows  
✓ **Paper-First:** 4–8 weeks learning before any real capital  
✓ **Regime-Aware:** Agent picks strategy per market regime, not one-size-fits-all  

---

## Tests to Validate Setup

After install, run:

```bash
# Cost model (realistic fee breakdown)
python -m tests.test_costs

# Risk engine (Kelly sizing, loss limits)
python -m tests.test_risk

# End-to-end (full loop: paper broker + P&L)
python -m tests.test_e2e

# Data ingestion (URL construction, caching)
python -m tests.test_ingest
```

All should pass in <1 second.

---

## Troubleshooting

**"ModuleNotFoundError: No module named 'pandas'"**
→ `pip install -e .` didn't work. Try: `pip install pandas numpy requests`

**"Cannot connect to Upstox"**
→ Check `UPSTOX_API_KEY` in `.env`. If blank, data ingestion uses free Yahoo/NSE only (fine for discovery).

**"No data in `data/processed/`"**
→ NSE bhavcopy download failed (holiday/weekend). Check NSE archives manually or wait for next trading day.

**Strategy discovery finds no strategies (Sharpe < 1.5)**
→ Means feature combos don't beat costs. Increase `--n-trials` or use paid tick data for better feature engineering.

---

## Next: What to Expect

1. **Ingest (10–20 min):** ~1.5k trading days × 2 symbols = 3k rows
2. **Discover (30–60 min):** Optuna tries 100 parameter combos, ~3–5 pass gate
3. **Regime fit (5 min):** KMeans clusters bars into 5 regimes
4. **Paper training (4–8 weeks):** Run agent against live ticks, validate edge

→ By week 4, you'll know: "Is this agent profitable in real market conditions?"

**Then:** Go live with ₹50k, 1 lot, ₹2k daily loss cap.

---

Good luck! 🚀
