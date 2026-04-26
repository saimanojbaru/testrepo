"""
Viral Mashup Bot — automated trending-music mashup pipeline.

Pipeline:
  1. yt-dlp searches YouTube for "trending music", picks top 2 results
  2. Downloads only the first 45 seconds of each (4x faster than full songs)
  3. Sends each clip to your HF Space (AggDynamo/Mashup) for stem separation
  4. librosa analyses BPM and key of both stems
  5. Time-stretches vocals to instrumental BPM
  6. Pitch-shifts vocals to a Camelot-compatible key
  7. Mixes into a 60-second high-quality MP3: Trending_Mashup_v1.mp3

Run:
    pip install -r viral_bot_requirements.txt
    python viral_bot.py
"""

from __future__ import annotations

import io
import logging
import re
import subprocess
import sys
import tempfile
from pathlib import Path

import librosa
import numpy as np
import requests
import soundfile as sf
from pydub import AudioSegment

# ── Config ────────────────────────────────────────────────────────────────────

HF_SPACE_URL = "https://aggdynamo-mashup.hf.space"
TRENDING_QUERY = "trending music"
CLIP_SECONDS = 45                    # only process first 45s — 4x faster
OUTPUT_DURATION_S = 60               # final mashup length
OUTPUT_FILE = "Trending_Mashup_v1.mp3"
SAMPLE_RATE = 44100
WORK_DIR = Path(tempfile.mkdtemp(prefix="viral_mashup_"))

logging.basicConfig(format="%(levelname)s: %(message)s", level=logging.INFO)
log = logging.getLogger(__name__)


# ── 1. Discover trending tracks ──────────────────────────────────────────────

def find_trending_urls(query: str = TRENDING_QUERY, count: int = 2) -> list[str]:
    """Use yt-dlp to search YouTube and return the top N video URLs."""
    log.info("🔍 Searching YouTube for '%s'…", query)
    cmd = [
        sys.executable, "-m", "yt_dlp",
        f"ytsearch{count}:{query}",
        "--get-id",
        "--flat-playlist",
        "--quiet",
    ]
    result = subprocess.run(cmd, capture_output=True, text=True, timeout=60)
    if result.returncode != 0:
        raise RuntimeError(f"yt-dlp search failed: {result.stderr}")
    ids = [line.strip() for line in result.stdout.splitlines() if line.strip()]
    urls = [f"https://www.youtube.com/watch?v={vid}" for vid in ids[:count]]
    log.info("📌 Top %d trending: %s", count, urls)
    return urls


# ── 2. Get separated stems via HF Space ──────────────────────────────────────

def fetch_stem(url: str, stem: str, clip_seconds: int = CLIP_SECONDS) -> Path:
    """POST to HF Space's /api/stem and save the returned WAV locally."""
    log.info("✂️  Requesting %s stem from HF Space (%ds clip)…", stem, clip_seconds)
    resp = requests.post(
        f"{HF_SPACE_URL}/api/stem",
        json={"url": url, "stem": stem, "clip_seconds": clip_seconds},
        timeout=600,  # demucs can take a few min on free tier
    )
    resp.raise_for_status()

    safe = re.sub(r"[^A-Za-z0-9]+", "_", stem)
    out_path = WORK_DIR / f"{safe}_{abs(hash(url)) % 10**6}.wav"
    out_path.write_bytes(resp.content)
    log.info("   → saved to %s (%.1f KB)", out_path.name, out_path.stat().st_size / 1024)
    return out_path


# ── 3. Musical alignment (BPM + key) ─────────────────────────────────────────

KEY_NAMES = ["C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"]


def estimate_bpm(audio_path: Path) -> float:
    y, sr = librosa.load(str(audio_path), sr=SAMPLE_RATE, mono=True)
    tempo, _ = librosa.beat.beat_track(y=y, sr=sr)
    bpm = float(tempo) if np.isscalar(tempo) else float(tempo[0])
    log.info("🥁 %s → %.1f BPM", audio_path.name, bpm)
    return bpm


def estimate_key(audio_path: Path) -> int:
    """Return the dominant key as 0-11 (0=C, 1=C#, …, 11=B)."""
    y, sr = librosa.load(str(audio_path), sr=SAMPLE_RATE, mono=True)
    chroma = librosa.feature.chroma_cqt(y=y, sr=sr)
    key_idx = int(np.argmax(chroma.mean(axis=1)))
    log.info("🎼 %s → key %s", audio_path.name, KEY_NAMES[key_idx])
    return key_idx


