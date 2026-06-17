#!/usr/bin/env python3
"""
Post-processing effects for anime-converted videos.
Apply speed ramps, zoom effects, and dynamic shading via FFmpeg.

Usage:
  python effects.py --input anime.mp4 --output final.mp4 --speed-ramp
  python effects.py --input anime.mp4 --output final.mp4 --vignette --color-grade warm
"""

import argparse
import os
import subprocess
import sys


def apply_speed_ramp(input_path: str, output_path: str, slow_start: float = 0.5, slow_end: float = 0.7):
    duration = get_duration(input_path)
    slow_start_t = duration * slow_start
    slow_end_t = duration * slow_end

    filter_complex = (
        f"[0:v]setpts="
        f"if(lt(T\\,{slow_start_t})\\,PTS-STARTPTS\\,"
        f"if(lt(T\\,{slow_end_t})\\,(PTS-STARTPTS)*2.5\\,"
        f"PTS-STARTPTS))"
        f"[v];"
        f"[0:a]atempo=1.0[a]"
    )

    subprocess.run(
        [
            "ffmpeg", "-y", "-i", input_path,
            "-filter_complex", filter_complex,
            "-map", "[v]", "-map", "[a]",
            "-c:v", "libx264", "-crf", "20", "-preset", "medium",
            "-c:a", "aac", "-b:a", "192k",
            output_path,
        ],
        check=True,
    )


def apply_vignette(input_path: str, output_path: str, strength: float = 0.4):
    subprocess.run(
        [
            "ffmpeg", "-y", "-i", input_path,
            "-vf", f"vignette=PI/{2 + strength * 3}",
            "-c:v", "libx264", "-crf", "20",
            "-c:a", "copy",
            output_path,
        ],
        check=True,
    )


def apply_color_grade(input_path: str, output_path: str, grade: str = "warm"):
    grades = {
        "warm": "curves=r='0/0 0.3/0.35 0.7/0.75 1/1':b='0/0 0.3/0.25 0.7/0.65 1/1'",
        "cool": "curves=b='0/0 0.3/0.35 0.7/0.75 1/1':r='0/0 0.3/0.25 0.7/0.65 1/1'",
        "dramatic": "eq=contrast=1.3:brightness=-0.05:saturation=1.4",
        "pastel": "eq=saturation=0.7:brightness=0.05,curves=all='0/0 0.25/0.3 0.75/0.8 1/1'",
    }

    if grade not in grades:
        print(f"Unknown grade: {grade}. Available: {list(grades.keys())}")
        sys.exit(1)

    subprocess.run(
        [
            "ffmpeg", "-y", "-i", input_path,
            "-vf", grades[grade],
            "-c:v", "libx264", "-crf", "20",
            "-c:a", "copy",
            output_path,
        ],
        check=True,
    )


def apply_shonen_zoom(input_path: str, output_path: str, zoom_point: float = 0.5, zoom_factor: float = 1.3):
    duration = get_duration(input_path)
    t = duration * zoom_point

    vf = (
        f"zoompan=z='if(between(in_time,{t},{t+0.3}),{zoom_factor},1)'"
        f":d=1:s=1920x1080:fps=30"
    )

    subprocess.run(
        [
            "ffmpeg", "-y", "-i", input_path,
            "-vf", vf,
            "-c:v", "libx264", "-crf", "20",
            "-c:a", "copy",
            output_path,
        ],
        check=True,
    )


def get_duration(path: str) -> float:
    result = subprocess.run(
        ["ffprobe", "-v", "quiet", "-show_entries", "format=duration", "-of", "csv=p=0", path],
        capture_output=True,
        text=True,
        check=True,
    )
    return float(result.stdout.strip())


def main():
    parser = argparse.ArgumentParser(description="Post-processing effects for anime videos")
    parser.add_argument("-i", "--input", required=True, help="Input anime video")
    parser.add_argument("-o", "--output", required=True, help="Output video")
    parser.add_argument("--speed-ramp", action="store_true", help="Add dramatic slow-mo ramp")
    parser.add_argument("--vignette", action="store_true", help="Add vignette darkening")
    parser.add_argument("--color-grade", choices=["warm", "cool", "dramatic", "pastel"], help="Apply color grade")
    parser.add_argument("--zoom", action="store_true", help="Add shonen-style zoom punch")

    args = parser.parse_args()

    if not os.path.isfile(args.input):
        print(f"Error: {args.input} not found", file=sys.stderr)
        sys.exit(1)

    current = args.input
    import tempfile
    tmpfiles = []

    effects = [
        (args.speed_ramp, lambda i, o: apply_speed_ramp(i, o)),
        (args.vignette, lambda i, o: apply_vignette(i, o)),
        (args.color_grade, lambda i, o: apply_color_grade(i, o, args.color_grade)),
        (args.zoom, lambda i, o: apply_shonen_zoom(i, o)),
    ]

    active = [(enabled, fn) for enabled, fn in effects if enabled]

    if not active:
        print("No effects selected. Use --speed-ramp, --vignette, --color-grade, or --zoom.")
        sys.exit(0)

    for idx, (_, fn) in enumerate(active):
        is_last = idx == len(active) - 1
        out = args.output if is_last else tempfile.mktemp(suffix=".mp4")
        if not is_last:
            tmpfiles.append(out)
        print(f"Applying effect {idx + 1}/{len(active)}...")
        fn(current, out)
        current = out

    for tmp in tmpfiles:
        os.unlink(tmp)

    print(f"Done! Output: {args.output}")


if __name__ == "__main__":
    main()
