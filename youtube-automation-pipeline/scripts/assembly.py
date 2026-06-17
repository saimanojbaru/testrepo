"""Video assembly — combines stock footage, narration audio, and subtitles."""

import json
import logging
import os
import re
import subprocess
from pathlib import Path

from dotenv import load_dotenv
import requests
from tenacity import retry, stop_after_attempt, wait_exponential

load_dotenv()
logger = logging.getLogger(__name__)


class VideoAssembler:
    def __init__(self, config: dict | None = None):
        self.config = config or {}

        video_config = self.config.get("video", {})
        self.resolution = video_config.get("resolution", "1920x1080")
        self.fps = video_config.get("fps", 30)

        subtitle_config = self.config.get("subtitles", {})
        self.subtitle_font = subtitle_config.get("font", "Arial")
        self.subtitle_font_size = subtitle_config.get("font_size", 48)
        self.subtitle_color = subtitle_config.get("color", "white")
        self.subtitle_outline_color = subtitle_config.get("outline_color", "black")
        self.subtitle_outline_width = subtitle_config.get("outline_width", 2)

        footage_config = self.config.get("footage", {})
        self.footage_source = footage_config.get("source", "pexels")
        self.clip_duration = footage_config.get("clip_duration", 6)
        self.pexels_api_key = os.getenv("PEXELS_API_KEY")

    def generate_srt(self, timestamps_path: str, output_srt_path: str, max_chars: int = 42) -> str:
        """Generate SRT subtitle file from word-level timestamps."""
        with open(timestamps_path) as f:
            data = json.load(f)

        words = data.get("words", [])
        if not words:
            logger.warning("No word timestamps found")
            return output_srt_path

        subtitles = []
        current_line = []
        current_chars = 0
        line_start = None

        for word_data in words:
            word = word_data["word"]
            start = word_data["start"]
            end = word_data["end"]

            if line_start is None:
                line_start = start

            if current_chars + len(word) + 1 > max_chars and current_line:
                subtitles.append({
                    "start": line_start,
                    "end": end,
                    "text": " ".join(current_line),
                })
                current_line = [word]
                current_chars = len(word)
                line_start = start
            else:
                current_line.append(word)
                current_chars += len(word) + 1

        if current_line:
            subtitles.append({
                "start": line_start,
                "end": words[-1]["end"],
                "text": " ".join(current_line),
            })

        srt_path = Path(output_srt_path)
        srt_path.parent.mkdir(parents=True, exist_ok=True)

        with open(srt_path, "w") as f:
            for i, sub in enumerate(subtitles, 1):
                start_ts = self._seconds_to_srt_time(sub["start"])
                end_ts = self._seconds_to_srt_time(sub["end"])
                f.write(f"{i}\n{start_ts} --> {end_ts}\n{sub['text']}\n\n")

        logger.info("Generated SRT with %d entries: %s", len(subtitles), output_srt_path)
        return str(srt_path)

    def _seconds_to_srt_time(self, seconds: float) -> str:
        """Convert seconds to SRT timestamp format (HH:MM:SS,mmm)."""
        hours = int(seconds // 3600)
        minutes = int((seconds % 3600) // 60)
        secs = int(seconds % 60)
        millis = int((seconds % 1) * 1000)
        return f"{hours:02d}:{minutes:02d}:{secs:02d},{millis:03d}"

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, max=10))
    def fetch_stock_footage(self, query: str, duration: int, output_dir: str) -> list[str]:
        """Fetch stock footage clips from Pexels API.

        Returns list of paths to downloaded video files.
        """
        if not self.pexels_api_key:
            logger.warning("PEXELS_API_KEY not set, skipping stock footage")
            return []

        num_clips = max(1, duration // self.clip_duration)
        out = Path(output_dir)
        out.mkdir(parents=True, exist_ok=True)

        headers = {"Authorization": self.pexels_api_key}
        resp = requests.get(
            "https://api.pexels.com/videos/search",
            headers=headers,
            params={"query": query, "per_page": num_clips, "orientation": "landscape"},
            timeout=30,
        )
        resp.raise_for_status()
        videos = resp.json().get("videos", [])

        downloaded = []
        for i, video in enumerate(videos[:num_clips]):
            video_files = video.get("video_files", [])
            hd_files = [
                vf for vf in video_files
                if vf.get("width", 0) >= 1280 and vf.get("file_type") == "video/mp4"
            ]
            if not hd_files:
                hd_files = [vf for vf in video_files if vf.get("file_type") == "video/mp4"]
            if not hd_files:
                continue

            download_url = hd_files[0]["link"]
            clip_path = out / f"clip_{i:03d}.mp4"

            logger.info("Downloading clip %d: %s", i, download_url[:80])
            clip_resp = requests.get(download_url, timeout=60)
            clip_resp.raise_for_status()
            with open(clip_path, "wb") as f:
                f.write(clip_resp.content)
            downloaded.append(str(clip_path))

        logger.info("Downloaded %d stock footage clips", len(downloaded))
        return downloaded

    def assemble(
        self,
        audio_path: str,
        srt_path: str,
        footage_clips: list[str],
        output_path: str,
    ) -> str:
        """Assemble final video from audio, subtitles, and footage clips.

        Uses FFmpeg to:
        1. Concatenate footage clips into a looping background
        2. Overlay narration audio
        3. Burn in subtitles
        """
        output = Path(output_path)
        output.parent.mkdir(parents=True, exist_ok=True)
        temp_dir = output.parent / "temp"
        temp_dir.mkdir(exist_ok=True)

        audio_duration = self._get_duration(audio_path)
        logger.info("Audio duration: %.1f seconds", audio_duration)

        if footage_clips:
            bg_video = str(temp_dir / "background.mp4")
            self._concat_clips(footage_clips, bg_video, audio_duration)
        else:
            bg_video = str(temp_dir / "black_bg.mp4")
            self._generate_black_background(bg_video, audio_duration)

        width, height = self.resolution.split("x")
        subtitle_filter = (
            f"subtitles={srt_path}:force_style='"
            f"FontName={self.subtitle_font},"
            f"FontSize={self.subtitle_font_size},"
            f"PrimaryColour=&H00FFFFFF,"
            f"OutlineColour=&H00000000,"
            f"Outline={self.subtitle_outline_width},"
            f"Alignment=2'"
        )

        cmd = [
            "ffmpeg", "-y",
            "-i", bg_video,
            "-i", audio_path,
            "-filter_complex",
            f"[0:v]scale={width}:{height}:force_original_aspect_ratio=decrease,"
            f"pad={width}:{height}:(ow-iw)/2:(oh-ih)/2:black,"
            f"{subtitle_filter}[v]",
            "-map", "[v]",
            "-map", "1:a",
            "-c:v", "libx264",
            "-preset", "medium",
            "-crf", "23",
            "-c:a", "aac",
            "-b:a", "192k",
            "-shortest",
            str(output),
        ]

        logger.info("Running FFmpeg assembly...")
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=600)
        if result.returncode != 0:
            logger.error("FFmpeg failed: %s", result.stderr[-500:] if result.stderr else "unknown")
            raise RuntimeError(f"FFmpeg assembly failed: {result.stderr[-200:]}")

        logger.info("Video assembled: %s (%.1f MB)", output, output.stat().st_size / 1e6)
        return str(output)

    def _get_duration(self, media_path: str) -> float:
        """Get duration of a media file in seconds."""
        cmd = [
            "ffprobe", "-v", "quiet",
            "-show_entries", "format=duration",
            "-of", "default=noprint_wrappers=1:nokey=1",
            media_path,
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=30)
        return float(result.stdout.strip())

    def _concat_clips(self, clips: list[str], output_path: str, target_duration: float):
        """Concatenate video clips, looping if necessary to fill target duration."""
        temp_dir = Path(output_path).parent
        list_file = temp_dir / "concat_list.txt"

        total_duration = 0.0
        clip_list = []
        while total_duration < target_duration:
            for clip in clips:
                clip_list.append(f"file '{clip}'")
                total_duration += self._get_duration(clip)
                if total_duration >= target_duration:
                    break

        with open(list_file, "w") as f:
            f.write("\n".join(clip_list))

        cmd = [
            "ffmpeg", "-y",
            "-f", "concat", "-safe", "0",
            "-i", str(list_file),
            "-t", str(target_duration),
            "-c:v", "libx264", "-preset", "fast",
            "-an",
            output_path,
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=300)
        if result.returncode != 0:
            raise RuntimeError(f"Clip concatenation failed: {result.stderr[-200:]}")

    def _generate_black_background(self, output_path: str, duration: float):
        """Generate a black background video as fallback when no footage is available."""
        width, height = self.resolution.split("x")
        cmd = [
            "ffmpeg", "-y",
            "-f", "lavfi",
            "-i", f"color=c=black:s={width}x{height}:r={self.fps}:d={duration}",
            "-c:v", "libx264", "-preset", "fast",
            output_path,
        ]
        result = subprocess.run(cmd, capture_output=True, text=True, timeout=120)
        if result.returncode != 0:
            raise RuntimeError(f"Background generation failed: {result.stderr[-200:]}")
