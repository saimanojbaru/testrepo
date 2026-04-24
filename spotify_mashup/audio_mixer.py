"""
AudioMixer: Overlays beat-matched vocals onto an instrumental and exports MP3.

Signal chain:
  1. Load the instrumental WAV.
  2. Apply a gentle mid-frequency dip (around 1–4 kHz) to make room for vocals.
     Implemented as a cascade of pydub high-pass and low-pass shelves that
     approximate a broad mid-cut without requiring scipy filters.
  3. Load the (already beat-matched) vocals WAV.
  4. Optionally trim/pad both tracks to the same length.
  5. Overlay with a configurable vocal gain offset.
  6. Export as 320 kbps MP3.
"""

import logging
import re
from pathlib import Path

from pydub import AudioSegment
from pydub.effects import normalize

logger = logging.getLogger(__name__)

# How much (in dB) to boost or cut the vocals relative to the instrumental.
DEFAULT_VOCAL_GAIN_DB = 2.0

# How many milliseconds of fade in/out to apply at the start/end of the mix.
FADE_MS = 2000


class AudioMixer:
    """Mixes beat-matched vocals onto an instrumental and exports the result."""

    def __init__(
        self,
        output_dir: Path,
        vocal_gain_db: float = DEFAULT_VOCAL_GAIN_DB,
    ) -> None:
        self.output_dir = Path(output_dir)
        self.output_dir.mkdir(parents=True, exist_ok=True)
        self.vocal_gain_db = vocal_gain_db

    # ── public ────────────────────────────────────────────────────────────────

    def mix(
        self,
        vocals_path: Path,
        instrumental_path: Path,
        track_a_name: str,
        track_b_name: str,
    ) -> Path:
        """
        Mix vocals over the instrumental and export as 320 kbps MP3.

        Args:
            vocals_path:         Beat-matched vocals WAV (from AudioManipulator).
            instrumental_path:   Instrumental stem WAV (from StemSeparator).
            track_a_name:        Track A title (used in output filename).
            track_b_name:        Track B title (used in output filename).

        Returns:
            Path to the exported MP3 file.
        """
        out_name = _make_output_name(track_a_name, track_b_name)
        out_path = self.output_dir / out_name

        logger.info("Loading instrumental: %s", instrumental_path)
        instrumental = AudioSegment.from_wav(str(instrumental_path))

        logger.info("Loading vocals: %s", vocals_path)
        vocals = AudioSegment.from_wav(str(vocals_path))

        # ── 1. Normalise both tracks ───────────────────────────────────────
        instrumental = normalize(instrumental)
        vocals = normalize(vocals)

        # ── 2. Gentle mid-cut on instrumental to make room for vocals ──────
        instrumental = self._apply_mid_cut(instrumental)

        # ── 3. Align lengths (pad shorter one with silence) ────────────────
        instrumental, vocals = self._align_lengths(instrumental, vocals)

        # ── 4. Apply vocal gain offset ─────────────────────────────────────
        vocals = vocals + self.vocal_gain_db

        # ── 5. Overlay ─────────────────────────────────────────────────────
        logger.info("Overlaying vocals onto instrumental …")
        mixed = instrumental.overlay(vocals)

        # ── 6. Fade in / out & normalise final mix ─────────────────────────
        mixed = mixed.fade_in(FADE_MS).fade_out(FADE_MS)
        mixed = normalize(mixed)

        # ── 7. Export ──────────────────────────────────────────────────────
        logger.info("Exporting MP3: %s", out_path)
        mixed.export(
            str(out_path),
            format="mp3",
            bitrate="320k",
            tags={
                "title": f"Mashup: {track_a_name} vs {track_b_name}",
                "artist": "Spotify Mashup Generator",
                "comment": "Generated automatically",
            },
        )
        logger.info("Export complete: %s", out_path)
        return out_path

    # ── private helpers ───────────────────────────────────────────────────────

    @staticmethod
    def _apply_mid_cut(segment: AudioSegment) -> AudioSegment:
        """
        Approximate a broad mid-frequency cut on the instrumental.

        Pure pydub doesn't expose parametric EQ, but we can simulate a subtle
        mid-cut by blending the original signal with a version that has both
        high and low components accentuated.  The technique:
          - Create a low-shelf boost  (high_pass_filter removes lows → invert)
          - Create a high-shelf boost (low_pass_filter removes highs → invert)
          - Blend lightly with the original (-2 dB on the result) to yield a
            perceptible but not heavy dip in the 800 Hz – 4 kHz range.

        This avoids scipy/numpy and works entirely within pydub.
        """
        try:
            # Components below ~800 Hz and above ~4 kHz at full level
            lows = segment.low_pass_filter(800)
            highs = segment.high_pass_filter(4000)
            mids_only = segment.high_pass_filter(800).low_pass_filter(4000)

            # Reduce the mids by ~2 dB and reconstruct
            mids_cut = mids_only - 2
            processed = lows.overlay(highs).overlay(mids_cut)
            logger.debug("Mid-cut EQ applied to instrumental.")
            return processed
        except Exception as exc:
            # Non-fatal: if the EQ fails, continue with the unprocessed signal.
            logger.warning("Mid-cut EQ skipped (%s); using raw instrumental.", exc)
            return segment

    @staticmethod
    def _align_lengths(
        a: AudioSegment, b: AudioSegment
    ) -> tuple[AudioSegment, AudioSegment]:
        """Pad the shorter segment with silence so both are the same length."""
        diff = len(a) - len(b)
        if diff > 0:
            b = b + AudioSegment.silent(duration=diff, frame_rate=b.frame_rate)
        elif diff < 0:
            a = a + AudioSegment.silent(duration=-diff, frame_rate=a.frame_rate)
        return a, b


def _make_output_name(track_a: str, track_b: str) -> str:
    """Build a safe MP3 filename from the two track names."""

    def _sanitise(s: str) -> str:
        return re.sub(r'[<>:"/\\|?*\s]+', "_", s).strip("_")[:40]

    return f"Mashup_{_sanitise(track_a)}_vs_{_sanitise(track_b)}.mp3"
