#!/usr/bin/env python3
"""
Anime Video Conversion Pipeline (AnimeGANv3 / ONNX)

Higher-quality successor to anime_convert.py. Uses AnimeGANv3 ONNX scene
models which process every frame at FULL native resolution (only rounding
to a multiple of 8) — no square crop, no downscale-then-upscale blur.

Styles (scene models, great for sports / action footage):
  shinkai  - Makoto Shinkai look: vivid, saturated skies & grass (default)
  hayao    - Studio Ghibli / Miyazaki look: softer, warmer, painterly
  shinkai40, h40, h64 - alternate checkpoints

Usage:
  python anime_convert_v3.py -i clip.mp4 -o anime.mp4
  python anime_convert_v3.py -i clip.mp4 -o anime.mp4 --model hayao
  python anime_convert_v3.py -i clip.mp4 -o anime.mp4 --max-size 1080
"""

import argparse
import os
import shutil
import subprocess
import sys
import tempfile
import urllib.request

import cv2
import numpy as np
import onnxruntime as ort
from tqdm import tqdm

MODEL_URLS = {
    "hayao": "https://github.com/TachibanaYoshino/AnimeGANv3/releases/download/v1.1.0/AnimeGANv3_Hayao_36.onnx",
    "shinkai": "https://github.com/TachibanaYoshino/AnimeGANv3/releases/download/v1.1.0/AnimeGANv3_Shinkai_37.onnx",
    "shinkai40": "https://github.com/TachibanaYoshino/AnimeGANv3/releases/download/v1.0.3/AnimeGANv3_Shinkai_40.onnx",
    "h40": "https://github.com/TachibanaYoshino/AnimeGANv3/releases/download/v1.0.3/animeganv3_H40_model.onnx",
    "h64": "https://github.com/TachibanaYoshino/AnimeGANv3/releases/download/v1.0.3/animeganv3_H64_model0.onnx",
}

MODEL_DIR = os.environ.get(
    "ANIMEGAN_V3_DIR", os.path.join(os.path.dirname(os.path.abspath(__file__)), "models")
)


def resolve_model(model: str) -> str:
    """Return a local path to the model, downloading a known name if needed."""
    if os.path.isfile(model):
        return model
    if model not in MODEL_URLS:
        raise ValueError(f"Unknown model '{model}'. Choices: {list(MODEL_URLS)} or a path to a .onnx file.")

    os.makedirs(MODEL_DIR, exist_ok=True)
    local = os.path.join(MODEL_DIR, f"AnimeGANv3_{model}.onnx")
    if not os.path.isfile(local):
        print(f"Downloading {model} model...")
        urllib.request.urlretrieve(MODEL_URLS[model], local)
    return local


def make_session(model_path: str, device: str) -> ort.InferenceSession:
    providers = ["CPUExecutionProvider"]
    if device == "gpu" and "CUDAExecutionProvider" in ort.get_available_providers():
        providers = ["CUDAExecutionProvider", "CPUExecutionProvider"]
    return ort.InferenceSession(model_path, providers=providers)


def _to_8s(x: int) -> int:
    return 256 if x < 256 else x - x % 8


def process_frame(frame_bgr: np.ndarray, session, input_name: str, proc_w: int, proc_h: int) -> np.ndarray:
    """Stylize a single BGR frame, returned at the processing resolution (proc_w x proc_h)."""
    img = cv2.resize(frame_bgr, (proc_w, proc_h))
    img = cv2.cvtColor(img, cv2.COLOR_BGR2RGB).astype(np.float32) / 127.5 - 1.0
    img = np.expand_dims(img, axis=0)

    out = session.run(None, {input_name: img})[0]
    out = (np.squeeze(out) + 1.0) / 2.0 * 255.0
    out = np.clip(out, 0, 255).astype(np.uint8)
    return cv2.cvtColor(out, cv2.COLOR_RGB2BGR)


def get_video_info(path: str) -> dict:
    cap = cv2.VideoCapture(path)
    info = {
        "fps": cap.get(cv2.CAP_PROP_FPS) or 30.0,
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
            capture_output=True, check=True,
        )
        return os.path.getsize(audio_path) > 0
    except (subprocess.CalledProcessError, FileNotFoundError):
        return False


