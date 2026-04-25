"""
FastAPI backend for the Mashup Studio Android app.

Start locally:
    uvicorn backend.server:app --host 0.0.0.0 --port 8000 --reload

Hugging Face Spaces deployment:
    See Dockerfile — the Space binds to port 7860.

Required env vars (set as HF Space secrets):
    SPOTIFY_CLIENT_ID / SPOTIFY_CLIENT_SECRET  — from developer.spotify.com
    YOUTUBE_API_KEY                             — from Google Cloud Console
"""

import asyncio
import logging
import sys
import uuid
from concurrent.futures import ThreadPoolExecutor
from pathlib import Path
from typing import Optional

from fastapi import BackgroundTasks, FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import FileResponse
from pydantic import BaseModel, field_validator

sys.path.insert(0, str(Path(__file__).parent.parent))

from spotify_mashup.pipeline import MashupPipeline, PipelineConfig
from spotify_mashup.stem_separator import Backend
from spotify_mashup.audio_downloader import AudioDownloader
from spotify_mashup.stem_separator import StemSeparator
from spotify_mashup.audio_manipulator import AudioManipulator
from spotify_mashup.audio_mixer import AudioMixer
from spotify_mashup.spotify_fetcher import SpotifyFetcher
from spotify_mashup.trending_detector import TrendingHookDetector
from spotify_mashup.search import search as do_search, SearchResult

logging.basicConfig(
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%H:%M:%S",
    level=logging.INFO,
)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="Mashup Studio API",
    description="REST backend for the Mashup Studio Android app",
    version="2.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

_executor = ThreadPoolExecutor(max_workers=2)
_jobs: dict[str, dict] = {}

OUTPUT_DIR = Path("./mashup_output")
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)


# ── Pydantic models ───────────────────────────────────────────────────────────

class MashupRequest(BaseModel):
    track_a: str
    track_b: str
    youtube_only: bool = False
    bpm_a: Optional[float] = None
    bpm_b: Optional[float] = None
    key_a: Optional[int] = None
    key_b: Optional[int] = None
    apply_pitch_shift: bool = True
    stem_backend: str = "demucs"
    hook_a_start_ms: Optional[int] = None
    hook_a_end_ms: Optional[int] = None
    hook_b_start_ms: Optional[int] = None
    hook_b_end_ms: Optional[int] = None

    @field_validator("stem_backend")
    @classmethod
    def _check_backend(cls, v: str) -> str:
        if v not in ("demucs", "spleeter"):
            raise ValueError("stem_backend must be 'demucs' or 'spleeter'")
        return v


class TrackInput(BaseModel):
    """One track in a multi-track mashup request."""
    url: str                          # Spotify URL, YouTube URL, or search query
    role: str = "full"               # vocals | instrumental | drums | melody | full
    hook_start_ms: Optional[int] = None
    hook_end_ms: Optional[int] = None
    bpm_override: Optional[float] = None
    pitch_shift: int = 0             # semitones, applied before mixing
    volume: float = 1.0              # 0.0-2.0


class MultiMashupRequest(BaseModel):
    tracks: list[TrackInput]         # 2-4 tracks
    apply_pitch_shift: bool = True
    youtube_only: bool = False
    stem_backend: str = "demucs"

    @field_validator("tracks")
    @classmethod
    def _check_tracks(cls, v: list) -> list:
        if not (2 <= len(v) <= 4):
            raise ValueError("tracks must have 2-4 items")
        return v

    @field_validator("stem_backend")
    @classmethod
    def _check_backend(cls, v: str) -> str:
        if v not in ("demucs", "spleeter"):
            raise ValueError("stem_backend must be 'demucs' or 'spleeter'")
        return v


class JobResponse(BaseModel):
    job_id: str
    status: str
    message: str
    progress: int


class TrendingHookRequest(BaseModel):
    spotify_url: str   # accepts Spotify URL, YouTube URL, or plain "Artist - Title"
    top_k: int = 5


class HookDto(BaseModel):
    start_ms: int
    end_ms: int
    duration_ms: int
    score: float
    confidence: float
    label: str
    reasons: list[str]
    signals: dict[str, float]


