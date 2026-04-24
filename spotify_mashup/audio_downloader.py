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

MAX_SEARCH_RESULTS = 5   # how many YouTube candidates to try before giving up


class AudioDownloader:
    """Downloads audio from YouTube using yt-dlp and converts to WAV."""

    def __init__(self, output_dir: Path) -> None:
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self._check_ytdlp()

    # ── public ────────────────────────────────────────────────────────────────

    def download(self, track_name: str, artist_name: str, label: str) -> Path:
        """
        Search YouTube for '<track_name> <artist_name> audio' and download
        the best match as a WAV file.

        Args:
            track_name:  Song title.
            artist_name: Primary artist.
            label:       Short label used in the output filename (e.g. 'track_a').

        Returns:
            Path to the downloaded WAV file.
        """
        query = f"{track_name} {artist_name} audio"
        safe_label = _sanitise_filename(label)
        out_path = self.output_dir / f"{safe_label}.wav"

        if out_path.exists():
            logger.info("Cached WAV found, skipping download: %s", out_path)
            return out_path

        logger.info("Searching YouTube for: %s", query)

        for attempt in range(1, MAX_SEARCH_RESULTS + 1):
            search_url = f"ytsearch{attempt}:{query}"
            logger.debug("yt-dlp attempt %d / %d …", attempt, MAX_SEARCH_RESULTS)
            success = self._run_ytdlp(search_url, out_path, pick_index=attempt - 1)
            if success and out_path.exists():
                logger.info("Downloaded: %s", out_path)
                return out_path

        raise RuntimeError(
            f"All {MAX_SEARCH_RESULTS} YouTube download attempts failed for "
            f"'{track_name}' by {artist_name}. "
            "Check your internet connection or try a different search term."
        )

    # ── private ───────────────────────────────────────────────────────────────

    def _run_ytdlp(self, search_url: str, out_path: Path, pick_index: int) -> bool:
        """Run yt-dlp and return True if the output file was created."""
        # Playlist index 1-based; we pick the Nth result on successive retries.
        playlist_items = str(pick_index + 1)

        cmd = [
            sys.executable, "-m", "yt_dlp",
            "--no-playlist",
            "--playlist-items", playlist_items,
            "--format", "bestaudio/best",
            "--audio-quality", "0",         # highest quality
            "--extract-audio",
            "--audio-format", "wav",
            "--output", str(out_path.with_suffix("")),   # yt-dlp appends extension
            "--no-warnings",
            "--quiet",
            search_url,
        ]

        try:
            result = subprocess.run(
                cmd,
                capture_output=True,
                text=True,
                timeout=300,   # 5-minute cap per download
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