def time_stretch(audio_path: Path, source_bpm: float, target_bpm: float) -> Path:
    """Stretch audio to match target BPM (preserves pitch)."""
    if abs(source_bpm - target_bpm) < 1.0:
        return audio_path
    ratio = target_bpm / source_bpm
    log.info("⏱️  Time-stretching %.2fx (%.1f → %.1f BPM)", ratio, source_bpm, target_bpm)
    y, sr = librosa.load(str(audio_path), sr=SAMPLE_RATE, mono=False)
    if y.ndim == 1:
        y_stretched = librosa.effects.time_stretch(y, rate=ratio)
    else:
        y_stretched = np.stack([librosa.effects.time_stretch(c, rate=ratio) for c in y])
    out = WORK_DIR / f"stretched_{audio_path.name}"
    sf.write(str(out), y_stretched.T if y_stretched.ndim > 1 else y_stretched, sr)
    return out


def pitch_shift_to_match(vocals_path: Path, vocals_key: int, instr_key: int) -> Path:
    """
    Shift vocals to a Camelot-compatible key for the instrumental.
    Picks the smallest semitone shift that lands on either the same key,
    a perfect fifth (±7), or a relative minor/major (±3 / ±9).
    """
    if vocals_key == instr_key:
        return vocals_path

    # Try same, ±7 (perfect fifth), ±3 (relative minor/major)
    candidates = [0, 7, -7, -5, 5, 3, -3]
    deltas = [(c, ((vocals_key + c) - instr_key) % 12) for c in candidates]
    # Choose the candidate whose result is closest to instr_key (mod 12)
    best = min(candidates, key=lambda c: min((vocals_key + c - instr_key) % 12,
                                              (instr_key - vocals_key - c) % 12))
    if best == 0:
        return vocals_path

    log.info("🎹 Pitch-shifting vocals %+d semitones for key compatibility", best)
    y, sr = librosa.load(str(vocals_path), sr=SAMPLE_RATE, mono=False)
    if y.ndim == 1:
        y_shifted = librosa.effects.pitch_shift(y, sr=sr, n_steps=best)
    else:
        y_shifted = np.stack([
            librosa.effects.pitch_shift(c, sr=sr, n_steps=best) for c in y
        ])
    out = WORK_DIR / f"pitched_{vocals_path.name}"
    sf.write(str(out), y_shifted.T if y_shifted.ndim > 1 else y_shifted, sr)
    return out


# ── 4. Combine and export ────────────────────────────────────────────────────

def export_mashup(vocals: Path, instrumental: Path, out_file: str = OUTPUT_FILE) -> Path:
    log.info("🎚️  Mixing vocals over instrumental…")
    vox = AudioSegment.from_file(str(vocals))
    inst = AudioSegment.from_file(str(instrumental))

    # Trim/loop both to OUTPUT_DURATION_S
    target_ms = OUTPUT_DURATION_S * 1000
    vox = vox[:target_ms] if len(vox) >= target_ms else vox * (target_ms // max(len(vox), 1) + 1)
    vox = vox[:target_ms]
    inst = inst[:target_ms] if len(inst) >= target_ms else inst * (target_ms // max(len(inst), 1) + 1)
    inst = inst[:target_ms]

    # Duck instrumental slightly so vocals sit on top
    inst = inst - 3
    mashup = inst.overlay(vox)

    out_path = Path(out_file).resolve()
    mashup.export(str(out_path), format="mp3", bitrate="192k", parameters=["-ar", "44100"])
    log.info("✅ Exported %s (%.1f sec, %.1f KB)", out_path, len(mashup) / 1000, out_path.stat().st_size / 1024)
    return out_path


# ── Main pipeline ────────────────────────────────────────────────────────────

def main():
    log.info("🎵 Viral Mashup Bot starting (work dir: %s)", WORK_DIR)

    # 1. Discover
    urls = find_trending_urls(TRENDING_QUERY, count=2)

    # 2. Fetch stems in parallel-ish (sequential is fine since HF Space throttles anyway)
    vocals_wav = fetch_stem(urls[0], "vocals", CLIP_SECONDS)
    instr_wav = fetch_stem(urls[1], "instrumental", CLIP_SECONDS)

    # 3. Musical alignment
    vox_bpm = estimate_bpm(vocals_wav)
    instr_bpm = estimate_bpm(instr_wav)
    vox_key = estimate_key(vocals_wav)
    instr_key = estimate_key(instr_wav)

    vocals_aligned = time_stretch(vocals_wav, vox_bpm, instr_bpm)
    vocals_aligned = pitch_shift_to_match(vocals_aligned, vox_key, instr_key)

    # 4. Export
    output = export_mashup(vocals_aligned, instr_wav)

    log.info("🎉 Done! Listen to %s", output)


if __name__ == "__main__":
    main()
