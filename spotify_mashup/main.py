"""
CLI entry point for the Spotify Mashup Generator.

Usage examples
--------------
# Minimal (Spotify mode):
  python -m spotify_mashup \
    "https://open.spotify.com/track/AAA" \
    "https://open.spotify.com/track/BBB"

# YouTube-only mode (no Spotify credentials needed):
  python -m spotify_mashup \
    --youtube-only \
    --search-a "Billie Jean Michael Jackson" \
    --search-b "Blinding Lights The Weeknd" \
    --bpm-a 118 --bpm-b 171

# Skip pitch-shift, use spleeter, custom output dir:
  python -m spotify_mashup \
    "https://open.spotify.com/track/AAA" \
    "https://open.spotify.com/track/BBB" \
    --no-pitch-shift \
    --stem-backend spleeter \
    --output-dir ./my_mashups
"""

import logging
import sys
from pathlib import Path

import click

from .pipeline import MashupPipeline, PipelineConfig
from .stem_separator import Backend


# ── logging setup ─────────────────────────────────────────────────────────────

def _setup_logging(verbose: bool) -> None:
    level = logging.DEBUG if verbose else logging.INFO
    logging.basicConfig(
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        datefmt="%H:%M:%S",
        level=level,
        stream=sys.stderr,
    )
    # Suppress overly chatty third-party loggers unless verbose
    if not verbose:
        for noisy in ("numba", "librosa", "audioread", "urllib3", "spotipy"):
            logging.getLogger(noisy).setLevel(logging.WARNING)


# ── CLI ───────────────────────────────────────────────────────────────────────

@click.command(context_settings={"help_option_names": ["-h", "--help"]})
@click.argument("track_a_url", required=False, metavar="SPOTIFY_URL_A")
@click.argument("track_b_url", required=False, metavar="SPOTIFY_URL_B")
# YouTube-only mode ──────────────────────────────────────────────────────────
@click.option(
    "--youtube-only", is_flag=True, default=False,
    help="Skip Spotify lookup. Provide --search-a / --search-b instead of URLs.",
)
@click.option("--search-a", default=None, metavar="QUERY",
              help="YouTube search query for Track A (vocals).")
@click.option("--search-b", default=None, metavar="QUERY",
              help="YouTube search query for Track B (instrumental).")
@click.option("--bpm-a", type=float, default=None,
              help="BPM of Track A (required in --youtube-only mode).")
@click.option("--bpm-b", type=float, default=None,
              help="BPM of Track B (required in --youtube-only mode).")
@click.option("--key-a", type=int, default=None,
              help="Pitch class of Track A, 0=C … 11=B (optional).")
@click.option("--key-b", type=int, default=None,
              help="Pitch class of Track B, 0=C … 11=B (optional).")
