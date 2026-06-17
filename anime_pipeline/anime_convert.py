#!/usr/bin/env python3
"""
Anime Video Conversion Pipeline
Convert real video footage into anime style using AnimeGANv2.

Supports three anime styles:
  - face_paint_512_v2 (default): Bold lines, vibrant colors — best for faces/action
  - celeba_distill: Softer, more realistic anime look
  - paprika: Satoshi Kon's Paprika movie style — dreamy, surreal

Usage:
  python anime_convert.py --input video.mp4 --output anime.mp4
  python anime_convert.py --input video.mp4 --output anime.mp4 --style paprika
  python anime_convert.py --input video.mp4 --output anime.mp4 --enhance --resolution 1080
"""

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
from pathlib import Path

import cv2
import numpy as np
import torch
from PIL import Image
from torchvision.transforms.functional import to_tensor, to_pil_image
from tqdm import tqdm

STYLES = ["face_paint_512_v2", "celeba_distill", "paprika"]


def load_model(style: str, device: torch.device):
    model = torch.hub.load(
        "bryandlee/animegan2-pytorch:main",
        "generator",
        pretrained=style,
        trust_repo=True,
    )
    model = model.to(device).eval()
    face2paint = torch.hub.load(
        "bryandlee/animegan2-pytorch:main",
        "face2paint",
        size=512,
        trust_repo=True,
    )
    return model, face2paint


def process_frame(frame_bgr: np.ndarray, model, face2paint, device: torch.device) -> np.ndarray:
    rgb = cv2.cvtColor(frame_bgr, cv2.COLOR_BGR2RGB)
    pil_img = Image.fromarray(rgb)

    with torch.no_grad():
        output = face2paint(model, pil_img, side_by_side=False)

    result = np.array(output)
    return cv2.cvtColor(result, cv2.COLOR_RGB2BGR)


def enhance_frame(frame: np.ndarray) -> np.ndarray:
    lab = cv2.cvtColor(frame, cv2.COLOR_BGR2LAB)
    l, a, b = cv2.split(lab)
    clahe = cv2.createCLAHE(clipLimit=2.0, tileGridSize=(8, 8))
    l = clahe.apply(l)
    enhanced = cv2.merge([l, a, b])
    enhanced = cv2.cvtColor(enhanced, cv2.COLOR_LAB2BGR)

    hsv = cv2.cvtColor(enhanced, cv2.COLOR_BGR2HSV).astype(np.float32)
    hsv[:, :, 1] = np.clip(hsv[:, :, 1] * 1.15, 0, 255)
    enhanced = cv2.cvtColor(hsv.astype(np.uint8), cv2.COLOR_HSV2BGR)

    return enhanced


def add_edge_lines(frame: np.ndarray, strength: float = 0.3) -> np.ndarray:
    gray = cv2.cvtColor(frame, cv2.COLOR_BGR2GRAY)
    edges = cv2.Canny(gray, 50, 150)
    edges = cv2.dilate(edges, np.ones((2, 2), np.uint8), iterations=1)

    edge_overlay = frame.copy()
    edge_overlay[edges > 0] = (
        frame[edges > 0] * (1 - strength) + np.array([0, 0, 0]) * strength
    ).astype(np.uint8)
    return edge_overlay


def get_video_info(path: str) -> dict:
    cap = cv2.VideoCapture(path)
    info = {
        "fps": cap.get(cv2.CAP_PROP_FPS),
        "width": int(cap.get(cv2.CAP_PROP_FRAME_WIDTH)),
        "height": int(cap.get(cv2.CAP_PROP_FRAME_HEIGHT)),
        "total_frames": int(cap.get(cv2.CAP_PROP_FRAME_COUNT)),
    }
    cap.release()
    return info


def extract_audio(input_video: str, audio_path: str) -> bool:
    try:
        subprocess.run(
            ["ffmpeg", "-y", "-i", input_video, "-vn", "-acodec", "aac", "-b:a", "192k", audio_path],
            capture_output=True,
            check=True,
        )
        return True
    except (subprocess.CalledProcessError, FileNotFoundError):
        return False


def mux_video_audio(video_path: str, audio_path: str, output_path: str):
    subprocess.run(
        [
            "ffmpeg", "-y",
            "-i", video_path,
            "-i", audio_path,
            "-c:v", "copy",
            "-c:a", "aac",
            "-shortest",
            output_path,
        ],
        capture_output=True,
        check=True,
    )


def reencode_mp4(input_path: str, output_path: str):
    subprocess.run(
        [
            "ffmpeg", "-y",
            "-i", input_path,
            "-c:v", "libx264",
            "-crf", "20",
            "-preset", "medium",
            "-pix_fmt", "yuv420p",
            "-movflags", "+faststart",
            output_path,
        ],
        capture_output=True,
        check=True,
    )


