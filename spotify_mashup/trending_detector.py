"""
TrendingHookDetector: Identifies the most viral / shareable segments of a song.

A "viral hook" is a 15-30 second slice that listeners are most likely to
recognise, sing along to, or use in a short-form video.  We score every
candidate region with a weighted blend of signals:

    score = w_spotify * spotify_section_score
          + w_tiktok  * tiktok_clip_density      (if available)
          + w_youtube * youtube_timestamp_density (if available)
          + w_lyric   * lyric_chorus_score       (if available)

Each signal is normalised to 0-1 before combination, so the absence of any
signal degrades gracefully — Spotify alone still produces sensible results.

The Spotify signal uses the AudioAnalysis API's segment/section/beat data:
high-confidence boundaries with rising loudness and stable timbre tend to
align with choruses and drops.

External signals (TikTok, YouTube comments) are best-effort scrapes; failures
are caught and treated as "signal unavailable", never fatal.
"""

from __future__ import annotations

import json
import logging
import math
import re
import urllib.parse
import urllib.request
from dataclasses import asdict, dataclass, field
from typing import Optional

logger = logging.getLogger(__name__)


# ── Public data model ────────────────────────────────────────────────────────

@dataclass
class ViralHook:
    """A candidate hook within a track, ranked by virality."""
    start_ms: int
    end_ms: int
    score: float                # 0-100 final virality score
    confidence: float           # 0-1 — how reliable this score is
    label: str                  # e.g. "Chorus", "Drop", "Hook"
    reasons: list[str] = field(default_factory=list)
    signals: dict[str, float] = field(default_factory=dict)

    @property
    def duration_ms(self) -> int:
        return self.end_ms - self.start_ms

    def as_dict(self) -> dict:
        d = asdict(self)
        d["duration_ms"] = self.duration_ms
        return d


# ── Detector ─────────────────────────────────────────────────────────────────

