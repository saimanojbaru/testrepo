"""FastAPI application entrypoint.

Run locally:
    uvicorn app.main:app --reload --port 8000

Notes:
* CORS is open by default so the Flutter dev build (running on
  ``http://localhost:<random>`` on Android emulator) can call the API.
* The database is created lazily on startup via ``init_db()``.
"""
from __future__ import annotations

import logging
import time

from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.middleware.gzip import GZipMiddleware
from fastapi.responses import JSONResponse

from .cache import memory_stats
from .config import settings
from .database import disk_cache_purge_expired, init_db
from .routers import company, compare, dashboard, knowledge, standards, updates
from .services.http_client import close_client


logging.basicConfig(level=logging.INFO,
                    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s")
log = logging.getLogger("iip")


app = FastAPI(title=settings.app_name, version=settings.app_version,
              docs_url="/docs", redoc_url=None)


if settings.enable_cors:
    app.add_middleware(
        CORSMiddleware,
        allow_origins=["*"],
        allow_methods=["*"],
        allow_headers=["*"],
        allow_credentials=False,
    )

app.add_middleware(GZipMiddleware, minimum_size=512)


@app.middleware("http")
async def timing_middleware(request: Request, call_next):
    start = time.perf_counter()
    response = await call_next(request)
    elapsed_ms = (time.perf_counter() - start) * 1000
    response.headers["X-Response-Time-ms"] = f"{elapsed_ms:.1f}"
    response.headers["Cache-Control"] = "public, max-age=60"
    return response


@app.on_event("startup")
async def startup() -> None:
    init_db()
    purged = disk_cache_purge_expired()
    log.info("DB ready, purged %s expired entries.", purged)


@app.on_event("shutdown")
async def shutdown() -> None:
    await close_client()


@app.get("/")
async def root() -> dict:
    return {
        "name": settings.app_name,
        "version": settings.app_version,
        "endpoints": [
            "/dashboard/pulse", "/dashboard/top-news",
            "/company/{query}", "/company/search",
            "/compare", "/compare/auto",
            "/updates",
            "/knowledge", "/knowledge/{slug}", "/knowledge/search",
            "/healthz",
        ],
    }


@app.get("/healthz")
async def healthz() -> dict:
    return {"status": "ok", "cache": memory_stats()}


@app.exception_handler(Exception)
async def unhandled_exc(request: Request, exc: Exception):
    log.exception("Unhandled error on %s", request.url)
    return JSONResponse(status_code=500, content={
        "error": "Internal server error",
        "detail": str(exc) if str(exc) else exc.__class__.__name__,
    })


app.include_router(dashboard.router)
app.include_router(company.router)
app.include_router(compare.router)
app.include_router(updates.router)
app.include_router(knowledge.router)
app.include_router(standards.router)
