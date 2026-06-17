#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

echo "=== Anime Video Conversion Pipeline Setup ==="

# Install ffmpeg
if ! command -v ffmpeg &>/dev/null; then
    echo "[1/3] Installing ffmpeg..."
    sudo apt-get update -qq && sudo apt-get install -y -qq ffmpeg
else
    echo "[1/3] ffmpeg already installed: $(ffmpeg -version 2>&1 | head -1)"
fi

# Install Python dependencies
echo "[2/3] Installing Python dependencies..."
pip install -r "$SCRIPT_DIR/requirements.txt"

# Pre-download AnimeGANv2 model weights
echo "[3/3] Pre-downloading AnimeGANv2 model weights..."
python3 -c "
import torch
torch.hub.load('bryandlee/animegan2-pytorch:main', 'generator', pretrained='face_paint_512_v2', trust_repo=True)
torch.hub.load('bryandlee/animegan2-pytorch:main', 'generator', pretrained='celeba_distill', trust_repo=True)
torch.hub.load('bryandlee/animegan2-pytorch:main', 'generator', pretrained='paprika', trust_repo=True)
print('Models downloaded successfully.')
"

# Create working directories
mkdir -p "$SCRIPT_DIR/input" "$SCRIPT_DIR/output" "$SCRIPT_DIR/temp_frames"

echo ""
echo "=== Setup Complete ==="
echo "Usage:  python3 $SCRIPT_DIR/anime_convert.py --input video.mp4 --output anime_video.mp4"
echo "Styles: face_paint_512_v2 (default), celeba_distill, paprika"
