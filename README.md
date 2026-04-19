# Scalping Agent — Indian F&O

Autonomous scalping agent for Nifty / Bank Nifty / stock & commodity options. Cost-aware
strategy discovery, walk-forward validation, Upstox live execution. Greenfield build on
branch `claude/scalping-agent-indian-options-bKW6h`.

This README covers the MVP sprint (Phases 0–3). See
`/root/.claude/plans/autonomous-scalping-agent-for-eventual-rainbow.md` for the full
12-week roadmap including regime classifier, risk engine, paper training, live deployment,
and Flutter mobile app.

## Quick start

```bash
python -m venv .venv && source .venv/bin/activate   # or Scripts\activate on Windows
pip install -e ".[dev]"
cp .env.example .env                                 # fill in API keys when ready
docker compose up -d postgres                        # Timescale on 5432
pytest                                               # fixture-only tests, no external deps
```

## Run the demo

```bash
# 1) Load 30 days of sample EOD bhavcopy (synthetic fixture shipped in data/fixtures)
python -m data.ingest.nse_bhavcopy --sample

# 2) Measure data completeness (free sources vs required 5-year intraday history)
python -m data.ingest.completeness_report

# 3) Run Optuna strategy discovery on the sample data
python -m strategies.discover --sample --max-trials 50

# 4) Walk-forward validate the top candidate
python -m backtest.walk_forward --strategy top_1 --folds 3
```

## Layout

```
config/        Pydantic settings
costs/         Zerodha/Upstox fee schedule (STT, brokerage, GST, SEBI, stamp duty)
data/          Timescale schema + ingestion pipelines + fixtures
features/      Technical, options (greeks via py_vollib), microstructure, regime
strategies/    Pluggable rule base + Optuna discovery loop + registry
backtest/      Event-driven engine, slippage model, metrics, walk-forward harness
regime/        (Phase 4) XGBoost regime classifier
risk/          (Phase 6) fractional Kelly, daily-loss halt, kill-switch
broker/        (Phase 7) Upstox adapter (Kite deferred)
execution/     (Phase 7) order manager + TradingView webhook receiver
monitor/       (Phase 8) Grafana panels + Telegram bot
tests/         Fixture-based unit + integration tests
```

## Cost-first profitability

Every trade's P&L is computed net of:
- Brokerage (Upstox / Zerodha intraday: flat ₹20 or 0.03%, whichever is lower, per leg)
- STT (0.1% on sell-side option premium, since Oct 2024)
- Exchange transaction charge (NSE F&O: 0.0503% on option premium)
- GST (18% on brokerage + exchange + SEBI)
- SEBI turnover fee (0.0001% on turnover)
- Stamp duty (0.003% on buy-side, state-dependent)

Implemented in `costs/zerodha_upstox.py`. Any discovered strategy must clear costs by
≥1.5× safety margin before promotion — enforced in `strategies/discover.py`.

## Data sourcing

Free-first per approved plan:
1. NSE bhavcopy (daily EOD) — free, no auth
2. Quandl free tier — options/index history
3. Upstox historical REST — intraday candles once API keys configured
4. GitHub option-data dumps — ad-hoc
5. Paid (TrueData / GDFL) — only if `completeness_report` flags gaps > 20%

## Tests

Tests use bundled fixtures — no Postgres, no internet, no broker keys needed:

```bash
pytest -v
```

## Status

| Phase | Status |
|-------|--------|
| 0. Scaffolding | ✓ |
| 1. Data pipeline | ✓ |
| 2. Features + cost model | ✓ |
| 3. Backtester + strategy discovery | ✓ |
| 4. Regime classifier | deferred |
| 5. Risk engine | deferred |
| 6. Broker + execution | deferred |
| 7. Paper trading | deferred |
| 8. Live deployment | deferred |
| 9. Mobile app | deferred |