class TrendingHookResponse(BaseModel):
    track_id: str
    track_name: str
    artist_name: str
    duration_ms: int
    bpm: Optional[float]
    key_label: Optional[str]
    hooks: list[HookDto]


class CompatibilityRequest(BaseModel):
    spotify_url_a: str
    spotify_url_b: str


class CompatibilityResponse(BaseModel):
    bpm_a: Optional[float]
    bpm_b: Optional[float]
    key_a_label: Optional[str]
    key_b_label: Optional[str]
    bpm_score: float
    key_score: float
    energy_score: float
    overall_score: float
    suggested_pitch_shift: Optional[int]
    suggested_tempo_ratio: Optional[float]


class SearchRequest(BaseModel):
    query: str
    source: str = "spotify"   # "spotify" | "youtube" | "both"
    limit: int = 8


class SearchResultDto(BaseModel):
    id: str
    title: str
    artist: str
    duration_ms: int
    thumbnail_url: str
    preview_url: Optional[str]
    source: str
    url: str


class SearchResponse(BaseModel):
    results: list[SearchResultDto]


# ── Endpoints ─────────────────────────────────────────────────────────────────

@app.get("/health")
def health():
    return {"status": "ok", "version": "2.0.0"}


# ── Search ───────────────────────────────────────────────────────────────────

@app.post("/api/search", response_model=SearchResponse)
def search(req: SearchRequest):
    """
    Search for tracks on Spotify and/or YouTube by free-text query.
    source: "spotify" | "youtube" | "both"
    """
    try:
        results = do_search(req.query, source=req.source, limit=req.limit)
        return SearchResponse(results=[
            SearchResultDto(
                id=r.id,
                title=r.title,
                artist=r.artist,
                duration_ms=r.duration_ms,
                thumbnail_url=r.thumbnail_url,
                preview_url=r.preview_url,
                source=r.source,
                url=r.url,
            )
            for r in results
        ])
    except Exception as exc:
        raise HTTPException(500, str(exc))


# ── Mashup (legacy 2-track) ──────────────────────────────────────────────────

@app.post("/api/mashup", response_model=JobResponse, status_code=202)
async def create_mashup(req: MashupRequest, bg: BackgroundTasks):
    job_id = str(uuid.uuid4())
    _set_job(job_id, status="pending", message="Job queued", progress=0)
    bg.add_task(_run_pipeline, job_id, req)
    return _job_response(job_id)


# ── Mashup (multi-track, 2-4 tracks) ────────────────────────────────────────

@app.post("/api/mashup/multi", response_model=JobResponse, status_code=202)
async def create_multi_mashup(req: MultiMashupRequest, bg: BackgroundTasks):
    job_id = str(uuid.uuid4())
    _set_job(job_id, status="pending", message="Job queued", progress=0)
    bg.add_task(_run_multi_pipeline, job_id, req)
    return _job_response(job_id)


@app.get("/api/mashup/{job_id}", response_model=JobResponse)
def get_job(job_id: str):
    _require(job_id)
    return _job_response(job_id)


@app.get("/api/mashup/{job_id}/download")
def download_mashup(job_id: str):
    job = _require(job_id)
    if job["status"] != "done":
        raise HTTPException(400, f"Job not ready — status: {job['status']}")
    path = Path(job["result_file"])
    if not path.exists():
        raise HTTPException(500, "Output file missing from server")
    return FileResponse(
        str(path),
        media_type="audio/mpeg",
        filename=path.name,
        headers={"Content-Disposition": f'attachment; filename="{path.name}"'},
    )


# ── Trending hook detection ──────────────────────────────────────────────────

