#!/usr/bin/env python3
"""
Batch convert multiple videos to anime style.

Usage:
  python batch_convert.py --input-dir videos/ --output-dir anime_videos/
  python batch_convert.py --input-dir videos/ --output-dir anime_videos/ --style paprika --enhance
"""

import argparse
import os
import sys
from pathlib import Path

from anime_convert import STYLES, convert_video

VIDEO_EXTENSIONS = {".mp4", ".avi", ".mov", ".mkv", ".webm", ".flv", ".wmv"}


def find_videos(directory: str) -> list[Path]:
    videos = []
    for f in sorted(Path(directory).iterdir()):
        if f.is_file() and f.suffix.lower() in VIDEO_EXTENSIONS:
            videos.append(f)
    return videos


def main():
    parser = argparse.ArgumentParser(description="Batch convert videos to anime style")
    parser.add_argument("--input-dir", required=True, help="Directory containing input videos")
    parser.add_argument("--output-dir", required=True, help="Directory for output videos")
    parser.add_argument("-s", "--style", choices=STYLES, default="face_paint_512_v2")
    parser.add_argument("--enhance", action="store_true")
    parser.add_argument("--edge-lines", action="store_true")
    parser.add_argument("--edge-strength", type=float, default=0.3)
    parser.add_argument("--resolution", type=int, default=None)
    parser.add_argument("--no-audio", action="store_true")

    args = parser.parse_args()

    if not os.path.isdir(args.input_dir):
        print(f"Error: Input directory not found: {args.input_dir}", file=sys.stderr)
        sys.exit(1)

    os.makedirs(args.output_dir, exist_ok=True)
    videos = find_videos(args.input_dir)

    if not videos:
        print(f"No video files found in {args.input_dir}")
        sys.exit(0)

    print(f"Found {len(videos)} video(s) to convert.\n")

    for idx, video in enumerate(videos, 1):
        output_name = f"{video.stem}_anime{video.suffix}"
        output_path = os.path.join(args.output_dir, output_name)
        print(f"\n{'='*60}")
        print(f"[{idx}/{len(videos)}] {video.name} -> {output_name}")
        print(f"{'='*60}")

        try:
            convert_video(
                input_path=str(video),
                output_path=output_path,
                style=args.style,
                enhance=args.enhance,
                edge_lines=args.edge_lines,
                edge_strength=args.edge_strength,
                target_resolution=args.resolution,
                skip_audio=args.no_audio,
            )
        except Exception as e:
            print(f"Error processing {video.name}: {e}", file=sys.stderr)
            continue

    print(f"\nBatch complete! Outputs in: {args.output_dir}")


if __name__ == "__main__":
    main()
