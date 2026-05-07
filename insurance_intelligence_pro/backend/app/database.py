"""SQLite persistence layer used for long-term caching of fetched filings.

Schema is intentionally tiny — most state lives in memory, but disk-backed
caching keeps the app responsive when SEC EDGAR is slow or rate-limited.
"""
from __future__ import annotations

import json
import sqlite3
import threading
import time
from contextlib import contextmanager
from typing import Any, Iterator, Optional

from .config import DB_PATH


_LOCK = threading.Lock()


SCHEMA = """
CREATE TABLE IF NOT EXISTS cache_entries (
    cache_key TEXT PRIMARY KEY,
    payload   TEXT NOT NULL,
    expires_at INTEGER NOT NULL,
    created_at INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_cache_expires ON cache_entries(expires_at);

CREATE TABLE IF NOT EXISTS company_index (
    cik         TEXT PRIMARY KEY,
    ticker      TEXT NOT NULL,
    name        TEXT NOT NULL,
    sic         TEXT,
    insurer_type TEXT,
    updated_at  INTEGER NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_company_ticker ON company_index(ticker);
CREATE INDEX IF NOT EXISTS idx_company_name ON company_index(name);

CREATE TABLE IF NOT EXISTS analysis_history (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    cik TEXT NOT NULL,
    fiscal_year INTEGER,
    payload TEXT NOT NULL,
    created_at INTEGER NOT NULL
);
"""


@contextmanager
def connect() -> Iterator[sqlite3.Connection]:
    conn = sqlite3.connect(DB_PATH, timeout=10, isolation_level=None)
    conn.row_factory = sqlite3.Row
    conn.execute("PRAGMA journal_mode=WAL;")
    conn.execute("PRAGMA synchronous=NORMAL;")
    try:
        yield conn
    finally:
        conn.close()


def init_db() -> None:
    DB_PATH.parent.mkdir(parents=True, exist_ok=True)
    with _LOCK, connect() as conn:
        conn.executescript(SCHEMA)


def disk_cache_get(key: str) -> Optional[Any]:
    now = int(time.time())
    with connect() as conn:
        row = conn.execute(
            "SELECT payload FROM cache_entries WHERE cache_key=? AND expires_at>?",
            (key, now),
        ).fetchone()
    if not row:
        return None
    try:
        return json.loads(row["payload"])
    except json.JSONDecodeError:
        return None


def disk_cache_set(key: str, value: Any, ttl_seconds: int) -> None:
    now = int(time.time())
    payload = json.dumps(value, default=str)
    with _LOCK, connect() as conn:
        conn.execute(
            "INSERT OR REPLACE INTO cache_entries"
            " (cache_key, payload, expires_at, created_at) VALUES (?,?,?,?)",
            (key, payload, now + ttl_seconds, now),
        )


def disk_cache_purge_expired() -> int:
    now = int(time.time())
    with _LOCK, connect() as conn:
        cur = conn.execute("DELETE FROM cache_entries WHERE expires_at<=?", (now,))
        return cur.rowcount or 0


def upsert_company(cik: str, ticker: str, name: str,
                   sic: Optional[str], insurer_type: Optional[str]) -> None:
    now = int(time.time())
    with _LOCK, connect() as conn:
        conn.execute(
            "INSERT OR REPLACE INTO company_index"
            " (cik, ticker, name, sic, insurer_type, updated_at) VALUES (?,?,?,?,?,?)",
            (cik, ticker.upper(), name, sic, insurer_type, now),
        )


def find_company(query: str) -> Optional[dict]:
    q = query.strip().upper()
    with connect() as conn:
        row = conn.execute(
            "SELECT * FROM company_index WHERE ticker=? LIMIT 1", (q,),
        ).fetchone()
        if not row:
            row = conn.execute(
                "SELECT * FROM company_index WHERE UPPER(name) LIKE ? "
                "ORDER BY length(name) ASC LIMIT 1",
                (f"%{q}%",),
            ).fetchone()
    return dict(row) if row else None


def record_analysis(cik: str, fiscal_year: Optional[int], payload: dict) -> None:
    now = int(time.time())
    with _LOCK, connect() as conn:
        conn.execute(
            "INSERT INTO analysis_history (cik, fiscal_year, payload, created_at)"
            " VALUES (?,?,?,?)",
            (cik, fiscal_year, json.dumps(payload, default=str), now),
        )
