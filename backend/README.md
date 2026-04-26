# Scalping Backend

Real-time signal engine, event-driven backtester, strategy lab, risk manager,
paper trader, **CA-precision reconciliation + audit trail**, multi-persona
Claude analyst, Telegram reporter, and a **pluggable data-connector
framework** (Fincept-style) for the Indian F&O scalping agent.

## What's new in this revision (Fincept-inspired)

| Addition | Where | Purpose |
|---|---|---|
| `DataConnector` ABC + registry | `app/connectors/` | Pluggable sources: Upstox V3 WS, Yahoo poll, CSV replay |
| Indian F&O cost calculator | `app/costs/india_fno.py` | Paisa-precision STT / brokerage / GST / SEBI / stamp duty |
| Reconciliation engine | `app/audit/reconciliation.py` | Diff broker contract note vs internal ledger |
| Audit-trail event log | `app/audit/audit_log.py` | Append-only record of every signal, fill, risk decision |
| Multi-persona analyst | `app/reporting/personas.py` | Technical / Risk / Reconciliation / Quant analyst lenses |
| Connector + audit API | `app/api/routes_connectors.py`, `routes_audit.py` | `/connectors`, `/costs/*`, `/reconciliation/upload`, `/audit-trail*`, `/personas/*` |

The Flutter mobile app (in `../mobile_app`) consumes this backend over
REST + WebSocket.

## Layout

```
backend/
├── app/
│   ├── main.py              # FastAPI entrypoint + lifespan wiring
│   ├── config.py            # Pydantic settings loaded from env
│   ├── scheduler.py         # APScheduler hourly + daily reports
│   ├── domain/
│   │   ├── signals.py       # Tick, Candle, Signal dataclasses
│   │   ├── trades.py        # OpenPosition, ClosedTrade dataclasses
│   │   └── strategies/
│   │       ├── base.py
│   │       ├── momentum_breakout.py
│   │       ├── reversal_scalp.py
│   │       └── range_breakout.py
│   ├── engine/
│   │   ├── tick_processor.py    # 1-min candle aggregator
│   │   ├── signal_engine.py     # ticks → strategies → risk → paper trader
│   │   ├── paper_trader.py      # simulated fills, SL/TP/time exits
│   │   └── risk_manager.py      # trade caps, loss halts, cooldowns
│   ├── backtest/
│   │   ├── engine.py            # event-driven intraday backtester
│   │   ├── metrics.py           # win rate, PF, drawdown, expectancy
│   │   ├── costs.py             # brokerage + slippage + execution delay
│   │   └── data_loader.py       # CSV → Candle list, split-by-day
│   ├── lab/
│   │   └── runner.py            # runs every strategy, ranks by PF
│   ├── upstox/
│   │   └── ws_client.py         # V3 WebSocket tick subscription
│   ├── reporting/
│   │   ├── telegram_bot.py      # hourly + daily Markdown reports
│   │   └── claude_analyst.py    # Anthropic SDK — analysis only
│   ├── db/
│   │   ├── models.py            # SQLAlchemy 2.x ORM
│   │   ├── session.py           # async session factory
│   │   └── repositories.py      # insert signal / trade / lab result
│   └── api/
│       ├── routes_signals.py    # /signals/live, /signals/history, /ws/signals
│       ├── routes_lab.py        # /strategy-lab/run, /strategy-lab/results
│       ├── routes_performance.py# /performance
│       └── deps.py
├── data/sample_ohlcv.csv        # 35 mins of Nifty — for local lab run
├── tests/                       # pytest; 20+ deterministic tests
└── pyproject.toml
```

## Quick start

```bash
cd backend
pip install -e ".[dev]"
pytest                             # all green, no external services needed
uvicorn app.main:app --reload --port 8000
```

Open http://127.0.0.1:8000/docs for the OpenAPI UI.

## Endpoints

| Method | Path                     | Purpose                                   |
|--------|--------------------------|-------------------------------------------|
| GET    | `/signals/live`          | Last 50 signals currently in memory       |
| GET    | `/signals/history`       | DB-persisted signals (paginated `limit`)  |
| WS     | `/ws/signals`            | Streaming push of new signals             |
| POST   | `/strategy-lab/run`      | Run backtests over a CSV + persist + Claude analysis |
| GET    | `/strategy-lab/results`  | Ranking of persisted lab runs             |
| GET    | `/performance`           | Per-strategy net PnL over last N hours    |
| GET    | `/health`                | Liveness probe                            |