@app.post("/api/trending-hook", response_model=TrendingHookResponse)
def trending_hook(req: TrendingHookRequest):
    """
    Accepts:
      - Spotify track URL  → full audio-analysis + YouTube + Genius signals
      - YouTube video URL  → YouTube timestamps + Genius markers (no Spotify needed)
      - "Artist - Title"   → same as YouTube path
    """
    url = req.spotify_url.strip()
    is_spotify = "open.spotify.com/track" in url
    is_youtube = "youtube.com/watch" in url or "youtu.be/" in url

    if is_spotify:
        fetcher = SpotifyFetcher()
        if not fetcher.available:
            raise HTTPException(
                503,
                "Spotify credentials not configured on the server. "
                "Set SPOTIFY_CLIENT_ID and SPOTIFY_CLIENT_SECRET env vars, "
                "or use YouTube search instead."
            )
        metadata = fetcher.fetch(url)
        detector = TrendingHookDetector(fetcher._sp)
        hooks = detector.detect(metadata.track_id, top_k=req.top_k)
        return TrendingHookResponse(
            track_id=metadata.track_id,
            track_name=metadata.track_name,
            artist_name=metadata.artist_name,
            duration_ms=metadata.duration_ms,
            bpm=metadata.bpm,
            key_label=metadata.key_label,
            hooks=[HookDto(**h.as_dict()) for h in hooks],
        )

    # YouTube or plain query — metadata-only path (no Spotify creds required)
    title, artist, duration_s, track_id = _resolve_youtube_meta(url)
    detector = TrendingHookDetector(spotify_client=None)
    hooks = detector.detect_from_metadata(title, artist, duration_s, top_k=req.top_k)
    return TrendingHookResponse(
        track_id=track_id,
        track_name=title,
        artist_name=artist,
        duration_ms=int(duration_s * 1000),
        bpm=None,
        key_label=None,
        hooks=[HookDto(**h.as_dict()) for h in hooks],
    )


# ── Compatibility ────────────────────────────────────────────────────────────

@app.post("/api/compatibility", response_model=CompatibilityResponse)
def compatibility(req: CompatibilityRequest):
    import math
    fetcher = SpotifyFetcher()
    a = fetcher.fetch(req.spotify_url_a)
    b = fetcher.fetch(req.spotify_url_b)

    bpm_score = 0.0
    if a.bpm and b.bpm:
        diff = abs(a.bpm - b.bpm)
        bpm_score = max(0.0, 1.0 - (max(diff - 2.0, 0.0) / 18.0))

    key_score = 0.5
    if a.key is not None and b.key is not None and a.key >= 0 and b.key >= 0:
        if a.key == b.key and a.mode == b.mode:
            key_score = 1.0
        elif (a.key - b.key) % 12 in (1, 11):
            key_score = 0.7
        elif a.mode != b.mode and (
            (a.key - b.key) % 12 == 9 if a.mode == 1 else (b.key - a.key) % 12 == 9
        ):
            key_score = 0.85
        else:
            key_score = 0.3

    try:
        feats = fetcher._sp.audio_features([a.track_id, b.track_id])
        e_a = float(feats[0].get("energy", 0.5)) if feats and feats[0] else 0.5
        e_b = float(feats[1].get("energy", 0.5)) if feats and feats[1] else 0.5
        energy_score = 1.0 - abs(e_a - e_b)
    except Exception:
        energy_score = 0.5

    overall = 0.45 * bpm_score + 0.35 * key_score + 0.20 * energy_score

    pitch_shift = None
    if a.key is not None and b.key is not None and a.key >= 0 and b.key >= 0:
        diff = (b.key - a.key) % 12
        pitch_shift = diff if diff <= 6 else diff - 12

    tempo_ratio = (b.bpm / a.bpm) if (a.bpm and b.bpm) else None

    return CompatibilityResponse(
        bpm_a=a.bpm, bpm_b=b.bpm,
        key_a_label=a.key_label, key_b_label=b.key_label,
        bpm_score=bpm_score, key_score=key_score,
        energy_score=energy_score, overall_score=overall,
        suggested_pitch_shift=pitch_shift,
        suggested_tempo_ratio=tempo_ratio,
    )


# ── Background pipelines ──────────────────────────────────────────────────────

async def _run_pipeline(job_id: str, req: MashupRequest) -> None:
    _set_job(job_id, status="running", message="Pipeline starting…", progress=5)
    loop = asyncio.get_event_loop()
    try:
        result = await loop.run_in_executor(_executor, lambda: _sync_run(job_id, req))
        _set_job(job_id, status="done", message="Mashup ready!", progress=100, result_file=str(result))
    except Exception as exc:
        logger.exception("Job %s failed", job_id)
        _set_job(job_id, status="failed", message=str(exc), progress=0)


