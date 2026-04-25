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