### Running the strategy lab

```bash
curl -X POST localhost:8000/strategy-lab/run \
  -H 'content-type: application/json' \
  -d '{"csv_path":"backend/data/sample_ohlcv.csv","instrument":"NIFTY","analyze":true}'
```

Returns per-strategy metrics sorted by profit factor, plus a Claude analysis
if `ANTHROPIC_API_KEY` is set (otherwise a heuristic fallback).

## Strategy interface

Every strategy implements one method:

```python
class Strategy(ABC):
    name: str
    regime: str
    def on_candle(self, candle: Candle) -> Signal | None: ...
```

To add a new strategy:

1. Create `app/domain/strategies/my_strategy.py`
2. Inherit from `Strategy`, implement `on_candle`
3. Add it to `app/domain/strategies/__init__.py` `STRATEGIES = [...]`

The backtester, strategy lab, and signal engine all iterate the registry —
no wiring required in routes or the engine.

## Risk rules

Configured via env vars (see `app/config.py`):

- `RISK_MAX_TRADES_PER_HOUR` (default 6)
- `RISK_CONSECUTIVE_LOSS_HALT` (default 3) → halts until manual reset
- `RISK_DAILY_LOSS_LIMIT` (default ₹2000) → halts for the day
- `RISK_COOLDOWN_SECONDS` (default 180) → pause after each loss

Risk checks are enforced inside `SignalEngine._on_candle_closed` **before**
any paper trade is opened — the engine will emit the signal for the UI but
not execute it when risk rejects.

## Integrations

| Variable | Effect |
|----------|--------|
| `UPSTOX_ACCESS_TOKEN` | Starts the Upstox V3 WebSocket tick subscriber on boot |
| `UPSTOX_SYMBOLS` | Comma-separated instrument keys (default: Nifty50 + BankNifty) |
| `ANTHROPIC_API_KEY` | Enables Claude analysis on `/strategy-lab/run` |
| `CLAUDE_MODEL` | Defaults to `claude-opus-4-7` |
| `TELEGRAM_BOT_TOKEN` + `TELEGRAM_CHAT_ID` | Hourly (minute 5) + daily (16:00 IST) reports |
| `DATABASE_URL` | SQLAlchemy URL (sqlite+aiosqlite by default; swap for postgres+asyncpg in prod) |

All integrations are optional. With none set, the backend still boots, persists
to SQLite, and exposes the lab / backtest APIs.

## Connecting Upstox

Obtain an access token via the Flutter app's OAuth flow (Settings → Connect
Upstox). Copy that token into `.env`:

```
UPSTOX_ACCESS_TOKEN=eyJ...
UPSTOX_SYMBOLS=["NSE_INDEX|Nifty 50","NSE_INDEX|Nifty Bank"]
```

Tokens expire daily at 03:30 IST — re-auth and update the env var each morning.

## Sample Claude output

With metrics like `momentum_breakout` losing ₹450 at 28% WR and
`reversal_scalp` winning ₹320 at 58% WR, the analyst returns:

```markdown
**Best**: `reversal_scalp` (PF 2.1, PnL ₹320, win rate 58%).
**Worst**: `momentum_breakout` (PF 0.7, PnL -₹450).
- `momentum_breakout` flags: low win rate — false breakouts?, poor risk-reward (avg loss > avg profit)

Tuning ideas (MEDIUM risk): require breakouts to close above prior high
by ≥0.4% and within the first 90 min of session when directional drift is
strongest.
```

## Testing

```bash
pytest                             # runs strategies + backtest + engine + risk
pytest tests/test_backtest.py -v   # backtester determinism proof
```

The backtester test replays the sample CSV twice and asserts byte-identical
trade logs — catches any non-determinism in the engine.

## Production path

When ready to go live, these are the swaps:

- `DATABASE_URL` → `postgresql+asyncpg://...` with TimescaleDB for hypertables
- `REDIS_URL` for real-time signal fan-out (currently in-memory broadcast)
- Replace `PaperTrader` with a broker adapter implementing the same
  `apply_signal` / `on_tick` / `manual_close` surface (see `broker/` in the
  root plan)

Nothing in the engine layer depends on SQLite or the paper trader directly —
all boundary types are dataclasses in `domain/`.
