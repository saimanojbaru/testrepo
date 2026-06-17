"""YouTube upload preparation and metadata generation."""

import json
import logging
from pathlib import Path

from dotenv import load_dotenv

load_dotenv()
logger = logging.getLogger(__name__)


class UploadPrep:
    def __init__(self, config: dict | None = None):
        self.config = config or {}
        upload_config = self.config.get("upload", {})
        self.privacy_status = upload_config.get("privacy_status", "private")
        self.category_id = upload_config.get("category_id", "28")
        self.default_language = upload_config.get("default_language", "en")

    def generate_metadata(self, script_data: dict, video_path: str, audio_path: str) -> dict:
        """Generate YouTube-ready metadata from the script data."""
        sections = script_data.get("sections", [])
        timestamps_desc = "\n".join(
            f"{s['timestamp']} - {s['name'].replace('_', ' ').title()}"
            for s in sections
            if s.get("timestamp")
        )

        description = script_data.get("description", "")
        if timestamps_desc and "0:00" not in description:
            description += f"\n\nTimestamps:\n{timestamps_desc}"

        metadata = {
            "snippet": {
                "title": script_data.get("title", "Untitled Video"),
                "description": description,
                "tags": script_data.get("tags", []),
                "categoryId": self.category_id,
                "defaultLanguage": self.default_language,
            },
            "status": {
                "privacyStatus": self.privacy_status,
                "selfDeclaredMadeForKids": False,
            },
            "files": {
                "video": video_path,
                "audio": audio_path,
            },
            "thumbnail_text": script_data.get("thumbnail_text", ""),
        }

        logger.info("Generated metadata for: '%s'", metadata["snippet"]["title"])
        return metadata

    def save_metadata(self, metadata: dict, output_path: str) -> str:
        """Save metadata to a JSON file for later upload."""
        path = Path(output_path)
        path.parent.mkdir(parents=True, exist_ok=True)

        with open(path, "w") as f:
            json.dump(metadata, f, indent=2)

        logger.info("Metadata saved to %s", output_path)
        return str(path)

    def upload(self, metadata_path: str) -> dict:
        """Upload video to YouTube using the YouTube Data API.

        This is a stub — full implementation requires OAuth2 flow.
        See: https://developers.google.com/youtube/v3/guides/uploading_a_video
        """
        with open(metadata_path) as f:
            metadata = json.load(f)

        video_path = metadata.get("files", {}).get("video")
        if not video_path or not Path(video_path).exists():
            raise FileNotFoundError(f"Video file not found: {video_path}")

        logger.info(
            "YouTube upload stub — video ready at: %s", video_path
        )
        logger.info(
            "To upload, configure OAuth2 credentials and run:\n"
            "  python -m scripts.upload --metadata %s", metadata_path
        )

        return {
            "status": "ready",
            "video_path": video_path,
            "title": metadata["snippet"]["title"],
            "privacy": metadata["status"]["privacyStatus"],
            "message": "Video is ready for upload. Configure YouTube OAuth2 to enable automatic uploading.",
        }

    def validate_metadata(self, metadata: dict) -> list[str]:
        """Validate metadata before upload. Returns list of issues (empty = valid)."""
        issues = []
        snippet = metadata.get("snippet", {})

        title = snippet.get("title", "")
        if not title:
            issues.append("Missing title")
        elif len(title) > 100:
            issues.append(f"Title too long ({len(title)} chars, max 100)")

        description = snippet.get("description", "")
        if not description:
            issues.append("Missing description")
        elif len(description) > 5000:
            issues.append(f"Description too long ({len(description)} chars, max 5000)")

        tags = snippet.get("tags", [])
        if not tags:
            issues.append("No tags provided")
        total_tag_chars = sum(len(t) for t in tags)
        if total_tag_chars > 500:
            issues.append(f"Total tag characters too long ({total_tag_chars}, max 500)")

        if issues:
            logger.warning("Metadata validation issues: %s", issues)
        else:
            logger.info("Metadata validation passed")

        return issues
