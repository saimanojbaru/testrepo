#!/usr/bin/env bash
#
# Full anime conversion pipeline: style transfer + post-processing effects.
#
# Usage:
#   ./full_pipeline.sh input.mp4 output.mp4
#   ./full_pipeline.sh input.mp4 output.mp4 paprika
#
# This runs:
#   1. AnimeGANv2 style transfer (with enhance + edge lines)
#   2. Color grading (dramatic)
#   3. Vignette
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"

INPUT="${1:?Usage: $0 <input> <output> [style]}"
OUTPUT="${2:?Usage: $0 <input> <output> [style]}"
STYLE="${3:-face_paint_512_v2}"

INTERMEDIATE=$(mktemp /tmp/anime_intermediate_XXXXX.mp4)
trap "rm -f $INTERMEDIATE" EXIT

echo "=========================================="
echo "  Anime Video Conversion Pipeline"
echo "=========================================="
echo "Input:  $INPUT"
echo "Output: $OUTPUT"
echo "Style:  $STYLE"
echo ""

echo "--- Step 1: AnimeGANv2 Style Transfer ---"
python3 "$SCRIPT_DIR/anime_convert.py" \
    --input "$INPUT" \
    --output "$INTERMEDIATE" \
    --style "$STYLE" \
    --enhance \
    --edge-lines

echo ""
echo "--- Step 2: Post-Processing Effects ---"
python3 "$SCRIPT_DIR/effects.py" \
    --input "$INTERMEDIATE" \
    --output "$OUTPUT" \
    --color-grade dramatic \
    --vignette

echo ""
echo "=========================================="
echo "  Pipeline Complete!"
echo "  Output: $OUTPUT"
echo "=========================================="
