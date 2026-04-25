# Hugging Face Spaces — Mashup Studio backend
# Port 7860 is the HF Spaces standard.
#
# Set these secrets in your Space settings (Settings → Variables and secrets):
#   SPOTIFY_CLIENT_ID       — from developer.spotify.com
#   SPOTIFY_CLIENT_SECRET   — from developer.spotify.com
#   YOUTUBE_API_KEY         — from Google Cloud Console → YouTube Data API v3
#
# Deploy:
#   1. Create a new Space: https://huggingface.co/new-space
#      SDK: Docker  |  Hardware: CPU Basic (free)
#   2. Push this repo (or just the relevant dirs) to the Space repo.
#   3. Add the three secrets above.
#   4. The Space URL (https://<user>-<space>.hf.space) is your backend URL.

FROM python:3.11-slim

# System deps: ffmpeg for audio, rubberband for time-stretch, git for yt-dlp
RUN apt-get update && apt-get install -y --no-install-recommends \
        ffmpeg \
        rubberband-cli \
        git \
        curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

# Install PyTorch CPU-only first (smaller, sufficient for demucs on free tier)
RUN pip install --no-cache-dir \
    torch==2.2.2 torchaudio==2.2.2 \
    --index-url https://download.pytorch.org/whl/cpu

# Install all Python deps
COPY spotify_mashup/requirements.txt spotify_mashup/requirements.txt
COPY backend/requirements.txt backend/requirements.txt
RUN pip install --no-cache-dir -r spotify_mashup/requirements.txt
RUN pip install --no-cache-dir -r backend/requirements.txt

# Pre-download the demucs htdemucs model so first request is fast
RUN python -c "from demucs.pretrained import get_model; get_model('htdemucs')" || true

# Copy source
COPY spotify_mashup/ spotify_mashup/
COPY backend/ backend/

RUN mkdir -p mashup_output

# HF Spaces expects port 7860
EXPOSE 7860

CMD ["uvicorn", "backend.server:app", "--host", "0.0.0.0", "--port", "7860", "--workers", "1"]
