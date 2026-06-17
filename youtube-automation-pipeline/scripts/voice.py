"""ElevenLabs voice synthesis integration."""

import os
import json
import logging
from pathlib import Path

from dotenv import load_dotenv
from elevenlabs import ElevenLabs
from tenacity import retry, stop_after_attempt, wait_exponential

load_dotenv()
logger = logging.getLogger(__name__)

DEFAULT_VOICE_ID = "21m00Tcm4TlvDq8ikWAM"  # Rachel
DEFAULT_MODEL_ID = "eleven_multilingual_v2"


class VoiceSynthesizer:
    def __init__(self, config: dict | None = None):
        self.api_key = os.getenv("ELEVENLABS_API_KEY")
        if not self.api_key:
            raise ValueError("ELEVENLABS_API_KEY not set in environment")

        self.client = ElevenLabs(api_key=self.api_key)
        self.config = config or {}

        voice_config = self.config.get("voice", {})
        self.voice_id = (
            os.getenv("ELEVENLABS_VOICE_ID")
            or voice_config.get("voice_id")
            or DEFAULT_VOICE_ID
        )
        self.model_id = voice_config.get("model_id", DEFAULT_MODEL_ID)
        self.stability = voice_config.get("stability", 0.5)
        self.similarity_boost = voice_config.get("similarity_boost", 0.75)
        self.style = voice_config.get("style", 0.0)
        self.speed = voice_config.get("speed", 1.0)

    def list_voices(self) -> list[dict]:
        """List available ElevenLabs voices."""
        response = self.client.voices.get_all()
        voices = []
        for voice in response.voices:
            voices.append({
                "voice_id": voice.voice_id,
                "name": voice.name,
                "category": voice.category,
                "labels": voice.labels,
            })
        logger.info("Found %d available voices", len(voices))
        return voices

    def set_voice(self, voice_id: str):
        """Switch to a different voice."""
        self.voice_id = voice_id
        logger.info("Voice set to: %s", voice_id)

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, max=10))
    def synthesize(self, text: str, output_path: str) -> str:
        """Convert text to speech and save to file.

        Returns the path to the saved audio file.
        """
        logger.info("Synthesizing %d characters of text...", len(text))

        audio_generator = self.client.text_to_speech.convert(
            voice_id=self.voice_id,
            text=text,
            model_id=self.model_id,
            voice_settings={
                "stability": self.stability,
                "similarity_boost": self.similarity_boost,
                "style": self.style,
                "use_speaker_boost": True,
            },
        )

        output = Path(output_path)
        output.parent.mkdir(parents=True, exist_ok=True)

        with open(output, "wb") as f:
            for chunk in audio_generator:
                f.write(chunk)

        file_size = output.stat().st_size
        logger.info("Audio saved to %s (%d bytes)", output_path, file_size)
        return str(output)

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, max=10))
    def synthesize_with_timestamps(
        self, text: str, output_audio_path: str, output_timestamps_path: str
    ) -> dict:
        """Convert text to speech with word-level timestamps for subtitle generation.

        Returns dict with audio_path, timestamps_path, and duration.
        """
        logger.info("Synthesizing with timestamps: %d characters", len(text))

        response = self.client.text_to_speech.convert_with_timestamps(
            voice_id=self.voice_id,
            text=text,
            model_id=self.model_id,
            voice_settings={
                "stability": self.stability,
                "similarity_boost": self.similarity_boost,
                "style": self.style,
                "use_speaker_boost": True,
            },
        )

        audio_path = Path(output_audio_path)
        audio_path.parent.mkdir(parents=True, exist_ok=True)
        timestamps_path = Path(output_timestamps_path)

        audio_bytes = b""
        all_alignment = {"characters": [], "character_start_times_seconds": [], "character_end_times_seconds": []}

        for chunk in response:
            if chunk.get("audio_base64"):
                import base64
                audio_bytes += base64.b64decode(chunk["audio_base64"])
            if chunk.get("alignment"):
                alignment = chunk["alignment"]
                all_alignment["characters"].extend(alignment.get("characters", []))
                all_alignment["character_start_times_seconds"].extend(
                    alignment.get("character_start_times_seconds", [])
                )
                all_alignment["character_end_times_seconds"].extend(
                    alignment.get("character_end_times_seconds", [])
                )

        with open(audio_path, "wb") as f:
            f.write(audio_bytes)

        word_timestamps = self._characters_to_words(all_alignment)

        timestamp_data = {
            "words": word_timestamps,
            "total_characters": len(all_alignment["characters"]),
            "audio_file": str(audio_path),
        }
        with open(timestamps_path, "w") as f:
            json.dump(timestamp_data, f, indent=2)

        logger.info(
            "Audio: %s (%d bytes), Timestamps: %s (%d words)",
            audio_path, len(audio_bytes), timestamps_path, len(word_timestamps),
        )

        return {
            "audio_path": str(audio_path),
            "timestamps_path": str(timestamps_path),
            "word_count": len(word_timestamps),
        }

    def _characters_to_words(self, alignment: dict) -> list[dict]:
        """Convert character-level timestamps to word-level timestamps."""
        characters = alignment["characters"]
        starts = alignment["character_start_times_seconds"]
        ends = alignment["character_end_times_seconds"]

        words = []
        current_word = ""
        word_start = None

        for i, char in enumerate(characters):
            if char == " ":
                if current_word:
                    words.append({
                        "word": current_word,
                        "start": word_start,
                        "end": ends[i - 1],
                    })
                    current_word = ""
                    word_start = None
            else:
                if word_start is None:
                    word_start = starts[i]
                current_word += char

        if current_word and word_start is not None:
            words.append({
                "word": current_word,
                "start": word_start,
                "end": ends[-1],
            })

        return words

    def synthesize_sections(
        self, sections: list[dict], output_dir: str
    ) -> list[dict]:
        """Synthesize each script section separately with timestamps.

        Each section dict should have 'name' and 'narration' keys.
        Returns list of result dicts with paths and timing info.
        """
        output = Path(output_dir)
        output.mkdir(parents=True, exist_ok=True)
        results = []

        for i, section in enumerate(sections):
            name = section.get("name", f"section_{i}")
            text = section["narration"]

            logger.info("Synthesizing section '%s' (%d chars)", name, len(text))

            audio_path = str(output / f"{name}.mp3")
            timestamps_path = str(output / f"{name}_timestamps.json")

            result = self.synthesize_with_timestamps(
                text=text,
                output_audio_path=audio_path,
                output_timestamps_path=timestamps_path,
            )
            result["section_name"] = name
            result["section_index"] = i
            results.append(result)

        logger.info("Synthesized %d sections", len(results))
        return results
