"""
SpotifyFetcher: Authenticates with Spotify API and extracts track metadata.

To use this module, set your Spotify credentials:
  - SPOTIFY_CLIENT_ID:     Your app's Client ID from https://developer.spotify.com/dashboard
  - SPOTIFY_CLIENT_SECRET: Your app's Client Secret from the same dashboard
"""

import os
import re
import logging
from dataclasses import dataclass
from typing import Optional

import spotipy
from spotipy.oauth2 import SpotifyClientCredentials

logger = logging.getLogger(__name__)


# ─── INSERT YOUR CREDENTIALS HERE (or pass via environment variables) ────────
SPOTIFY_CLIENT_ID = os.environ.get("SPOTIFY_CLIENT_ID", "YOUR_CLIENT_ID_HERE")
SPOTIFY_CLIENT_SECRET = os.environ.get("SPOTIFY_CLIENT_SECRET", "YOUR_CLIENT_SECRET_HERE")
# ─────────────────────────────────────────────────────────────────────────────


@dataclass
class TrackMetadata:
    track_id: str
    track_name: str
    artist_name: str
    album_name: str
    bpm: Optional[float]
    key: Optional[int]       # Pitch class: 0=C, 1=C#, 2=D … 11=B, -1=unknown
    mode: Optional[int]      # 0=minor, 1=major
    duration_ms: int

    # Friendly key label, e.g. "C# Major"
    @property
    def key_label(self) -> str:
        if self.key is None or self.key < 0:
            return "Unknown"
        names = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]
        mode_label = "Major" if self.mode == 1 else "Minor"
        return f"{names[self.key]} {mode_label}"

    def __str__(self) -> str:
        bpm_str = f"{self.bpm:.1f}" if self.bpm else "N/A"
        return (
            f"'{self.track_name}' by {self.artist_name} | "
            f"BPM: {bpm_str} | Key: {self.key_label}"
        )


class SpotifyFetcher:
    """Wraps the Spotify API to fetch track metadata and audio features."""

    _TRACK_URL_RE = re.compile(
        r"https?://open\.spotify\.com/track/([A-Za-z0-9]+)"
    )

    #: True when Spotify credentials were successfully loaded.
    available: bool = False

    def __init__(
        self,
        client_id: str = SPOTIFY_CLIENT_ID,
        client_secret: str = SPOTIFY_CLIENT_SECRET,
    ) -> None:
        if client_id in ("", "YOUR_CLIENT_ID_HERE") or client_secret in ("", "YOUR_CLIENT_SECRET_HERE"):
            logger.warning(
                "Spotify credentials not set — Spotify features will be unavailable. "
                "Set SPOTIFY_CLIENT_ID and SPOTIFY_CLIENT_SECRET env vars."
            )
            self._sp = None
            self.available = False
            return
        try:
            auth = SpotifyClientCredentials(
                client_id=client_id,
                client_secret=client_secret,
            )
            self._sp = spotipy.Spotify(auth_manager=auth)
            self.available = True
            logger.debug("Spotify client initialised.")
        except Exception as exc:
            logger.warning("Spotify client init failed: %s", exc)
            self._sp = None
            self.available = False

    # ── public ────────────────────────────────────────────────────────────────

    def fetch(self, spotify_url: str) -> TrackMetadata:
        """Return a TrackMetadata for the given Spotify track URL."""
        if not self.available or self._sp is None:
            raise RuntimeError(
                "Spotify credentials not configured. "
                "Set SPOTIFY_CLIENT_ID and SPOTIFY_CLIENT_SECRET env vars."
            )
        track_id = self._parse_track_id(spotify_url)
        logger.info("Fetching Spotify metadata for track ID: %s", track_id)

        track = self._get_track(track_id)
        features = self._get_audio_features(track_id)

        metadata = TrackMetadata(
            track_id=track_id,
            track_name=track["name"],
            artist_name=track["artists"][0]["name"],
            album_name=track["album"]["name"],
            bpm=features.get("tempo") if features else None,
            key=features.get("key") if features else None,
            mode=features.get("mode") if features else None,
            duration_ms=track["duration_ms"],
        )
        logger.info("Fetched: %s", metadata)
        return metadata

    # ── private ───────────────────────────────────────────────────────────────

    def _parse_track_id(self, url: str) -> str:
        m = self._TRACK_URL_RE.search(url)
        if not m:
            raise ValueError(
                f"Not a valid Spotify track URL: '{url}'\n"
                "Expected format: https://open.spotify.com/track/<id>"
            )
        return m.group(1)

    def _get_track(self, track_id: str) -> dict:
        try:
            return self._sp.track(track_id)
        except spotipy.SpotifyException as exc:
            raise RuntimeError(
                f"Spotify API error fetching track '{track_id}': {exc}"
            ) from exc

    def _get_audio_features(self, track_id: str) -> Optional[dict]:
        try:
            results = self._sp.audio_features([track_id])
            if results and results[0]:
                return results[0]
            logger.warning("No audio features returned for %s.", track_id)
            return None
        except spotipy.SpotifyException as exc:
            logger.warning("Could not fetch audio features: %s", exc)
            return None