def convert_video(
    input_path: str,
    output_path: str,
    style: str = "face_paint_512_v2",
    enhance: bool = False,
    edge_lines: bool = False,
    edge_strength: float = 0.3,
    target_resolution: int | None = None,
    batch_size: int = 1,
    skip_audio: bool = False,
):
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Device: {device}")
    print(f"Style:  {style}")
    print(f"Loading AnimeGANv2 model...")
    model, face2paint = load_model(style, device)

    info = get_video_info(input_path)
    print(f"Input:  {info['width']}x{info['height']} @ {info['fps']:.1f}fps, {info['total_frames']} frames")

    cap = cv2.VideoCapture(input_path)
    out_w, out_h = info["width"], info["height"]

    if target_resolution:
        scale = target_resolution / max(out_w, out_h)
        out_w = int(out_w * scale) // 2 * 2
        out_h = int(out_h * scale) // 2 * 2
        print(f"Output resolution: {out_w}x{out_h}")

    tmpdir = tempfile.mkdtemp(prefix="anime_pipeline_")
    raw_video = os.path.join(tmpdir, "raw_output.mp4")

    fourcc = cv2.VideoWriter_fourcc(*"mp4v")
    writer = cv2.VideoWriter(raw_video, fourcc, info["fps"], (out_w, out_h))

    print("Converting frames...")
    for i in tqdm(range(info["total_frames"]), desc="Anime conversion"):
        ret, frame = cap.read()
        if not ret:
            break

        anime_frame = process_frame(frame, model, face2paint, device)
        anime_frame = cv2.resize(anime_frame, (out_w, out_h), interpolation=cv2.INTER_LANCZOS4)

        if enhance:
            anime_frame = enhance_frame(anime_frame)
        if edge_lines:
            anime_frame = add_edge_lines(anime_frame, edge_strength)

        writer.write(anime_frame)

    cap.release()
    writer.release()

    has_ffmpeg = shutil.which("ffmpeg") is not None
    if not has_ffmpeg:
        print("Warning: ffmpeg not found — output may have limited compatibility.")
        shutil.move(raw_video, output_path)
        shutil.rmtree(tmpdir, ignore_errors=True)
        return

    encoded_video = os.path.join(tmpdir, "encoded.mp4")
    print("Encoding with H.264...")
    reencode_mp4(raw_video, encoded_video)

    if not skip_audio:
        audio_path = os.path.join(tmpdir, "audio.aac")
        has_audio = extract_audio(input_path, audio_path)
        if has_audio:
            print("Muxing audio...")
            mux_video_audio(encoded_video, audio_path, output_path)
        else:
            print("No audio track found, skipping audio mux.")
            shutil.move(encoded_video, output_path)
    else:
        shutil.move(encoded_video, output_path)

    shutil.rmtree(tmpdir, ignore_errors=True)
    print(f"Done! Output saved to: {output_path}")


def main():
    parser = argparse.ArgumentParser(
        description="Convert video to anime style using AnimeGANv2",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  %(prog)s --input goal.mp4 --output anime_goal.mp4
  %(prog)s --input save.mp4 --output save_anime.mp4 --style paprika --enhance
  %(prog)s --input highlights.mp4 --output highlights_anime.mp4 --edge-lines --resolution 1080
        """,
    )
    parser.add_argument("-i", "--input", required=True, help="Input video file path")
    parser.add_argument("-o", "--output", required=True, help="Output video file path")
    parser.add_argument(
        "-s", "--style",
        choices=STYLES,
        default="face_paint_512_v2",
        help="Anime style (default: face_paint_512_v2)",
    )
    parser.add_argument("--enhance", action="store_true", help="Apply color/contrast enhancement")
    parser.add_argument("--edge-lines", action="store_true", help="Add bold anime-style edge lines")
    parser.add_argument("--edge-strength", type=float, default=0.3, help="Edge line darkness (0-1, default: 0.3)")
    parser.add_argument("--resolution", type=int, default=None, help="Target max resolution (e.g. 720, 1080)")
    parser.add_argument("--no-audio", action="store_true", help="Skip audio track")

    args = parser.parse_args()

    if not os.path.isfile(args.input):
        print(f"Error: Input file not found: {args.input}", file=sys.stderr)
        sys.exit(1)

    os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)

    convert_video(
        input_path=args.input,
        output_path=args.output,
        style=args.style,
        enhance=args.enhance,
        edge_lines=args.edge_lines,
        edge_strength=args.edge_strength,
        target_resolution=args.resolution,
        skip_audio=args.no_audio,
    )


if __name__ == "__main__":
    main()
