"""
StemSeparator: Isolates vocal and instrumental stems from full audio files.

Supports two backends:
  - 'demucs'  (default, higher quality, Facebook Research)
  - 'spleeter' (faster, Deezer)

Demucs is preferred because it handles a wider range of music styles and
produces cleaner stem isolation. Spleeter is available as a lighter fallback.
"""

import logging
import shutil
import subprocess
import sys
from enum import Enum
from pathlib import Path

logger = logging.getLogger(__name__)


class Backend(str, Enum):
    DEMUCS = "demucs"
    SPLEETER = "spleeter"


class StemSeparator:
    """
    Runs a stem-separation model and returns paths to the vocals and
    instrumental (accompaniment) stems.
    """

    def __init__(
        self,
        output_dir: Path,
        backend: Backend = Backend.DEMUCS,
        demucs_model: str = "htdemucs",   # or 'htdemucs_ft' for fine-tuned
    ) -> None:
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.backend = backend
        self.demucs_model = demucs_model

    # ── public ────────────────────────────────────────────────────────────────

    def extract_vocals(self, audio_path: Path) -> Path:
        """Return path to the vocals-only stem for the given audio file."""
        return self._separate(audio_path, want="vocals")

    def extract_instrumental(self, audio_path: Path) -> Path:
        """Return path to the instrumental (no-vocals) stem."""
        return self._separate(audio_path, want="instrumental")

    # ── routing ───────────────────────────────────────────────────────────────

    def _separate(self, audio_path: Path, want: str) -> Path:
        audio_path = Path(audio_path)
        if not audio_path.exists():
            raise FileNotFoundError(f"Audio file not found: {audio_path}")

        if self.backend == Backend.DEMUCS:
            return self._demucs(audio_path, want)
        elif self.backend == Backend.SPLEETER:
            return self._spleeter(audio_path, want)
        else:
            raise ValueError(f"Unknown backend: {self.backend!r}")

    # ── demucs backend ────────────────────────────────────────────────────────

    def _demucs(self, audio_path: Path, want: str) -> Path:
        """
        Run demucs and return the requested stem.

        Demucs outputs four stems: bass, drums, other, vocals.
        For 'instrumental' we return the 'no_vocals' mix that demucs can
        produce with --two-stems vocals (outputs vocals + no_vocals).
        """
        stem_name = "vocals" if want == "vocals" else "no_vocals"
        separated_dir = self.output_dir / "demucs" / self.demucs_model / audio_path.stem

        cached = separated_dir / f"{stem_name}.wav"
        if cached.exists():
            logger.info("Using cached demucs stem: %s", cached)
            return cached

        logger.info(
            "Running demucs (%s) on %s — this may take a few minutes …",
            self.demucs_model,
            audio_path.name,
        )

        cmd = [
            sys.executable, "-m", "demucs",
            "--two-stems", "vocals",        # splits into vocals + no_vocals only
            "--model", self.demucs_model,
            "--out", str(self.output_dir / "demucs"),
            str(audio_path),
        ]

        self._run(cmd, label="demucs")

        if not cached.exists():
            raise RuntimeError(
                f"Demucs finished but expected stem not found: {cached}\n"
                "Check the demucs output directory for details."
            )

        logger.info("Stem extracted: %s", cached)
        return cached

    # ── spleeter backend ──────────────────────────────────────────────────────

    def _spleeter(self, audio_path: Path, want: str) -> Path:
        """
        Run spleeter:separate (2stems) and return the requested stem.

        Spleeter names the outputs 'vocals.wav' and 'accompaniment.wav'.
        """
        stem_name = "vocals" if want == "vocals" else "accompaniment"
        separated_dir = self.output_dir / "spleeter" / audio_path.stem

        cached = separated_dir / f"{stem_name}.wav"
        if cached.exists():
            logger.info("Using cached spleeter stem: %s", cached)
            return cached

        logger.info(
            "Running spleeter on %s — this may take a few minutes …",
            audio_path.name,
        )

        cmd = [
            sys.executable, "-m", "spleeter", "separate",
            "-p", "spleeter:2stems",
            "-o", str(self.output_dir / "spleeter"),
            str(audio_path),
        ]

        self._run(cmd, label="spleeter")

        if not cached.exists():
            raise RuntimeError(
                f"Spleeter finished but expected stem not found: {cached}"
            )

        logger.info("Stem extracted: %s", cached)
        return cached

    # ── helpers ───────────────────────────────────────────────────────────────

    @staticmethod
    def _run(cmd: list, label: str) -> None:
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
        if result.returncode != 0:
            raise RuntimeError(
                f"{label} failed (exit {result.returncode}):\n"
                f"{result.stderr.strip() or result.stdout.strip()}"
            )
