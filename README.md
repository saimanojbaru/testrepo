# Autonomous Scalping Agent for Indian F&O

An intelligent trading agent that discovers and executes scalping strategies on Indian options (Nifty, Bank Nifty, commodities, stock options) with real-time cost awareness.

## Architecture

- **Cost-first design:** Every signal is gated by profitability vs. transaction costs
- **Strategy discovery:** Walk-forward optimization finds rules that work, not pre-baked playbooks
- **Regime-aware execution:** Intelligent switching between multiple discovered strategies
- **Paper training:** Live-tick paper trading to stress-test before real capital
- **Broker-agnostic:** Works with Upstox (primary), Zerodha Kite (fallback)

## Roadmap

### Phase 0: ✅ Scaffolding
- Repo structure, config, Docker, `pyproject.toml`
- Cost model (Zerodha/Upstox fee schedules)
- Event-driven backtester
- Technical indicators

### Phase 1: Free Data Ingestion (Weeks 2–3)
- NSE bhavcopy (daily EOD)
- Quandl API (historical options)
- Upstox historical candles
- LiveWebSocket ingestion

### Phase 2 + 5: Features & Backtesting (Weeks 3–7)
- 30+ technical/options/microstructure features
- Walk-forward validation engine
- Slippage modeling

### Phase 3: Strategy Discovery (Weeks 4–7)
- Optuna/genetic search over feature combinations
- Gate: Sharpe > 1.5 net-of-costs

### Phase 4: Regime Classifier (Weeks 6–7)
- XGBoost trained on backtest P&L labels
- Real-time regime selection

### Phase 6: Risk Engine (Weeks 8–9)
- Kelly sizing, daily loss halt, position limits

### Phase 7: Broker Adapter + Paper (Weeks 9–10)
- Upstox integration, paper execution

### Phase 8: Paper Trading (Weeks 8–12)
- Live-tick paper training gate

### Phase 9: Live Deployment (Week 13+)
- Small capital (₹50k–100k), 1 lot, 1 instrument

## Quick Start

```bash
# Install deps
pip install -e .

# Run cost tests (validates fee schedules)
python tests/test_costs.py

# Run backtest tests
python tests/test_backtest.py

# Start services (PostgreSQL + Grafana + Prometheus)
docker-compose up -d
```

## Key Files

| Path | Purpose |
|------|---------|
| `config/settings.py` | Env-driven configuration |
| `backtest/costs.py` | Cost model (Zerodha/Upstox) |
| `backtest/engine.py` | Event-driven backtester |
| `features/technical.py` | Technical indicators |
| `data/ingest/` | Data pipelines (NSE, Quandl, Upstox) |
| `strategies/discover.py` | Strategy discovery via Optuna |
| `regime/classifier.py` | Regime detection (XGBoost) |
| `risk/` | Position sizing, limits, kill-switch |
| `broker/upstox.py` + `kite.py` | Broker adapters |
| `execution/` | Order management + paper fills |
| `tests/` | Unit + integration tests |

## Design Principles

1. **Cost profitability first:** Every trade must beat costs by ≥1.5x margin
2. **Learn from data:** Strategies discovered via backtesting, not assumptions
3. **Rigorous validation:** Walk-forward testing, paper trading gate before live
4. **Deterministic:** All features testable with fixtures
5. **Transparent:** Detailed cost breakdown, P&L reconciliation

## Next Steps

See the full plan at `/root/.claude/plans/so-idea-is-to-merry-shore.md`.

**Immediate:** Phase 1 data ingestion (free sources: NSE, Quandl).