class TrendingHookDetector:
    """
    Builds a ranked list of viral hooks for a single track.

    Parameters
    ----------
    spotify_client : spotipy.Spotify
        An authenticated client (see SpotifyFetcher).
    min_hook_seconds, max_hook_seconds : float
        Bounds on hook duration. Spotify sections that are too short get
        merged with the next; sections that are too long get trimmed
        around their loudness peak.
    """

    _DEFAULT_WEIGHTS = {
        "spotify": 0.55,
        "tiktok": 0.25,
        "youtube": 0.15,
        "lyric": 0.05,
    }

    def __init__(
        self,
        spotify_client,
        *,
        min_hook_seconds: float = 12.0,
        max_hook_seconds: float = 30.0,
        weights: Optional[dict[str, float]] = None,
    ) -> None:
        self._sp = spotify_client
        self.min_ms = int(min_hook_seconds * 1000)
        self.max_ms = int(max_hook_seconds * 1000)
        self.weights = weights or self._DEFAULT_WEIGHTS

    # ── Public ────────────────────────────────────────────────────────────────

    def detect(self, track_id: str, *, top_k: int = 5) -> list[ViralHook]:
        """Return the top-k ranked hooks for a Spotify track."""
        analysis = self._fetch_analysis(track_id)
        sections = analysis.get("sections", []) or []
        if not sections:
            logger.warning("No sections returned for %s — using fallback", track_id)
            return self._fallback(analysis)

        candidates = self._build_candidates(analysis, sections)

        # External signals — best-effort, never fatal.
        track = self._sp.track(track_id)
        title = track["name"]
        artist = track["artists"][0]["name"]

        tiktok = self._tiktok_density(title, artist, analysis.get("track", {}).get("duration", 0))
        youtube = self._youtube_density(title, artist, analysis.get("track", {}).get("duration", 0))
        lyric = self._lyric_chorus_density(title, artist, analysis.get("track", {}).get("duration", 0))

        for hook in candidates:
            self._mix_external(hook, tiktok, youtube, lyric)

        candidates.sort(key=lambda h: h.score, reverse=True)
        return candidates[:top_k]

    # ── Spotify analysis ──────────────────────────────────────────────────────

    def _fetch_analysis(self, track_id: str) -> dict:
        try:
            return self._sp.audio_analysis(track_id) or {}
        except Exception as e:
            logger.warning("audio_analysis failed for %s: %s", track_id, e)
            return {}

    def _build_candidates(self, analysis: dict, sections: list[dict]) -> list[ViralHook]:
        track_meta = analysis.get("track", {})
        track_loud = float(track_meta.get("loudness", -10.0))
        track_dur = float(track_meta.get("duration", 0.0))

        # Normalise raw section signals into 0-1.
        loud_vals = [float(s.get("loudness", track_loud)) for s in sections]
        if loud_vals:
            l_max = max(loud_vals)
            l_min = min(loud_vals)
            l_span = max(l_max - l_min, 1e-6)
        else:
            l_min, l_span = -60.0, 60.0

        out: list[ViralHook] = []
        for i, sec in enumerate(sections):
            start = float(sec.get("start", 0.0))
            duration = float(sec.get("duration", 0.0))
            end = start + duration
            if duration < 4.0:
                continue  # too short to be a useful hook

            loud = float(sec.get("loudness", track_loud))
            tempo_conf = float(sec.get("tempo_confidence", 0.0))
            key_conf = float(sec.get("key_confidence", 0.0))
            time_sig_conf = float(sec.get("time_signature_confidence", 0.0))

            # Loudness vs track average → energy peaks score higher.
            loudness_norm = (loud - l_min) / l_span
            # Late-track sections (final chorus / drop) tend to be the "biggest moment".
            position = (start + duration / 2.0) / max(track_dur, 1.0)
            position_boost = 0.6 + 0.4 * math.sin(math.pi * position)  # 0.6..1.0..0.6
            confidence = (tempo_conf + key_conf + time_sig_conf) / 3.0

            # Bigger jump in loudness vs the previous section ⇒ likely a drop / chorus entry.
            prev_loud = float(sections[i - 1].get("loudness", loud)) if i > 0 else loud
            delta = max(loud - prev_loud, 0.0)
            delta_norm = min(delta / 6.0, 1.0)  # +6 dB jump ≈ "huge"

            spotify_score = (
                0.45 * loudness_norm
                + 0.30 * delta_norm
                + 0.15 * position_boost
                + 0.10 * confidence
            )

            label, reasons = self._classify(loudness_norm, delta_norm, position)

            # Trim to the loudest 12-30s window if the section is too long.
            s_ms, e_ms = self._trim_to_window(start, end, analysis)

            hook = ViralHook(
                start_ms=s_ms,
                end_ms=e_ms,
                score=spotify_score * 100.0,
                confidence=confidence,
                label=label,
                reasons=reasons,
                signals={"spotify": spotify_score},
            )
            out.append(hook)

        # De-duplicate near-identical windows.
        return self._dedupe(out)

    def _trim_to_window(self, start: float, end: float, analysis: dict) -> tuple[int, int]:
        duration_s = end - start
        if duration_s * 1000 <= self.max_ms:
            return int(start * 1000), int(end * 1000)
        # Pick the loudest 24-second sub-window using segment-level loudness.
        target = 24.0
        segments = [
            seg for seg in analysis.get("segments", []) or []
            if start <= seg.get("start", 0.0) < end
        ]
        if not segments:
            return int(start * 1000), int((start + target) * 1000)
        # Sliding-window peak loudness.
        best_start, best_loud = start, -1e9
        for seg in segments:
            t = float(seg.get("start", start))
            if t + target > end:
                break
            window = [
                s for s in segments
                if t <= s.get("start", 0.0) < t + target
            ]
            avg = sum(float(s.get("loudness_max", -60.0)) for s in window) / max(len(window), 1)
            if avg > best_loud:
                best_loud = avg
                best_start = t
        return int(best_start * 1000), int((best_start + target) * 1000)

    def _classify(self, loudness_norm: float, delta_norm: float, position: float) -> tuple[str, list[str]]:
        reasons: list[str] = []
        if delta_norm > 0.6:
            reasons.append("sharp loudness jump from previous section")
        if loudness_norm > 0.8:
            reasons.append("near peak loudness of the track")
        if 0.55 < position < 0.85:
            reasons.append("late-track climax position")
        if not reasons:
            reasons.append("mid-track section with stable energy")

        if delta_norm > 0.65 and loudness_norm > 0.75:
            return "Drop", reasons
        if loudness_norm > 0.80:
            return "Chorus", reasons
        if 0.45 < position < 0.70:
            return "Hook", reasons
        return "Section", reasons

    def _dedupe(self, hooks: list[ViralHook]) -> list[ViralHook]:
        kept: list[ViralHook] = []
        for h in hooks:
            if any(abs(h.start_ms - k.start_ms) < 4000 for k in kept):
                continue
            kept.append(h)
        return kept

    # ── External signals (best-effort, no API keys required) ─────────────────

    def _http_get(self, url: str, *, timeout: float = 6.0) -> Optional[str]:
        req = urllib.request.Request(
            url,
            headers={
                "User-Agent": (
                    "Mozilla/5.0 (Linux; Android 14) "
                    "AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124"
                ),
                "Accept": "text/html,application/xhtml+xml,*/*;q=0.8",
                "Accept-Language": "en-US,en;q=0.9",
            },
        )
        try:
            with urllib.request.urlopen(req, timeout=timeout) as resp:
                return resp.read().decode("utf-8", errors="ignore")
        except Exception as e:
            logger.debug("HTTP get failed for %s: %s", url, e)
            return None

    def _tiktok_density(self, title: str, artist: str, duration_s: float) -> Optional[list[float]]:
        """
        Returns a density curve over the song's duration (one float per ~1s
        bucket) where higher values = more TikTok clips reference that part
        of the song. Returns None if no signal can be obtained.
        """
        if duration_s <= 0:
            return None
        query = urllib.parse.quote_plus(f"{title} {artist}")
        # TikTok's discover endpoint returns JSON-in-HTML; we just look for
        # any time markers like "0:23" / "1:12" inside captions/comments.
        html = self._http_get(f"https://www.tiktok.com/search?q={query}")
        if not html:
            return None
        timestamps = self._extract_timestamps(html, duration_s)
        if not timestamps:
            return None
        return self._density_curve(timestamps, duration_s)

    def _youtube_density(self, title: str, artist: str, duration_s: float) -> Optional[list[float]]:
        if duration_s <= 0:
            return None
        query = urllib.parse.quote_plus(f"{title} {artist} official audio")
        html = self._http_get(f"https://www.youtube.com/results?search_query={query}")
        if not html:
            return None
        # YouTube comment timestamps appear in the page initial-data JSON.
        timestamps = self._extract_timestamps(html, duration_s)
        if not timestamps:
            return None
        return self._density_curve(timestamps, duration_s)

    def _lyric_chorus_density(self, title: str, artist: str, duration_s: float) -> Optional[list[float]]:
        """Genius pages mark the chorus with [Chorus] tags — when present we
        approximate where the chorus sits by counting the lines before it."""
        if duration_s <= 0:
            return None
        slug = re.sub(r"[^a-z0-9]+", "-", f"{artist}-{title}".lower()).strip("-")
        html = self._http_get(f"https://genius.com/{slug}-lyrics")
        if not html:
            return None
        # Strip HTML, look for [Chorus]/[Hook]/[Drop] tags and their relative position.
        text = re.sub(r"<[^>]+>", "\n", html)
        lines = [l.strip() for l in text.splitlines() if l.strip()]
        if not lines:
            return None
        positions: list[float] = []
        for idx, line in enumerate(lines):
            if re.match(r"^\[(Chorus|Hook|Drop|Refrain)", line, re.I):
                positions.append(idx / len(lines))
        if not positions:
            return None
        # Convert relative (0-1) positions into seconds.
        seconds = [p * duration_s for p in positions]
        return self._density_curve(seconds, duration_s)

    _TS_RE = re.compile(r"\b(\d{1,2}):(\d{2})\b")

    def _extract_timestamps(self, html: str, duration_s: float) -> list[float]:
        out: list[float] = []
        for m in self._TS_RE.finditer(html):
            mm, ss = int(m.group(1)), int(m.group(2))
            t = mm * 60 + ss
            if 0 < t < duration_s:
                out.append(float(t))
            if len(out) >= 200:
                break
        return out

    def _density_curve(self, timestamps: list[float], duration_s: float) -> list[float]:
        """Gaussian-smoothed density over 1-second buckets, normalised to 0-1."""
        n = max(int(math.ceil(duration_s)), 1)
        buckets = [0.0] * n
        for t in timestamps:
            idx = min(int(t), n - 1)
            buckets[idx] += 1.0
        # Simple 5-tap Gaussian smoothing.
        kernel = [0.06, 0.24, 0.40, 0.24, 0.06]
        smoothed = [0.0] * n
        for i in range(n):
            for k, w in enumerate(kernel):
                j = i + k - 2
                if 0 <= j < n:
                    smoothed[i] += w * buckets[j]
        peak = max(smoothed) or 1.0
        return [v / peak for v in smoothed]

    def _mix_external(
        self,
        hook: ViralHook,
        tiktok: Optional[list[float]],
        youtube: Optional[list[float]],
        lyric: Optional[list[float]],
    ) -> None:
        """Blend external density curves into the hook score (in-place)."""
        spotify_score = hook.signals.get("spotify", 0.0)
        s = float(self.weights["spotify"]) * spotify_score
        weight_used = float(self.weights["spotify"])

        for name, curve in (("tiktok", tiktok), ("youtube", youtube), ("lyric", lyric)):
            if not curve:
                continue
            avg = self._average_in_window(curve, hook.start_ms, hook.end_ms)
            hook.signals[name] = avg
            w = float(self.weights[name])
            s += w * avg
            weight_used += w
            if avg > 0.5:
                hook.reasons.append(f"high {name} timestamp density")

        if weight_used > 0:
            hook.score = (s / weight_used) * 100.0

    def _average_in_window(self, curve: list[float], start_ms: int, end_ms: int) -> float:
        s = max(0, start_ms // 1000)
        e = min(len(curve), max(s + 1, end_ms // 1000))
        if e <= s:
            return 0.0
        return sum(curve[s:e]) / (e - s)

    # ── Fallback when Spotify analysis is unavailable ────────────────────────

    def _fallback(self, analysis: dict) -> list[ViralHook]:
        duration_ms = int(float(analysis.get("track", {}).get("duration", 180.0)) * 1000)
        # Pick three guesses: 25-50s, mid-song, last third.
        guesses = [
            (int(duration_ms * 0.10), int(duration_ms * 0.10) + 24000, "Intro Hook"),
            (int(duration_ms * 0.45), int(duration_ms * 0.45) + 24000, "Mid Hook"),
            (int(duration_ms * 0.75), int(duration_ms * 0.75) + 24000, "Final Drop"),
        ]
        return [
            ViralHook(
                start_ms=s,
                end_ms=min(e, duration_ms),
                score=50.0,
                confidence=0.2,
                label=label,
                reasons=["Spotify analysis unavailable — generic guess"],
                signals={},
            )
            for s, e, label in guesses
        ]
