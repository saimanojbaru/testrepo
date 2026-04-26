"""
AudioDownloader: Searches YouTube and downloads audio as a high-quality WAV file.

Uses yt-dlp under the hood. Falls back gracefully if the first search result
fails to download, trying up to MAX_SEARCH_RESULTS candidates.
"""

import logging
import re
import subprocess
import sys
from pathlib import Path
from typing import Optional

logger = logging.getLogger(__name__)

MAX_SEARCH_RESULTS = 2   # how many YouTube candidates to try before giving up


class AudioDownloader:
    """Downloads audio from YouTube using yt-dlp and converts to WAV."""

    def __init__(self, output_dir: Path) -> None:
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self._check_ytdlp()

    # ── public ────────────────────────────────────────────────────────────────

    def download_url(
        self,
        url: str,
        label: str,
        *,
        clip_seconds: Optional[int] = None,
    ) -> Path:
        """Download audio from a direct YouTube URL (no search needed)."""
        safe_label = _sanitise_filename(label)
        suffix = f"_{clip_seconds}s" if clip_seconds else ""
        out_path = self.output_dir / f"{safe_label}{suffix}.wav"
        if out_path.exists():
            return out_path
        logger.info("Direct URL download: %s (clip=%ss)", url, clip_seconds or "full")
        if self._run_ytdlp(url, out_path, pick_index=None, clip_seconds=clip_seconds) and out_path.exists():
            return out_path
        raise RuntimeError(f"Download failed for URL: {url}")

    def download(
        self,
        track_name: str,
        artist_name: str,
        label: str,
        *,
        clip_seconds: Optional[int] = None,
    ) -> Path:
        """
        Search YouTube for '<track_name> <artist_name> audio' and download
        the best match as a WAV file.

        Args:
            track_name:   Song title.
            artist_name:  Primary artist.
            label:        Short label used in the output filename (e.g. 'track_a').
            clip_seconds: If set, only download the first N seconds.  Cuts
                          download + processing time roughly proportionally.

        Returns:
            Path to the downloaded WAV file.
        """
        query = f"{track_name} {artist_name} audio"
        safe_label = _sanitise_filename(label)
        # Cache key includes clip duration so 45s and full versions don't collide
        suffix = f"_{clip_seconds}s" if clip_seconds else ""
        out_path = self.output_dir / f"{safe_label}{suffix}.wav"

        if out_path.exists():
            logger.info("Cached WAV found, skipping download: %s", out_path)
            return out_path

        logger.info("Searching YouTube for: %s (clip=%ss)", query, clip_seconds or "full")

        for attempt in range(1, MAX_SEARCH_RESULTS + 1):
            search_url = f"ytsearch{attempt}:{query}"
            logger.debug("yt-dlp attempt %d / %d …", attempt, MAX_SEARCH_RESULTS)
            success = self._run_ytdlp(search_url, out_path, pick_index=attempt - 1, clip_seconds=clip_seconds)
            if success and out_path.exists():
                logger.info("Downloaded: %s", out_path)
                return out_path

        raise RuntimeError(
            f"All {MAX_SEARCH_RESULTS} YouTube download attempts failed for "
            f"'{track_name}' by {artist_name}. "
            "Check your internet connection or try a different search term."
        )

    # ── private ───────────────────────────────────────────────────────────────

    def _run_ytdlp(
        self,
        search_url: str,
        out_path: Path,
        pick_index: Optional[int],   # None = direct URL (no playlist-items flag)
        clip_seconds: Optional[int] = None,
    ) -> bool:
        """Run yt-dlp and return True if the output file was created."""
        cmd = [
            sys.executable, "-m", "yt_dlp",
            "--no-playlist",
            "--format", "bestaudio/best",
            "--audio-quality", "0",
            "--extract-audio",
            "--audio-format", "wav",
            "--output", str(out_path.with_suffix("")),
            "--no-warnings",
            "--quiet",
            "--socket-timeout", "20",   # bail out fast if YouTube stalls
            "--retries", "2",
        ]
        if pick_index is not None:
            cmd += ["--playlist-items", str(pick_index + 1)]
        if clip_seconds and clip_seconds > 0:
            cmd += ["--download-sections", f"*0-{clip_seconds}", "--force-keyframes-at-cuts"]
        cmd.append(search_url)

        try:
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=90,    # 90-second hard cap per attempt
            )
            if result.returncode != 0:
                logger.debug("yt-dlp stderr: %s", result.stderr.strip())
                return False
            return True
        except subprocess.TimeoutExpired:
            logger.warning("yt-dlp timed out for: %s", search_url)
            return False
        except FileNotFoundError:
            raise RuntimeError(
                "yt-dlp is not installed or not in PATH. "
                "Install it with: pip install yt-dlp"
            )

    @staticmethod
    def _check_ytdlp() -> None:
        """Fail fast if yt-dlp is unavailable."""
        try:
            subprocess.run(
                [sys.executable, "-m", "yt_dlp", "--version"],
                capture_output=True,
                check=True,
            )
        except (subprocess.CalledProcessError, FileNotFoundError):
            raise RuntimeError(
                "yt-dlp module not found. Install with: pip install yt-dlp"
            )


def _sanitise_filename(name: str) -> str:
    """Strip characters that are unsafe in filenames."""
    return re.sub(r'[<>:"/\\|?*\s]+', "_", name).strip("_")
