"""Shared async HTTP client.

SEC EDGAR requires a descriptive User-Agent and rate-limits aggressive
clients. We keep a single shared httpx client with HTTP/2 + a reasonable
connection pool so subsequent requests are cheap.
"""
from __future__ import annotations

import asyncio
import logging
from typing import Optional

import httpx

from ..config import settings


_log = logging.getLogger(__name__)
_client: Optional[httpx.AsyncClient] = None
_lock = asyncio.Lock()


async def get_client() -> httpx.AsyncClient:
    global _client
    if _client is not None and not _client.is_closed:
        return _client
    async with _lock:
        if _client is None or _client.is_closed:
            limits = httpx.Limits(
                max_keepalive_connections=settings.max_concurrent_requests,
                max_connections=settings.max_concurrent_requests * 2,
            )
            _client = httpx.AsyncClient(
                http2=False,
                timeout=settings.request_timeout,
                limits=limits,
                headers={
                    "User-Agent": settings.sec_user_agent,
                    "Accept": "application/json, text/html;q=0.9, */*;q=0.5",
                    "Accept-Encoding": "gzip, deflate",
                },
                follow_redirects=True,
            )
    return _client


async def close_client() -> None:
    global _client
    if _client is not None and not _client.is_closed:
        await _client.aclose()
    _client = None


async def fetch_json(url: str, **kwargs) -> Optional[dict]:
    client = await get_client()
    try:
        r = await client.get(url, **kwargs)
        if r.status_code == 200:
            return r.json()
        _log.warning("fetch_json non-200 %s -> %s", url, r.status_code)
    except Exception as e:  # network errors should not crash the app
        _log.warning("fetch_json failed %s: %s", url, e)
    return None


async def fetch_text(url: str, **kwargs) -> Optional[str]:
    client = await get_client()
    try:
        r = await client.get(url, **kwargs)
        if r.status_code == 200:
            return r.text
        _log.warning("fetch_text non-200 %s -> %s", url, r.status_code)
    except Exception as e:
        _log.warning("fetch_text failed %s: %s", url, e)
    return None