async def _run_multi_pipeline(job_id: str, req: MultiMashupRequest) -> None:
    _set_job(job_id, status="running", message="Pipeline starting…", progress=5)
    loop = asyncio.get_event_loop()
    try:
        result = await loop.run_in_executor(_executor, lambda: _sync_multi_run(job_id, req))
        _set_job(job_id, status="done", message="Mashup ready!", progress=100, result_file=str(result))
    except Exception as exc:
        logger.exception("Multi job %s failed", job_id)
        _set_job(job_id, status="failed", message=str(exc), progress=0)


def _sync_run(job_id: str, req: MashupRequest) -> Path:
    import tempfile

    if req.youtube_only:
        work = Path(tempfile.mkdtemp(prefix=f"mashup_{job_id[:8]}_"))
        _set_job(job_id, message="Downloading from YouTube…", progress=15)
        dl = AudioDownloader(work / "downloads")

        def _split(q: str) -> tuple[str, str]:
            parts = q.split("-", 1)
            return (parts[1].strip(), parts[0].strip()) if len(parts) == 2 else (q, "")

        name_a, artist_a = _split(req.track_a)
        name_b, artist_b = _split(req.track_b)
        wav_a = dl.download(name_a, artist_a, "track_a")
        wav_b = dl.download(name_b, artist_b, "track_b")

        _set_job(job_id, message="Separating stems…", progress=35)
        sep = StemSeparator(work / "stems", backend=Backend(req.stem_backend))
        vocals = sep.extract_vocals(wav_a)
        instr = sep.extract_instrumental(wav_b)

        _set_job(job_id, message="Beat-matching…", progress=70)
        manip = AudioManipulator(work / "processed")
        matched = manip.beat_match(
            vocals,
            target_bpm=req.bpm_b or 120.0,
            source_bpm=req.bpm_a,
            vocals_key=req.key_a,
            target_key=req.key_b,
            apply_pitch_shift=req.apply_pitch_shift,
        )

        _set_job(job_id, message="Mixing & exporting…", progress=88)
        mixer = AudioMixer(OUTPUT_DIR)
        return mixer.mix(matched, instr, name_a, name_b)

    else:
        _set_job(job_id, message="Fetching Spotify metadata…", progress=10)
        config = PipelineConfig(
            output_dir=OUTPUT_DIR,
            stem_backend=Backend(req.stem_backend),
            apply_pitch_shift=req.apply_pitch_shift,
            override_bpm_a=req.bpm_a,
            override_bpm_b=req.bpm_b,
        )
        pipeline = MashupPipeline(config)
        return pipeline.run(req.track_a, req.track_b)


