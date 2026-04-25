"""
Search adapter — finds tracks on Spotify and YouTube by free-text query.

Credentials are read from environment variables:
  SPOTIFY_CLIENT_ID / SPOTIFY_CLIENT_SECRET  — Spotify Developer app
  YOUTUBE_API_KEY                             — Google Cloud, YouTube Data API v3

Both are optional: if a credential is absent the source is skipped and the
caller gets an empty list for that source (never a crash).
"""

from __future__ import annotations

import base64
import json
import logging
import os
import re
import urllib.error
import urllib.parse
import urllib.request
from dataclasses import dataclass
from typing import Optional

logger = logging.getLogger(__name__)

TIMEOUT = 10  # seconds per HTTP call


@dataclass
class SearchResult:
    id: str
    title: str
    artist: str
    duration_ms: int
    thumbnail_url: str
    preview_url: Optional[str]
    source: str   # "spotify" | "youtube"
    url: str


# ── Spotify ───────────────────────────────────────────────────────────────────

def search_spotify(query: str, limit: int = 8) -> list[SearchResult]:
    client_id = os.getenv("SPOTIFY_CLIENT_ID", "")
    client_secret = os.getenv("SPOTIFY_CLIENT_SECRET", "")
    if not client_id or not client_secret:
        logger.warning("Spotify credentials not set — skipping Spotify search")
        return []
    try:
        token = _spotify_client_token(client_id, client_secret)
        return _spotify_search(query, limit, token)
    except Exception as exc:
        logger.warning("Spotify search failed: %s", exc)
        return []


def _spotify_client_token(client_id: str, client_secret: str) -> str:
    creds = base64.b64encode(f"{client_id}:{client_secret}".encode()).decode()
    req = urllib.request.Request(
        "https://accounts.spotify.com/api/token",
        data=b"grant_type=client_credentials",
        headers={
            "Authorization": f"Basic {creds}",
            "Content-Type": "application/x-www-form-urlencoded",
        },
    )
    with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
        return json.loads(resp.read().decode())["access_token"]


def _spotify_search(query: str, limit: int, token: str) -> list[SearchResult]:
    q = urllib.parse.quote_plus(query)
    req = urllib.request.Request(
        f"https://api.spotify.com/v1/search?q={q}&type=track&limit={limit}",
        headers={"Authorization": f"Bearer {token}"},
    )
    with urllib.request.urlopen(req, timeout=TIMEOUT) as resp:
        data = json.loads(resp.read().decode())

    out: list[SearchResult] = []
    for item in data.get("tracks", {}).get("items", []):
        images = item.get("album", {}).get("images", [])
        out.append(SearchResult(
            id=item["id"],
            title=item["name"],
            artist=", ".join(a["name"] for a in item.get("artists", [])),
            duration_ms=item.get("duration_ms", 0),
            thumbnail_url=images[0]["url"] if images else "",
            preview_url=item.get("preview_url"),
            source="spotify",
            url=item.get("external_urls", {}).get("spotify", ""),
        ))
    return out


# ── YouTube ───────────────────────────────────────────────────────────────────

def search_youtube(query: str, limit: int = 8) -> list[SearchResult]:
    api_key = os.getenv("YOUTUBE_API_KEY", "")
    if not api_key:
        logger.warning("YOUTUBE_API_KEY not set — skipping YouTube search")
        return []
    try:
        return _youtube_search(query, limit, api_key)
    except Exception as exc:
        logger.warning("YouTube search failed: %s", exc)
        return []


def _youtube_search(query: str, limit: int, api_key: str) -> list[SearchResult]:
    q = urllib.parse.quote_plus(f"{query} official audio")
    search_url = (
        "https://www.googleapis.com/youtube/v3/search"
        f"?part=snippet&q={q}&type=video&videoCategoryId=10"
        f"&maxResults={limit}&key={api_key}"
    )
    with urllib.request.urlopen(search_url, timeout=TIMEOUT) as resp:
        data = json.loads(resp.read().decode())

    items = data.get("items", [])
    if not items:
        return []

    video_ids = ",".join(item["id"]["videoId"] for item in items)
    detail_url = (
        "https://www.googleapis.com/youtube/v3/videos"
        f"?part=contentDetails&id={video_ids}&key={api_key}"
    )
    with urllib.request.urlopen(detail_url, timeout=TIMEOUT) as resp:
        details = json.loads(resp.read().decode())

    dur_map: dict[str, int] = {}
    for v in details.get("items", []):
        dur_map[v["id"]] = _parse_iso_duration_ms(v["contentDetails"]["duration"])

    out: list[SearchResult] = []
    for item in items:
        vid_id = item["id"]["videoId"]
        snippet = item.get("snippet", {})
        thumb = snippet.get("thumbnails", {}).get("medium", {}).get("url", "")
        out.append(SearchResult(
            id=vid_id,
            title=snippet.get("title", ""),
            artist=snippet.get("channelTitle", ""),
            duration_ms=dur_map.get(vid_id, 0),
            thumbnail_url=thumb,
            preview_url=None,
            source="youtube",
            url=f"https://www.youtube.com/watch?v={vid_id}",
        ))
    return out


_ISO_RE = re.compile(r"PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+)S)?")


def _parse_iso_duration_ms(duration: str) -> int:
    m = _ISO_RE.match(duration)
    if not m:
        return 0
    h, mn, s = (int(x or 0) for x in m.groups())
    return (h * 3600 + mn * 60 + s) * 1000


# ── Combined ──────────────────────────────────────────────────────────────────

def search(query: str, source: str = "spotify", limit: int = 8) -> list[SearchResult]:
    """
    source: "spotify" | "youtube" | "both"
    "both" returns Spotify results first, then YouTube (deduped by title).
    """
    if source == "both":
        sp = search_spotify(query, limit)
        yt = search_youtube(query, limit)
        seen = {(r.title.lower(), r.artist.lower()) for r in sp}
        deduped = [r for r in yt if (r.title.lower(), r.artist.lower()) not in seen]
        return (sp + deduped)[:limit]
    if source == "youtube":
        return search_youtube(query, limit)
    return search_spotify(query, limit)
