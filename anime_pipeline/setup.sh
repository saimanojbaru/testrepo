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

# Pre-download AnimeGANv3 ONNX scene models (recommended pipeline)
echo "[3/3] Pre-downloading AnimeGANv3 models..."
mkdir -p "$SCRIPT_DIR/models"
BASE="https://github.com/TachibanaYoshino/AnimeGANv3/releases/download/v1.1.0"
curl -sL -o "$SCRIPT_DIR/models/AnimeGANv3_shinkai.onnx" "$BASE/AnimeGANv3_Shinkai_37.onnx"
curl -sL -o "$SCRIPT_DIR/models/AnimeGANv3_hayao.onnx" "$BASE/AnimeGANv3_Hayao_36.onnx"
echo "Models downloaded to $SCRIPT_DIR/models/"

# Create working directories
mkdir -p "$SCRIPT_DIR/input" "$SCRIPT_DIR/output"

echo ""
echo "=== Setup Complete ==="
echo "Recommended (AnimeGANv3, full-resolution, sharp):"
echo "  python3 $SCRIPT_DIR/anime_convert_v3.py -i video.mp4 -o anime.mp4 --model shinkai"
echo "  Styles: shinkai (vibrant, default), hayao (Ghibli/warm)"