def convert_video(input_path, output_path, model="shinkai", device="cpu",
                  max_size=None, skip_audio=False):
    model_path = resolve_model(model)
    session = make_session(model_path, device)
    input_name = session.get_inputs()[0].name

    info = get_video_info(input_path)
    w, h = info["width"], info["height"]
    print(f"Input:  {w}x{h} @ {info['fps']:.1f}fps, {info['total_frames']} frames")
    print(f"Model:  {os.path.basename(model_path)}  |  providers: {session.get_providers()}")

    # Processing resolution: full native (rounded to /8), optionally capped.
    proc_w, proc_h = w, h
    if max_size and max(w, h) > max_size:
        scale = max_size / max(w, h)
        proc_w, proc_h = int(w * scale), int(h * scale)
    proc_w, proc_h = _to_8s(proc_w), _to_8s(proc_h)
    print(f"Processing resolution: {proc_w}x{proc_h}")

    tmpdir = tempfile.mkdtemp(prefix="anime_v3_")
    raw_video = os.path.join(tmpdir, "raw.mp4")
    writer = cv2.VideoWriter(raw_video, cv2.VideoWriter_fourcc(*"mp4v"), info["fps"], (proc_w, proc_h))

    cap = cv2.VideoCapture(input_path)
    for _ in tqdm(range(info["total_frames"]), desc=f"AnimeGANv3 ({model})"):
        ret, frame = cap.read()
        if not ret:
            break
        writer.write(process_frame(frame, session, input_name, proc_w, proc_h))
    cap.release()
    writer.release()

    if shutil.which("ffmpeg") is None:
        print("Warning: ffmpeg not found — moving raw output (no audio, limited compatibility).")
        shutil.move(raw_video, output_path)
        shutil.rmtree(tmpdir, ignore_errors=True)
        return

    encoded = os.path.join(tmpdir, "encoded.mp4")
    print("Encoding H.264...")
    subprocess.run(
        ["ffmpeg", "-y", "-i", raw_video, "-c:v", "libx264", "-crf", "18",
         "-preset", "slow", "-pix_fmt", "yuv420p", "-movflags", "+faststart", encoded],
        capture_output=True, check=True,
    )

    if skip_audio:
        shutil.move(encoded, output_path)
    else:
        audio = os.path.join(tmpdir, "audio.aac")
        if extract_audio(input_path, audio):
            print("Muxing audio...")
            subprocess.run(
                ["ffmpeg", "-y", "-i", encoded, "-i", audio, "-c:v", "copy",
                 "-c:a", "aac", "-shortest", output_path],
                capture_output=True, check=True,
            )
        else:
            print("No audio track found.")
            shutil.move(encoded, output_path)

    shutil.rmtree(tmpdir, ignore_errors=True)
    print(f"Done! Output saved to: {output_path}")


def main():
    p = argparse.ArgumentParser(
        description="Convert video to anime using AnimeGANv3 (ONNX, full-resolution)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    p.add_argument("-i", "--input", required=True, help="Input video file")
    p.add_argument("-o", "--output", required=True, help="Output video file")
    p.add_argument("-m", "--model", default="shinkai",
                   help="Model name (shinkai, hayao, shinkai40, h40, h64) or path to .onnx")
    p.add_argument("-d", "--device", default="cpu", choices=["cpu", "gpu"])
    p.add_argument("--max-size", type=int, default=None,
                   help="Cap the long edge (e.g. 1080) for faster processing")
    p.add_argument("--no-audio", action="store_true")
    args = p.parse_args()

    if not os.path.isfile(args.input):
        print(f"Error: input not found: {args.input}", file=sys.stderr)
        sys.exit(1)
    os.makedirs(os.path.dirname(os.path.abspath(args.output)), exist_ok=True)

    convert_video(args.input, args.output, model=args.model, device=args.device,
                  max_size=args.max_size, skip_audio=args.no_audio)


if __name__ == "__main__":
    main()
