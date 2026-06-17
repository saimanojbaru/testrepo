"""AI script generation using Anthropic Claude API."""

import os
import json
import logging
from pathlib import Path

from dotenv import load_dotenv
import anthropic
from tenacity import retry, stop_after_attempt, wait_exponential

load_dotenv()
logger = logging.getLogger(__name__)

SCRIPT_SYSTEM_PROMPT = """You are a professional YouTube scriptwriter. You write engaging,
hook-driven scripts optimized for viewer retention. Your scripts are conversational but
authoritative — like explaining to a smart friend.

Rules:
- First 5 seconds MUST grab attention (bold claim, surprising stat, or provocative question)
- No filler phrases: "without further ado", "let's dive in", "hey guys"
- Use concrete examples over abstract claims
- Write for spoken delivery — short sentences, natural rhythm
- End with a clear CTA (subscribe, comment, next video tease)
- Target Flesch-Kincaid grade level 6-8 for accessibility

You MUST return valid JSON only, no markdown code fences."""

SCRIPT_USER_PROMPT = """Write a complete YouTube video script about: {topic}

Niche/Channel context: {niche}
Target audience: {audience}
Target duration: {duration} seconds (approximately {word_count} words at 150 wpm)

Return a JSON object with this exact structure:
{{
  "title": "Compelling, click-worthy title (under 60 chars)",
  "description": "Full YouTube description with timestamps and links (200-300 words)",
  "tags": ["tag1", "tag2", "tag3", "tag4", "tag5"],
  "thumbnail_text": "2-4 word hook for thumbnail",
  "sections": [
    {{
      "name": "hook",
      "timestamp": "0:00",
      "duration_seconds": 15,
      "narration": "The exact words to be spoken...",
      "visual_notes": "B-roll description for this section"
    }},
    {{
      "name": "intro",
      "timestamp": "0:15",
      "duration_seconds": 30,
      "narration": "...",
      "visual_notes": "..."
    }}
  ],
  "total_duration_seconds": {duration}
}}

Include these sections at minimum: hook, intro, 2-4 main content sections, recap, cta.
Make the narration natural and conversational. Each section's narration should flow into the next."""


class ScriptGenerator:
    def __init__(self, config: dict | None = None):
        self.api_key = os.getenv("ANTHROPIC_API_KEY")
        if not self.api_key:
            raise ValueError("ANTHROPIC_API_KEY not set in environment")

        self.client = anthropic.Anthropic(api_key=self.api_key)
        self.config = config or {}

        script_config = self.config.get("script", {})
        self.model = script_config.get("model", "claude-sonnet-4-6")
        self.max_tokens = script_config.get("max_tokens", 4096)
        self.temperature = script_config.get("temperature", 0.7)

        channel_config = self.config.get("channel", {})
        self.niche = channel_config.get("niche", "general")
        self.audience = channel_config.get("target_audience", "general audience")

    @retry(stop=stop_after_attempt(3), wait=wait_exponential(multiplier=1, max=10))
    def generate(self, topic: str, duration_seconds: int = 600) -> dict:
        """Generate a complete video script for the given topic.

        Returns parsed JSON with title, description, tags, sections, etc.
        """
        word_count = int(duration_seconds * 150 / 60)

        prompt = SCRIPT_USER_PROMPT.format(
            topic=topic,
            niche=self.niche,
            audience=self.audience,
            duration=duration_seconds,
            word_count=word_count,
        )

        logger.info("Generating script for topic: '%s' (target: %ds)", topic, duration_seconds)

        response = self.client.messages.create(
            model=self.model,
            max_tokens=self.max_tokens,
            temperature=self.temperature,
            system=SCRIPT_SYSTEM_PROMPT,
            messages=[{"role": "user", "content": prompt}],
        )

        raw_text = response.content[0].text
        script_data = self._parse_response(raw_text)

        logger.info(
            "Script generated: '%s' (%d sections, ~%ds)",
            script_data.get("title", "Untitled"),
            len(script_data.get("sections", [])),
            script_data.get("total_duration_seconds", 0),
        )

        return script_data

    def _parse_response(self, text: str) -> dict:
        """Parse Claude's response, handling potential JSON formatting issues."""
        cleaned = text.strip()
        if cleaned.startswith("```"):
            lines = cleaned.split("\n")
            lines = lines[1:]  # remove opening fence
            if lines and lines[-1].strip() == "```":
                lines = lines[:-1]
            cleaned = "\n".join(lines)

        try:
            return json.loads(cleaned)
        except json.JSONDecodeError as e:
            logger.error("Failed to parse script JSON: %s", e)
            logger.debug("Raw response: %s", text[:500])
            raise ValueError(f"Claude returned invalid JSON: {e}") from e

    def evaluate_script(self, script_data: dict) -> dict:
        """Use Claude to evaluate a generated script for quality."""
        prompt = f"""Evaluate this YouTube script JSON for quality. Score each category 1-10
and provide brief feedback. Return JSON only.

Script: {json.dumps(script_data, indent=2)[:3000]}

Return:
{{
  "scores": {{
    "hook_strength": 0,
    "content_depth": 0,
    "flow_and_pacing": 0,
    "cta_effectiveness": 0,
    "overall": 0
  }},
  "feedback": "Brief constructive feedback",
  "passes_quality_gate": true/false
}}"""

        response = self.client.messages.create(
            model=self.model,
            max_tokens=1024,
            temperature=0.3,
            system="You are a YouTube content strategist. Evaluate scripts critically but constructively. Return valid JSON only.",
            messages=[{"role": "user", "content": prompt}],
        )

        return self._parse_response(response.content[0].text)

    def save_script(self, script_data: dict, output_path: str) -> str:
        """Save generated script to a JSON file."""
        path = Path(output_path)
        path.parent.mkdir(parents=True, exist_ok=True)

        with open(path, "w") as f:
            json.dump(script_data, f, indent=2)

        logger.info("Script saved to %s", output_path)
        return str(path)

    def get_full_narration(self, script_data: dict) -> str:
        """Extract the full narration text from all sections, joined."""
        sections = script_data.get("sections", [])
        narrations = [s["narration"] for s in sections if s.get("narration")]
        return "\n\n".join(narrations)
