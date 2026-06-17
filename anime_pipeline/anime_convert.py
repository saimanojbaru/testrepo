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
from torch import nn
import torch.nn.functional as F
from PIL import Image
from torchvision.transforms.functional import to_tensor, to_pil_image
from tqdm import tqdm

STYLES = ["face_paint_512_v2", "celeba_distill", "paprika"]

ANIMEGAN_REPO = os.environ.get("ANIMEGAN_REPO", "/tmp/animegan2-pytorch")


class ConvNormLReLU(nn.Sequential):
    def __init__(self, in_ch, out_ch, kernel_size=3, stride=1, padding=1, pad_mode="reflect", groups=1, bias=False):
        pad_layer = {"zero": nn.ZeroPad2d, "same": nn.ReplicationPad2d, "reflect": nn.ReflectionPad2d}
        super().__init__(
            pad_layer[pad_mode](padding),
            nn.Conv2d(in_ch, out_ch, kernel_size=kernel_size, stride=stride, padding=0, groups=groups, bias=bias),
            nn.GroupNorm(num_groups=1, num_channels=out_ch, affine=True),
            nn.LeakyReLU(0.2, inplace=True),
        )


class InvertedResBlock(nn.Module):
    def __init__(self, in_ch, out_ch, expansion_ratio=2):
        super().__init__()
        self.use_res_connect = in_ch == out_ch
        bottleneck = int(round(in_ch * expansion_ratio))
        layers = []
        if expansion_ratio != 1:
            layers.append(ConvNormLReLU(in_ch, bottleneck, kernel_size=1, padding=0))
        layers.append(ConvNormLReLU(bottleneck, bottleneck, groups=bottleneck, bias=True))
        layers.append(nn.Conv2d(bottleneck, out_ch, kernel_size=1, padding=0, bias=False))
        layers.append(nn.GroupNorm(num_groups=1, num_channels=out_ch, affine=True))
        self.layers = nn.Sequential(*layers)

    def forward(self, input):
        out = self.layers(input)
        if self.use_res_connect:
            out = input + out
        return out


class Generator(nn.Module):
    def __init__(self):
        super().__init__()
        self.block_a = nn.Sequential(
            ConvNormLReLU(3, 32, kernel_size=7, padding=3),
            ConvNormLReLU(32, 64, stride=2, padding=(0, 1, 0, 1)),
            ConvNormLReLU(64, 64),
        )
        self.block_b = nn.Sequential(
            ConvNormLReLU(64, 128, stride=2, padding=(0, 1, 0, 1)),
            ConvNormLReLU(128, 128),
        )
        self.block_c = nn.Sequential(
            ConvNormLReLU(128, 128),
            InvertedResBlock(128, 256, 2),
            InvertedResBlock(256, 256, 2),
            InvertedResBlock(256, 256, 2),
            InvertedResBlock(256, 256, 2),
            ConvNormLReLU(256, 128),
        )
        self.block_d = nn.Sequential(ConvNormLReLU(128, 128), ConvNormLReLU(128, 128))
        self.block_e = nn.Sequential(
            ConvNormLReLU(128, 64),
            ConvNormLReLU(64, 64),
            ConvNormLReLU(64, 32, kernel_size=7, padding=3),
        )
        self.out_layer = nn.Sequential(nn.Conv2d(32, 3, kernel_size=1, stride=1, padding=0, bias=False), nn.Tanh())

    def forward(self, input, align_corners=True):
        out = self.block_a(input)
        half_size = out.size()[-2:]
        out = self.block_b(out)
        out = self.block_c(out)
        if align_corners:
            out = F.interpolate(out, half_size, mode="bilinear", align_corners=True)
        else:
            out = F.interpolate(out, scale_factor=2, mode="bilinear", align_corners=False)
        out = self.block_d(out)
        if align_corners:
            out = F.interpolate(out, input.size()[-2:], mode="bilinear", align_corners=True)
        else:
            out = F.interpolate(out, scale_factor=2, mode="bilinear", align_corners=False)
        out = self.block_e(out)
        return self.out_layer(out)


def load_model(style: str, device: torch.device):
    weights_path = os.path.join(ANIMEGAN_REPO, "weights", f"{style}.pt")
    if not os.path.isfile(weights_path):
        raise FileNotFoundError(
            f"Model weights not found at {weights_path}. "
            f"Clone the repo: git clone https://github.com/bryandlee/animegan2-pytorch.git {ANIMEGAN_REPO}"
        )

    model = Generator().to(device)
    model.load_state_dict(torch.load(weights_path, map_location=device, weights_only=True))
    model.eval()

    def face2paint_fn(
        model: torch.nn.Module,
        img: Image.Image,
        size: int = 512,
        side_by_side: bool = False,
        device=device,
    ) -> Image.Image:
        w, h = img.size
        s = min(w, h)
        img = img.crop(((w - s) // 2, (h - s) // 2, (w + s) // 2, (h + s) // 2))
        img = img.resize((size, size), Image.LANCZOS)
        with torch.no_grad():
            input_tensor = to_tensor(img).unsqueeze(0) * 2 - 1
            output = model(input_tensor.to(device)).cpu()[0]
            if side_by_side:
                output = torch.cat([input_tensor[0], output], dim=2)
            output = (output * 0.5 + 0.5).clip(0, 1)
        return to_pil_image(output)

    return model, face2paint_fn


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
