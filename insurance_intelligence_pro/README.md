# Insurance Intelligence Pro

A premium Bloomberg-lite mobile app for the US insurance industry. Type a
company name; get instant KPI analysis, peer benchmarks, AI-style insights,
industry news, and an accounting knowledge library — all powered by **free**
public data (SEC EDGAR XBRL + public RSS feeds).

```
┌──────────────┬───────────────────────────────────────────────────────┐
│ Tab          │ What it does                                          │
├──────────────┼───────────────────────────────────────────────────────┤
│ Dashboard    │ Market pulse, today's insights, top headlines         │
│ Company      │ Search → KPI cards, risk radar, 5-yr trend, insights  │
│ Compare      │ Pick peers / auto-peers, ranked metric-by-metric      │
│ Updates      │ Categorized industry news with impact scoring         │
│ Knowledge    │ ASC 944, FAS 60/97/133, SAP — GAAP vs STAT view       │
└──────────────┴───────────────────────────────────────────────────────┘
```

---

## Architecture

```
┌─────────────────────┐      HTTPS / JSON        ┌──────────────────────┐
│  Flutter (Android)  │ ───────────────────────▶│  FastAPI (Python)    │
│  • Dark Bloomberg   │                          │  • SEC EDGAR client  │
│    UI, fl_chart,    │ ◀───────────────────────│  • KPI engine        │
│    flutter_animate  │     gzip + cache hints   │  • Insight engine    │
│  • 2-tier client    │                          │  • Peer engine       │
│    cache (mem +     │                          │  • News service      │
│    SharedPrefs)     │                          │  • Knowledge library │
└─────────────────────┘                          └──────────┬───────────┘
                                                            │
                                              ┌─────────────▼─────────────┐
                                              │  SQLite (cache_entries,   │
                                              │  company_index,           │
                                              │  analysis_history)        │
                                              └───────────────────────────┘
```

### Caching strategy (faster API responses)

| Layer       | Technology                  | TTL      |
|-------------|-----------------------------|----------|
| Server mem  | `cachetools.TTLCache`       | 6h       |
| Server disk | SQLite `cache_entries`      | 7d       |
| Client mem  | In-process Dart map         | session  |
| Client disk | `shared_preferences` + TTL  | 5–360min |

The `@cached(...)` decorator on the FastAPI side memoizes async functions in
both layers transparently. SEC EDGAR `companyfacts` payloads are cached
aggressively (7 days) since 10-K filings only land annually.

---

## Project layout

```
insurance_intelligence_pro/
├── backend/
│   ├── app/
│   │   ├── main.py            FastAPI entrypoint, middleware, routes
│   │   ├── config.py          Pydantic settings (env-overridable)
│   │   ├── database.py        SQLite schema + helpers
│   │   ├── cache.py           @cached decorator (mem + disk)
│   │   ├── models/            Pydantic response models
│   │   ├── routers/           dashboard, company, compare, updates, knowledge
│   │   ├── services/
│   │   │   ├── http_client.py        Shared httpx client
│   │   │   ├── ticker_mapper.py      Free-form query → CIK + ticker
│   │   │   ├── sec_edgar.py          SEC XBRL companyfacts fetcher
│   │   │   ├── financial_extractor.py canonicalise into FY-indexed rows
│   │   │   ├── classifier.py         P&C / Life / Health / etc.
│   │   │   ├── kpi_engine.py         Loss/Expense/Combined ratio etc.
│   │   │   ├── insight_engine.py     Rule-based conclusions
│   │   │   ├── peer_engine.py        Peer comparison + ranking
│   │   │   ├── news_service.py       RSS ingestion + impact scoring
│   │   │   └── knowledge_service.py  ASC944, FAS60/97/133, SAP
│   │   └── data/
│   │       ├── tickers.json     curated ticker → CIK fallback
│   │       ├── peers.json       (embedded in tickers.json)
│   │       ├── knowledge.json   accounting standards library
│   │       └── sample_data.json fallback financials + benchmarks
│   ├── requirements.txt
│   └── run.sh                 one-liner to start the API
├── frontend/
│   ├── pubspec.yaml
│   ├── lib/
│   │   ├── main.dart          App entrypoint
│   │   ├── theme/             Dark Bloomberg palette + typography
│   │   ├── models/            Dart models matching API DTOs
│   │   ├── services/          api_service.dart, cache_service.dart
│   │   ├── widgets/           glass cards, kpi_card, sparkline, radar, ring
│   │   ├── screens/           5 tabs + home shell with custom bottom-nav
│   │   └── utils/             formatters
│   └── android/               minimal Gradle scaffolding for APK
└── README.md
```

---

## Running locally

### Backend (FastAPI)

```bash
cd backend
./run.sh             # creates .venv, installs deps, starts uvicorn :8000
# or manually
python3 -m venv .venv && source .venv/bin/activate
pip install -r requirements.txt
uvicorn app.main:app --reload --port 8000
```

Smoke-test:

```bash
curl http://localhost:8000/healthz
curl http://localhost:8000/dashboard/pulse | jq .
curl http://localhost:8000/company/PGR | jq '.score, .headline'
curl http://localhost:8000/compare/auto?q=PGR | jq '.verdict'
```

The first call to a new ticker may take 1–3 seconds (SEC fetch). Subsequent
calls return in ~10ms thanks to the dual-layer cache.

