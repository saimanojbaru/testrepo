"""
AudioManipulator: BPM-matching and optional pitch-shifting of audio stems.

Pipeline:
  1. Detect actual BPM of the vocals stem (in case it differs from Spotify metadata).
  2. Time-stretch the vocals to match the instrumental's BPM using pyrubberband
     (high-quality phase-vocoder, preserves pitch).
  3. Optionally pitch-shift the stretched vocals so they are harmonically
     compatible with the key of the instrumental.

Key compatibility logic (bonus):
  Both Spotify keys are mapped to a chromatic circle. The semitone shift
  that minimises the interval (i.e. never more than 6 semitones up or down)
  is applied. Shifts > ±3 semitones are capped at ±3 to avoid unnatural results.
"""

import logging
from pathlib import Path
from typing import Optional

import librosa
import numpy as np
import pyrubberband as pyrb
import soundfile as sf

logger = logging.getLogger(__name__)

# Maximum allowed pitch shift (semitones). Change to taste.
MAX_PITCH_SHIFT_SEMITONES = 3


class AudioManipulator:
    """Time-stretches and (optionally) pitch-shifts audio stems."""

    def __init__(self, output_dir: Path) -> None:
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)

    # ── public ────────────────────────────────────────────────────────────────

    def beat_match(
        self,
        vocals_path: Path,
        target_bpm: float,
        source_bpm: Optional[float] = None,
        vocals_key: Optional[int] = None,
        target_key: Optional[int] = None,
        apply_pitch_shift: bool = True,
    ) -> Path:
        """
        Time-stretch the vocals so they run at target_bpm, then optionally
        pitch-shift them toward target_key.

        Args:
            vocals_path:       Path to the vocals WAV stem.
            target_bpm:        BPM of the instrumental track.
            source_bpm:        BPM of the vocals track (auto-detected if None).
            vocals_key:        Spotify pitch class of the vocals track (0-11).
            target_key:        Spotify pitch class of the instrumental track.
            apply_pitch_shift: Whether to attempt key-compatibility shifting.

        Returns:
            Path to the processed WAV file.
        """
        vocals_path = Path(vocals_path)
        out_path = self.output_dir / f"{vocals_path.stem}_beatmatched.wav"

        if out_path.exists():
            logger.info("Cached beat-matched file found: %s", out_path)
            return out_path

        logger.info("Loading vocals: %s", vocals_path)
        y, sr = librosa.load(str(vocals_path), sr=None, mono=False)

        # ── 1. Detect or use provided BPM ─────────────────────────────────
        actual_bpm = self._detect_bpm(y, sr, source_bpm)
        logger.info(
            "Vocals BPM: %.2f → target BPM: %.2f", actual_bpm, target_bpm
        )

        # ── 2. Time-stretch ───────────────────────────────────────────────
        stretch_ratio = target_bpm / actual_bpm
        if abs(stretch_ratio - 1.0) < 0.005:
            logger.info("BPMs nearly identical; skipping time-stretch.")
            y_stretched = y
        else:
            logger.info(
                "Time-stretching by ratio %.4f (pyrubberband) …", stretch_ratio
            )
            y_stretched = self._time_stretch(y, sr, stretch_ratio)

        # ── 3. Pitch-shift (optional) ─────────────────────────────────────
        y_final = y_stretched
        if apply_pitch_shift and vocals_key is not None and target_key is not None:
            semitones = self._calc_semitone_shift(vocals_key, target_key)
            if semitones != 0:
                logger.info("Pitch-shifting by %+d semitones …", semitones)
                y_final = self._pitch_shift(y_stretched, sr, semitones)
            else:
                logger.info("Keys match; no pitch shift needed.")

        # ── 4. Write output ───────────────────────────────────────────────
        self._write(y_final, sr, out_path)
        logger.info("Beat-matched vocals saved: %s", out_path)
        return out_path

    # ── internals ─────────────────────────────────────────────────────────────

    @staticmethod
    def _detect_bpm(y: np.ndarray, sr: int, hint: Optional[float]) -> float:
        """Use librosa beat tracking; fall back to hint if tracking fails."""
        if hint is not None:
            logger.debug("Using Spotify BPM hint: %.2f", hint)
            return float(hint)

        logger.debug("Auto-detecting BPM with librosa …")
        # librosa.beat.beat_track expects mono
        mono = librosa.to_mono(y) if y.ndim > 1 else y
        tempo, _ = librosa.beat.beat_track(y=mono, sr=sr)
        detected = float(np.atleast_1d(tempo)[0])
        if detected < 40 or detected > 300:
            logger.warning(
                "Detected BPM %.1f looks unreliable; clamping to 120.", detected
            )
            detected = 120.0
        logger.info("Auto-detected BPM: %.2f", detected)
        return detected

    @staticmethod
    def _time_stretch(y: np.ndarray, sr: int, ratio: float) -> np.ndarray:
        """
        Time-stretch y by ratio using pyrubberband.
        Handles both mono (1-D) and stereo (2-D, shape [channels, samples]).
        """
        if y.ndim == 1:
            return pyrb.time_stretch(y, sr, ratio)

        # pyrubberband expects (samples, channels)
        y_t = y.T
        stretched_t = pyrb.time_stretch(y_t, sr, ratio)
        return stretched_t.T

    @staticmethod
    def _pitch_shift(y: np.ndarray, sr: int, semitones: int) -> np.ndarray:
        """Pitch-shift without time change using pyrubberband."""
        if y.ndim == 1:
            return pyrb.pitch_shift(y, sr, semitones)

        y_t = y.T
        shifted_t = pyrb.pitch_shift(y_t, sr, semitones)
        return shifted_t.T

    @staticmethod
    def _calc_semitone_shift(from_key: int, to_key: int) -> int:
        """
        Calculate the shortest chromatic path from from_key to to_key,
        clamped to ±MAX_PITCH_SHIFT_SEMITONES.
        """
        diff = (to_key - from_key) % 12
        if diff > 6:
            diff -= 12   # prefer going down rather than a large upward shift
        clamped = max(-MAX_PITCH_SHIFT_SEMITONES, min(MAX_PITCH_SHIFT_SEMITONES, diff))
        if clamped != diff:
            logger.warning(
                "Pitch shift clamped from %+d to %+d semitones to avoid "
                "unnatural artefacts.",
                diff,
                clamped,
            )
        return clamped

    @staticmethod
    def _write(y: np.ndarray, sr: int, path: Path) -> None:
        """Write a numpy audio array to a WAV file via soundfile."""
        if y.ndim == 2:
            # soundfile expects (samples, channels)
            data = y.T
        else:
            data = y
        sf.write(str(path), data, sr, subtype="PCM_16")