# Stem separation ─────────────────────────────────────────────────────────────
@click.option(
    "--stem-backend",
    type=click.Choice(["demucs", "spleeter"], case_sensitive=False),
    default="demucs",
    show_default=True,
    help="Stem-separation backend.",
)
# Mixing ──────────────────────────────────────────────────────────────────────
@click.option(
    "--no-pitch-shift", is_flag=True, default=False,
    help="Disable automatic key-compatibility pitch shifting.",
)
@click.option(
    "--vocal-gain", type=float, default=2.0, show_default=True,
    help="Vocal level relative to instrumental (dB).",
)
# Output & paths ──────────────────────────────────────────────────────────────
@click.option(
    "--output-dir", type=click.Path(), default="./mashup_output", show_default=True,
    help="Directory for the final MP3.",
)
@click.option(
    "--work-dir", type=click.Path(), default=None,
    help="Persistent working directory for downloads and stems (temp dir if omitted).",
)
@click.option("-v", "--verbose", is_flag=True, default=False, help="Verbose logging.")
def cli(
    track_a_url, track_b_url,
    youtube_only, search_a, search_b,
    bpm_a, bpm_b, key_a, key_b,
    stem_backend, no_pitch_shift, vocal_gain,
    output_dir, work_dir, verbose,
):
    """
    Generate a mashup by placing the vocals from SPOTIFY_URL_A over the
    instrumental of SPOTIFY_URL_B.

    Both arguments are Spotify track URLs of the form:
      https://open.spotify.com/track/<id>

    Use --youtube-only to bypass the Spotify API entirely.
    """
    _setup_logging(verbose)
    log = logging.getLogger(__name__)

    # ── validate inputs ───────────────────────────────────────────────────────
    if youtube_only:
        if not search_a or not search_b:
            raise click.UsageError(
                "--youtube-only requires both --search-a and --search-b."
            )
        if bpm_b is None:
            raise click.UsageError(
                "--youtube-only requires --bpm-b to be set "
                "(BPM of the instrumental track)."
            )
        return _run_youtube_only(
            search_a=search_a,
            search_b=search_b,
            bpm_a=bpm_a,
            bpm_b=bpm_b,
            key_a=key_a,
            key_b=key_b,
            stem_backend=Backend(stem_backend),
            apply_pitch_shift=not no_pitch_shift,
            vocal_gain_db=vocal_gain,
            output_dir=Path(output_dir),
            work_dir=Path(work_dir) if work_dir else None,
            log=log,
        )

    # ── Spotify mode ──────────────────────────────────────────────────────────
    if not track_a_url or not track_b_url:
        raise click.UsageError(
            "Provide two Spotify track URLs, or use --youtube-only mode.\n"
            "Run with --help for usage."
        )

    config = PipelineConfig(
        output_dir=Path(output_dir),
        work_dir=Path(work_dir) if work_dir else None,
        stem_backend=Backend(stem_backend),
        apply_pitch_shift=not no_pitch_shift,
        vocal_gain_db=vocal_gain,
        override_bpm_a=bpm_a,
        override_bpm_b=bpm_b,
    )

    try:
        pipeline = MashupPipeline(config)
        mp3 = pipeline.run(track_a_url, track_b_url)
        click.echo(f"\n✓ Mashup created: {mp3}")
    except EnvironmentError as exc:
        click.echo(f"\n[ERROR] Credentials problem: {exc}", err=True)
        sys.exit(1)
    except ValueError as exc:
        click.echo(f"\n[ERROR] {exc}", err=True)
        sys.exit(1)
    except RuntimeError as exc:
        click.echo(f"\n[ERROR] {exc}", err=True)
        sys.exit(1)


# ── YouTube-only path ─────────────────────────────────────────────────────────

def _run_youtube_only(
    search_a, search_b, bpm_a, bpm_b, key_a, key_b,
    stem_backend, apply_pitch_shift, vocal_gain_db,
    output_dir, work_dir, log,
):
    """Pipeline variant that skips Spotify and uses manual search + BPM values."""
    import tempfile
    from .audio_downloader import AudioDownloader
    from .stem_separator import StemSeparator
    from .audio_manipulator import AudioManipulator
    from .audio_mixer import AudioMixer

    work = work_dir or Path(tempfile.mkdtemp(prefix="mashup_yt_"))
    output_dir.mkdir(parents=True, exist_ok=True)

    # Parse "Artist - Title" style queries into (name, artist) for the downloader
    def _split_query(q: str):
        parts = q.split("-", 1)
        if len(parts) == 2:
            return parts[1].strip(), parts[0].strip()
        return q, ""

    name_a, artist_a = _split_query(search_a)
    name_b, artist_b = _split_query(search_b)

    log.info("YouTube-only mode: downloading audio …")
    downloader = AudioDownloader(work / "downloads")
    wav_a = downloader.download(name_a, artist_a, "track_a")
    wav_b = downloader.download(name_b, artist_b, "track_b")

    log.info("Separating stems …")
    separator = StemSeparator(work / "stems", backend=stem_backend)
    vocals = separator.extract_vocals(wav_a)
    instrumental = separator.extract_instrumental(wav_b)

    log.info("Beat-matching …")
    manipulator = AudioManipulator(work / "processed")
    beat_matched = manipulator.beat_match(
        vocals_path=vocals,
        target_bpm=bpm_b,
        source_bpm=bpm_a,
        vocals_key=key_a,
        target_key=key_b,
        apply_pitch_shift=apply_pitch_shift,
    )

    log.info("Mixing …")
    mixer = AudioMixer(output_dir, vocal_gain_db=vocal_gain_db)
    mp3 = mixer.mix(
        vocals_path=beat_matched,
        instrumental_path=instrumental,
        track_a_name=name_a,
        track_b_name=name_b,
    )

    click.echo(f"\n✓ Mashup created: {mp3}")


# ── entry point ───────────────────────────────────────────────────────────────

if __name__ == "__main__":
    cli()