> **Tip.** SEC EDGAR requires a real User-Agent. Override it via env:
> `IIP_SEC_USER_AGENT="YourName your@email.com"`.

### Frontend (Flutter)

Requirements: Flutter 3.19+ (Dart ≥ 3.3), Android SDK + emulator/device.

```bash
cd frontend
flutter pub get
# Run on an emulator / connected device
flutter run
# Or build a release APK
flutter build apk --release
# Output: build/app/outputs/flutter-apk/app-release.apk
```

By default the app talks to:

* `http://10.0.2.2:8000` on the Android emulator (your host's localhost)
* `http://localhost:8000` on web / desktop

For a real device, set the backend URL at runtime:

```dart
// e.g. in main.dart before runApp(...)
ApiService.baseUrlOverride = 'https://your-api.example.com';
```

### Build APK in one command

```bash
cd frontend
flutter build apk --release
```

The APK is at `frontend/build/app/outputs/flutter-apk/app-release.apk` and
installs straight onto a device with `adb install`.

---

## Highlights

### Premium dark UI

* Deep-navy/cyan palette with glow accents on the primary score ring
* Hand-built bottom-nav with morphing pill (`AnimatedContainer`)
* Glass-morphism cards (gradient + hairline border + soft shadow)
* Skeleton loading via `shimmer`, plus `flutter_animate` fade/slide chains
* Animated radar chart, line chart with curved spline, sparklines

### Smart insights (no paid AI)

`insight_engine.py` runs deterministic rules over the KPI set:

```python
if cr.value < 95: positive("Strong underwriting discipline")
elif cr.value < 100: neutral("Underwriting at breakeven")
else: negative("Underwriting losses persisting")
```

Output is **conclusions, not data** — exactly what the spec asked for.

### Zero paid APIs

Every data source is free:

* **SEC EDGAR** — XBRL `companyfacts` + `submissions` (no key required)
* **RSS** — Insurance Journal, Reinsurance News, Artemis, AM Best, NAIC, SEC
* **Curated fallbacks** — `data/sample_data.json` keeps the app responsive
  even fully offline

### Resilient by design

* Every external call routed through `fetch_json` / `fetch_text` which
  swallow network errors and log them — the response payload is `None`,
  the caller falls back to local data.
* Two-year fiscal history threshold: if SEC returns sparse data, we hand
  back a curated fallback flagged with `is_estimated: true`.
* Health endpoint at `/healthz` exposes cache stats for monitoring.

---

## Endpoints (cheat-sheet)

| Method | Path                          | Notes                                      |
|--------|-------------------------------|--------------------------------------------|
| GET    | `/`                           | Service descriptor                         |
| GET    | `/healthz`                    | Health + cache stats                       |
| GET    | `/dashboard/pulse`            | Headline + insights + indicators           |
| GET    | `/dashboard/top-news?limit=N` | Categorized news for the home screen       |
| GET    | `/company/search?q=…`         | Free-form lookup (returns suggestions)     |
| GET    | `/company/{ticker_or_name}`   | Full analysis: KPIs, radar, insights       |
| GET    | `/company/{q}/summary`        | Lightweight summary card                   |
| POST   | `/compare`                    | Body: `{"companies": ["PGR","TRV"]}`       |
| GET    | `/compare/auto?q=PGR`         | Picks peer group automatically             |
| GET    | `/updates?category=…&limit=…` | Industry news feed                         |
| GET    | `/knowledge?framework=…`      | Article list                               |
| GET    | `/knowledge/search?q=…`       | Full-text search                           |
| GET    | `/knowledge/{slug}`           | Single article                             |

OpenAPI / Swagger UI: `http://localhost:8000/docs`.

---

## Database schema

```sql
-- Generic key-value cache (used by @cached(persist=True))
cache_entries(cache_key TEXT PRIMARY KEY,
              payload TEXT, expires_at INTEGER, created_at INTEGER);

-- Company resolution (warmed lazily as users search)
company_index(cik TEXT PRIMARY KEY,
              ticker, name, sic, insurer_type, updated_at);

-- Audit trail of analyses (drives future "recently viewed" UI)
analysis_history(id INTEGER PK,
                 cik, fiscal_year, payload JSON, created_at);
```

---

## Performance optimizations

1. **GZip middleware** on FastAPI for any response > 512 bytes.
2. **HTTP client reuse** — single `httpx.AsyncClient` per process.
3. **Mem + disk caching** on every external call (SEC, RSS).
4. **TTL cache on the client** so re-opening the app doesn't refetch.
5. **`@cached(persist=True)` on company analysis** so the second pull on
   the same ticker hits SQLite instead of SEC.
6. **Parallel fan-out in peer comparison** via `asyncio.gather`.
7. **Lazy-loaded JSON fixtures** with `functools.lru_cache`.
8. **Animated Material widgets** chosen for cheap GPU paths
   (`AnimatedContainer`, fl_chart's built-in tweens).
9. **`IndexedStack` in the home shell** so tab pages keep state on switch.

---

## Roadmap

* Hugging Face inference for free LLM-style insights (optional)
* iOS support (the Flutter app is platform-agnostic; only Android is wired)
* In-app webview for news links
* Persisted "watchlist" with pull-to-refresh + push notifications
