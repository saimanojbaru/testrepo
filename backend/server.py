"""
FastAPI backend for the Spotify Mashup Generator Android app.

The Android client submits a job and polls for status. Heavy processing
(yt-dlp, demucs, mixing) runs here on the server.

Start:
    cd /path/to/testrepo
    uvicorn backend.server:app --host 0.0.0.0 --port 8000 --reload

Android emulator reaches this at http://10.0.2.2:8000 (loopback alias).
Real devices on the same LAN use your machine's local IP, e.g. http://192.168.1.x:8000.
Update BASE_URL in the Android app's build.gradle accordingly.
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

# Allow importing the sibling spotify_mashup package
sys.path.insert(0, str(Path(__file__).parent.parent))

from spotify_mashup.pipeline import MashupPipeline, PipelineConfig
from spotify_mashup.stem_separator import Backend
from spotify_mashup.audio_downloader import AudioDownloader
from spotify_mashup.stem_separator import StemSeparator
from spotify_mashup.audio_manipulator import AudioManipulator
from spotify_mashup.audio_mixer import AudioMixer
from spotify_mashup.spotify_fetcher import SpotifyFetcher
from spotify_mashup.trending_detector import TrendingHookDetector, ViralHook

logging.basicConfig(
    format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
    datefmt="%H:%M:%S",
    level=logging.INFO,
)
logger = logging.getLogger(__name__)

app = FastAPI(
    title="Spotify Mashup Generator API",
    description="REST backend for the Android Spotify Mashup Generator",
    version="1.0.0",
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],   # tighten in production
    allow_methods=["*"],
    allow_headers=["*"],
)

_executor = ThreadPoolExecutor(max_workers=2)

# In-memory store — swap for Redis/SQLite in production
_jobs: dict[str, dict] = {}

OUTPUT_DIR = Path("./mashup_output")
OUTPUT_DIR.mkdir(parents=True, exist_ok=True)


# ── Pydantic models ───────────────────────────────────────────────────────────

class MashupRequest(BaseModel):
    """
    track_a / track_b are either:
      - A Spotify URL: https://open.spotify.com/track/<id>
      - A free-text YouTube search query (when youtube_only=True)
    """
    track_a: str
    track_b: str
    youtube_only: bool = False
    bpm_a: Optional[float] = None
    bpm_b: Optional[float] = None
    key_a: Optional[int] = None   # 0=C … 11=B
    key_b: Optional[int] = None
    apply_pitch_shift: bool = True
    stem_backend: str = "demucs"
    # New: optional trim windows so the mashup uses just the viral hook of each track.
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


class JobResponse(BaseModel):
    job_id: str
    status: str      # pending | running | done | failed
    message: str
    progress: int    # 0–100


class TrendingHookRequest(BaseModel):
    spotify_url: str
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
    bpm_score: float          # 0-1 — how close the tempos are
    key_score: float          # 0-1 — Camelot-wheel compatibility
    energy_score: float       # 0-1 — danceability/energy overlap
    overall_score: float      # 0-1 — combined
    suggested_pitch_shift: Optional[int]   # semitones, A toward B
    suggested_tempo_ratio: Optional[float] # tempo of B / tempo of A


# ── Endpoints ─────────────────────────────────────────────────────────────────

@app.get("/health")
def health():
    return {"status": "ok"}


@app.post("/api/mashup", response_model=JobResponse, status_code=202)
async def create_mashup(req: MashupRequest, bg: BackgroundTasks):
    job_id = str(uuid.uuid4())
    _set_job(job_id, status="pending", message="Job queued", progress=0)
    bg.add_task(_run_pipeline, job_id, req)
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
    Returns the most viral candidate hooks of a track, ranked by a blend
    of Spotify audio analysis (loudness/section confidence/position) and
    best-effort external signals (TikTok / YouTube comment timestamps,
    Genius chorus markers).
    """
    fetcher = SpotifyFetcher()
    metadata = fetcher.fetch(req.spotify_url)
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


# ── Track-vs-track compatibility ─────────────────────────────────────────────

@app.post("/api/compatibility", response_model=CompatibilityResponse)
def compatibility(req: CompatibilityRequest):
    """Score how well two Spotify tracks will blend, with concrete suggestions."""
    fetcher = SpotifyFetcher()
    a = fetcher.fetch(req.spotify_url_a)
    b = fetcher.fetch(req.spotify_url_b)

    # BPM closeness — full score within ±2 BPM, falls off to 0 at ±20 BPM.
    bpm_score = 0.0
    if a.bpm and b.bpm:
        diff = abs(a.bpm - b.bpm)
        bpm_score = max(0.0, 1.0 - (max(diff - 2.0, 0.0) / 18.0))

    # Camelot-wheel compatibility: same key (1.0), ±1 semitone (0.7), relative maj/min (0.85), else 0.3.
    key_score = 0.5
    if a.key is not None and b.key is not None and a.key >= 0 and b.key >= 0:
        if a.key == b.key and a.mode == b.mode:
            key_score = 1.0
        elif (a.key - b.key) % 12 in (1, 11):
            key_score = 0.7
        elif a.mode != b.mode and ((a.key - b.key) % 12 == 9 if a.mode == 1 else (b.key - a.key) % 12 == 9):
            key_score = 0.85
        else:
            key_score = 0.3

    # Energy overlap is approximate — Spotify's "energy" feature normalises 0-1.
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
        bpm_a=a.bpm,
        bpm_b=b.bpm,
        key_a_label=a.key_label,
        key_b_label=b.key_label,
        bpm_score=bpm_score,
        key_score=key_score,
        energy_score=energy_score,
        overall_score=overall,
        suggested_pitch_shift=pitch_shift,
        suggested_tempo_ratio=tempo_ratio,
    )


# ── Background pipeline ───────────────────────────────────────────────────────

async def _run_pipeline(job_id: str, req: MashupRequest) -> None:
    _set_job(job_id, status="running", message="Pipeline starting…", progress=5)
    loop = asyncio.get_event_loop()
    try:
        result = await loop.run_in_executor(_executor, lambda: _sync_run(job_id, req))
        _set_job(job_id, status="done", message="Mashup ready!", progress=100, result_file=str(result))
        logger.info("Job %s complete → %s", job_id, result)
    except Exception as exc:
        logger.exception("Job %s failed", job_id)
        _set_job(job_id, status="failed", message=str(exc), progress=0)


def _sync_run(job_id: str, req: MashupRequest) -> Path:
    import tempfile

    if req.youtube_only:
        # ── YouTube-only path ────────────────────────────────────────────
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

        _set_job(job_id, message="Separating stems (this takes a few minutes)…", progress=35)
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

        _set_job(job_id, message="Mixing & exporting MP3…", progress=88)
        mixer = AudioMixer(OUTPUT_DIR)
        return mixer.mix(matched, instr, name_a, name_b)

    else:
        # ── Spotify mode path ────────────────────────────────────────────
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


# ── Helpers ───────────────────────────────────────────────────────────────────

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