def _sync_multi_run(job_id: str, req: MultiMashupRequest) -> Path:
    """
    Multi-track pipeline: downloads and separates each track by role, then
    mixes them all together. Roles:
      vocals      → extract vocals stem
      instrumental → extract instrumental (no vocals)
      drums       → extract drums stem (demucs 4-stem only)
      melody      → extract other/melody stem
      full        → use full track without separation
    """
    import tempfile

    work = Path(tempfile.mkdtemp(prefix=f"mashup_{job_id[:8]}_"))
    dl = AudioDownloader(work / "downloads")
    sep = StemSeparator(work / "stems", backend=Backend(req.stem_backend))
    mixer = AudioMixer(OUTPUT_DIR)

    n = len(req.tracks)
    stems: list[Path] = []
    names: list[str] = []

    for i, track in enumerate(req.tracks):
        pct_base = 15 + (i * 50 // n)
        _set_job(job_id, message=f"Downloading track {i + 1}/{n}…", progress=pct_base)

        # Resolve URL → local WAV
        if "youtube.com" in track.url or "youtu.be" in track.url:
            parts = track.url.split("?v=")
            vid_id = parts[1][:11] if len(parts) > 1 else track.url[-11:]
            wav = dl.download(vid_id, "", f"track_{i}")
            names.append(f"Track{i + 1}")
        elif "spotify.com" in track.url:
            fetcher = SpotifyFetcher()
            meta = fetcher.fetch(track.url)
            wav = dl.download(meta.track_name, meta.artist_name, f"track_{i}")
            names.append(meta.track_name)
        else:
            # Treat as search query
            wav = dl.download(track.url, "", f"track_{i}")
            names.append(track.url[:20])

        _set_job(job_id, message=f"Separating track {i + 1}/{n} ({track.role})…",
                 progress=pct_base + (25 // n))

        if track.role == "vocals":
            stem = sep.extract_vocals(wav)
        elif track.role == "instrumental":
            stem = sep.extract_instrumental(wav)
        elif track.role == "drums":
            stem = sep.extract_stem(wav, "drums") if hasattr(sep, "extract_stem") else sep.extract_instrumental(wav)
        elif track.role == "melody":
            stem = sep.extract_stem(wav, "other") if hasattr(sep, "extract_stem") else sep.extract_vocals(wav)
        else:
            stem = wav  # "full" — use as-is

        stems.append(stem)

    _set_job(job_id, message="Beat-matching and mixing…", progress=75)

    # Mix all stems together using the first track's BPM as reference
    ref_bpm = req.tracks[0].bpm_override
    manip = AudioManipulator(work / "processed")
    processed = []
    for stem, track in zip(stems, req.tracks):
        if track.bpm_override or track.pitch_shift:
            p = manip.beat_match(
                stem,
                target_bpm=ref_bpm or track.bpm_override or 120.0,
                source_bpm=track.bpm_override,
                apply_pitch_shift=req.apply_pitch_shift,
            )
        else:
            p = stem
        processed.append(p)

    _set_job(job_id, message="Final mix…", progress=90)
    return mixer.mix_multi(processed, names) if hasattr(mixer, "mix_multi") else mixer.mix(
        processed[0], processed[1], names[0], names[1]
    )


# ── Helpers ───────────────────────────────────────────────────────────────────

def _resolve_youtube_meta(url_or_query: str) -> tuple[str, str, float, str]:
    """Return (title, artist, duration_seconds, track_id) for a YouTube URL or query."""
    import urllib.request, urllib.parse, json, re

    if "youtube.com/watch" in url_or_query or "youtu.be/" in url_or_query:
        # Use YouTube oEmbed to get title; duration needs yt-dlp or Data API
        try:
            oe_url = f"https://www.youtube.com/oembed?url={urllib.parse.quote(url_or_query)}&format=json"
            with urllib.request.urlopen(oe_url, timeout=8) as r:
                oe = json.loads(r.read())
            full_title = oe.get("title", "Unknown Track")
            author = oe.get("author_name", "Unknown Artist")
            # Parse "Title - Artist" or "Artist - Title" conventions
            parts = re.split(r"\s[\-–]\s", full_title, maxsplit=1)
            title = parts[0].strip() if len(parts) == 1 else parts[1].strip()
            artist_hint = parts[0].strip() if len(parts) > 1 else author
            # Extract video ID
            m = re.search(r"(?:v=|youtu\.be/)([A-Za-z0-9_-]{11})", url_or_query)
            vid_id = m.group(1) if m else url_or_query[-11:]
            return title, artist_hint, 210.0, vid_id  # duration fallback 3m30s
        except Exception:
            pass

    # Plain text query — split "Artist - Title"
    parts = re.split(r"\s[\-–]\s", url_or_query, maxsplit=1)
    if len(parts) == 2:
        artist, title = parts[0].strip(), parts[1].strip()
    else:
        title, artist = url_or_query.strip(), ""
    return title, artist, 210.0, url_or_query[:32]


def _set_job(job_id: str, **kwargs) -> None:
    if job_id not in _jobs:
        _jobs[job_id] = {}
    _jobs[job_id].update(kwargs)


def _require(job_id: str) -> dict:
    if job_id not in _jobs:
        raise HTTPException(404, "Job not found")
    return _jobs[job_id]


def _job_response(job_id: str) -> JobResponse:
    j = _jobs[job_id]
    return JobResponse(
        job_id=job_id,
        status=j["status"],
        message=j["message"],
        progress=j["progress"],
    )
