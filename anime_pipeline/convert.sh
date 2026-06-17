#!/usr/bin/env bash
#
# Quick-convert a video to anime style.
#
# Usage:
#   ./convert.sh input.mp4                         # Default style, output to input_anime.mp4
#   ./convert.sh input.mp4 paprika                 # Paprika style
#   ./convert.sh input.mp4 face_paint_512_v2 1080  # Default style, 1080p output
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

INPUT="${1:?Usage: $0 <input_video> [style] [resolution]}"
STYLE="${2:-face_paint_512_v2}"
RESOLUTION="${3:-}"

BASENAME="$(basename "${INPUT%.*}")"
EXTENSION="${INPUT##*.}"
OUTPUT="${BASENAME}_anime.${EXTENSION}"

ARGS=( --input "$INPUT" --output "$OUTPUT" --style "$STYLE" --enhance --edge-lines )

if [[ -n "$RESOLUTION" ]]; then
    ARGS+=( --resolution "$RESOLUTION" )
fi

echo "Converting: $INPUT -> $OUTPUT (style: $STYLE)"
python3 "$SCRIPT_DIR/anime_convert.py" "${ARGS[@]}"
