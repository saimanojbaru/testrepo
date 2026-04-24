"""
MashupPipeline: Orchestrates the full end-to-end mashup generation workflow.

Sequence:
  SpotifyFetcher → AudioDownloader → StemSeparator → AudioManipulator → AudioMixer
"""

import logging
import tempfile
from dataclasses import dataclass
from pathlib import Path
from typing import Optional

from .spotify_fetcher import SpotifyFetcher, TrackMetadata
from .audio_downloader import AudioDownloader
from .stem_separator import StemSeparator, Backend
from .audio_manipulator import AudioManipulator
from .audio_mixer import AudioMixer

logger = logging.getLogger(__name__)


@dataclass
class PipelineConfig:
    output_dir: Path = Path("./mashup_output")
    work_dir: Optional[Path] = None          # temp dir if None
    stem_backend: Backend = Backend.DEMUCS
    apply_pitch_shift: bool = True
    vocal_gain_db: float = 2.0
    # Override Spotify BPM values if you know the correct values already
    override_bpm_a: Optional[float] = None
    override_bpm_b: Optional[float] = None


class MashupPipeline:
    """
    Full mashup pipeline.

    Example
    -------
    >>> pipeline = MashupPipeline(config)
    >>> mp3_path = pipeline.run(
    ...     spotify_url_a="https://open.spotify.com/track/...",
    ...     spotify_url_b="https://open.spotify.com/track/...",
    ... )
    """

    def __init__(self, config: PipelineConfig) -> None:
        self.config = config
        self.config.output_dir.mkdir(parents=True, exist_ok=True)

        # Use a persistent work directory or create a temporary one
        if config.work_dir:
            self._work_dir = config.work_dir
            self._work_dir.mkdir(parents=True, exist_ok=True)
            self._temp_dir = None
        else:
            self._temp_dir = tempfile.mkdtemp(prefix="mashup_work_")
            self._work_dir = Path(self._temp_dir)

        self._fetcher = SpotifyFetcher()
        self._downloader = AudioDownloader(self._work_dir / "downloads")
        self._separator = StemSeparator(
            self._work_dir / "stems",
            backend=config.stem_backend,
        )
        self._manipulator = AudioManipulator(self._work_dir / "processed")
        self._mixer = AudioMixer(
            config.output_dir,
            vocal_gain_db=config.vocal_gain_db,
        )

    # ── public ────────────────────────────────────────────────────────────────

    def run(self, spotify_url_a: str, spotify_url_b: str) -> Path:
        """
        Run the full pipeline.

        Args:
            spotify_url_a: Spotify URL for the vocals track (Track A).
            spotify_url_b: Spotify URL for the instrumental track (Track B).

        Returns:
            Path to the final mixed MP3.
        """
        logger.info("=" * 60)
        logger.info("STEP 1 / 5  —  Fetching Spotify metadata")
        logger.info("=" * 60)
        meta_a = self._fetcher.fetch(spotify_url_a)
        meta_b = self._fetcher.fetch(spotify_url_b)
        self._log_metadata(meta_a, "Track A (vocals)")
        self._log_metadata(meta_b, "Track B (instrumental)")

        logger.info("=" * 60)
        logger.info("STEP 2 / 5  —  Downloading audio from YouTube")
        logger.info("=" * 60)
        wav_a = self._downloader.download(meta_a.track_name, meta_a.artist_name, "track_a")
        wav_b = self._downloader.download(meta_b.track_name, meta_b.artist_name, "track_b")

        logger.info("=" * 60)
        logger.info("STEP 3 / 5  —  Separating stems")
        logger.info("=" * 60)
        vocals_stem = self._separator.extract_vocals(wav_a)
        instrumental_stem = self._separator.extract_instrumental(wav_b)

        logger.info("=" * 60)
        logger.info("STEP 4 / 5  —  Beat-matching & pitch-shifting")
        logger.info("=" * 60)
        bpm_a = self.config.override_bpm_a or meta_a.bpm
        bpm_b = self.config.override_bpm_b or meta_b.bpm

        if bpm_b is None:
            raise ValueError(
                f"BPM for Track B ('{meta_b.track_name}') is unknown. "
                "Pass --bpm-b on the CLI to set it manually."
            )

        beat_matched_vocals = self._manipulator.beat_match(
            vocals_path=vocals_stem,
            target_bpm=bpm_b,
            source_bpm=bpm_a,
            vocals_key=meta_a.key,
            target_key=meta_b.key,
            apply_pitch_shift=self.config.apply_pitch_shift,
        )

        logger.info("=" * 60)
        logger.info("STEP 5 / 5  —  Mixing & exporting")
        logger.info("=" * 60)
        out_path = self._mixer.mix(
            vocals_path=beat_matched_vocals,
            instrumental_path=instrumental_stem,
            track_a_name=meta_a.track_name,
            track_b_name=meta_b.track_name,
        )

        logger.info("=" * 60)
        logger.info("DONE  —  Mashup saved to: %s", out_path)
        logger.info("=" * 60)
        return out_path

    # ── helpers ───────────────────────────────────────────────────────────────

    @staticmethod
    def _log_metadata(meta: TrackMetadata, label: str) -> None:
        logger.info(
            "%s: '%s' by %s | BPM: %s | Key: %s",
            label,
            meta.track_name,
            meta.artist_name,
            f"{meta.bpm:.1f}" if meta.bpm else "N/A",
            meta.key_label,
        )
