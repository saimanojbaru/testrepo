"""Two-tier cache: fast in-process TTL cache + SQLite-backed persistent cache.

Use ``cached(...)`` as a decorator on async functions. The decorator hashes
positional/keyword args into the cache key, so callers don't need to manage
keys manually.
"""
from __future__ import annotations

import functools
import hashlib
import inspect
import json
from typing import Any, Awaitable, Callable, Optional

from cachetools import TTLCache

from .config import settings
from .database import disk_cache_get, disk_cache_set


_memory = TTLCache(maxsize=2048, ttl=settings.cache_ttl_seconds)


def _make_key(prefix: str, args: tuple, kwargs: dict) -> str:
    raw = json.dumps({"a": args, "k": kwargs}, default=str, sort_keys=True)
    digest = hashlib.sha1(raw.encode()).hexdigest()[:16]
    return f"{prefix}:{digest}"


def cached(prefix: str, *, persist: bool = True,
           memory_ttl: Optional[int] = None,
           disk_ttl: Optional[int] = None) -> Callable:
    """Decorator: memoize an async function in memory (and optionally on disk)."""
    mem_ttl = memory_ttl or settings.cache_ttl_seconds
    dsk_ttl = disk_ttl or settings.long_cache_ttl_seconds

    def decorator(fn: Callable[..., Awaitable[Any]]):
        if not inspect.iscoroutinefunction(fn):
            raise TypeError("cached() requires an async function")

        @functools.wraps(fn)
        async def wrapper(*args, **kwargs):
            key = _make_key(prefix, args, kwargs)
            if key in _memory:
                return _memory[key]
            if persist:
                disk_value = disk_cache_get(key)
                if disk_value is not None:
                    _memory[key] = disk_value
                    return disk_value
            value = await fn(*args, **kwargs)
            if value is not None:
                _memory[key] = value
                if persist:
                    disk_cache_set(key, value, dsk_ttl)
            return value

        wrapper.cache_clear = _memory.clear  # type: ignore[attr-defined]
        return wrapper

    # Allow both @cached("x") and @cached("x", persist=False) usage
    return decorator


def memory_stats() -> dict:
    return {
        "memory_size": len(_memory),
        "memory_maxsize": _memory.maxsize,
        "memory_ttl": settings.cache_ttl_seconds,
    }


def clear_memory() -> None:
    _memory.clear()
