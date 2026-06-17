"""YouTube Automation Pipeline — Main Orchestrator.

Usage:
    python main.py --topic "Your Topic Here"
    python main.py --topic "Your Topic Here" --dry-run
    python main.py --topic "Your Topic Here" --stage voice
    python main.py --topic "Your Topic Here" --config config.yaml
"""

import argparse
import json
import logging
import re
import sys
from datetime import datetime
from pathlib import Path

import yaml
from dotenv import load_dotenv
from rich.console import Console
from rich.panel import Panel
from rich.progress import Progress, SpinnerColumn, TextColumn

load_dotenv()
console = Console()

STAGES = ["discovery", "script", "voice", "assembly", "upload"]


def setup_logging(level: str = "INFO", topic_slug: str = "pipeline"):
    log_dir = Path("logs")
    log_dir.mkdir(exist_ok=True)
    timestamp = datetime.now().strftime("%Y%m%d_%H%M%S")
    log_file = log_dir / f"pipeline_{topic_slug}_{timestamp}.log"

    logging.basicConfig(
        level=getattr(logging, level.upper(), logging.INFO),
        format="%(asctime)s [%(levelname)s] %(name)s: %(message)s",
        handlers=[
            logging.FileHandler(log_file),
            logging.StreamHandler(sys.stdout),
        ],
    )
    return logging.getLogger(__name__)


def load_config(config_path: str = "config.yaml") -> dict:
    path = Path(config_path)
    if path.exists():
        with open(path) as f:
            return yaml.safe_load(f)
    return {}


def slugify(text: str) -> str:
    slug = text.lower().strip()
    slug = re.sub(r"[^\w\s-]", "", slug)
    slug = re.sub(r"[\s_]+", "-", slug)
    return slug[:60]


def run_pipeline(topic: str, config: dict, dry_run: bool = False, start_stage: str | None = None):
    topic_slug = slugify(topic)
    logger = setup_logging(
        level=config.get("pipeline", {}).get("log_level", "INFO"),
        topic_slug=topic_slug,
    )

    output_dir = Path(config.get("pipeline", {}).get("output_dir", "output")) / topic_slug
    output_dir.mkdir(parents=True, exist_ok=True)

    console.print(Panel(
        f"[bold]Topic:[/bold] {topic}\n"
        f"[bold]Output:[/bold] {output_dir}\n"
        f"[bold]Dry Run:[/bold] {dry_run}",
        title="YouTube Automation Pipeline",
        border_style="blue",
    ))

    start_idx = STAGES.index(start_stage) if start_stage and start_stage in STAGES else 0
    stages_to_run = STAGES[start_idx:]

    results = {}

    with Progress(
        SpinnerColumn(),
        TextColumn("[progress.description]{task.description}"),
        console=console,
    ) as progress:

        # --- Stage 1: Discovery ---
        if "discovery" in stages_to_run:
            task = progress.add_task("Discovering topics...", total=None)
            from scripts.discovery import TopicDiscovery

            discovery = TopicDiscovery(config)
            topics = discovery.discover(manual_topic=topic)
            selected = discovery.select_topic(topics)
            results["topic"] = selected
            progress.update(task, description=f"[green]Topic: {selected['title']}")

        # --- Stage 2: Script Generation ---
        if "script" in stages_to_run:
            task = progress.add_task("Generating script...", total=None)
            if dry_run:
                results["script"] = {"title": topic, "sections": [], "tags": [], "dry_run": True}
                progress.update(task, description="[yellow]Script: dry run (skipped)")
            else:
                from scripts.script_gen import ScriptGenerator

                generator = ScriptGenerator(config)
                duration = config.get("video", {}).get("target_duration", 600)
                script_data = generator.generate(topic, duration_seconds=duration)

                script_path = str(output_dir / "script.json")
                generator.save_script(script_data, script_path)

                evaluation = generator.evaluate_script(script_data)
                eval_path = str(output_dir / "script_evaluation.json")
                with open(eval_path, "w") as f:
                    json.dump(evaluation, f, indent=2)

                results["script"] = script_data
                results["script_path"] = script_path
                results["evaluation"] = evaluation

                title = script_data.get("title", "Untitled")
                passes = evaluation.get("passes_quality_gate", "N/A")
                progress.update(task, description=f"[green]Script: '{title}' (quality: {passes})")

        # --- Stage 3: Voice Synthesis ---
        if "voice" in stages_to_run:
            task = progress.add_task("Synthesizing voice...", total=None)
            if dry_run:
                results["voice"] = {"dry_run": True}
                progress.update(task, description="[yellow]Voice: dry run (skipped)")
            else:
                from scripts.voice import VoiceSynthesizer

                synth = VoiceSynthesizer(config)
                script_data = results.get("script")
                if not script_data:
                    script_path = str(output_dir / "script.json")
                    with open(script_path) as f:
                        script_data = json.load(f)

                sections = script_data.get("sections", [])
                if sections:
                    voice_results = synth.synthesize_sections(sections, str(output_dir / "audio"))
                    results["voice"] = voice_results

                    full_narration = "\n\n".join(s["narration"] for s in sections if s.get("narration"))
                    full_audio = str(output_dir / "narration.mp3")
                    full_timestamps = str(output_dir / "timestamps.json")
                    synth.synthesize_with_timestamps(full_narration, full_audio, full_timestamps)
                    results["audio_path"] = full_audio
                    results["timestamps_path"] = full_timestamps
                else:
                    logger.warning("No sections in script, synthesizing full text")
                    full_text = script_data.get("description", topic)
                    full_audio = str(output_dir / "narration.mp3")
                    full_timestamps = str(output_dir / "timestamps.json")
                    synth.synthesize_with_timestamps(full_text, full_audio, full_timestamps)
                    results["audio_path"] = full_audio
                    results["timestamps_path"] = full_timestamps

                progress.update(task, description="[green]Voice: synthesis complete")

        # --- Stage 4: Video Assembly ---
        if "assembly" in stages_to_run:
            task = progress.add_task("Assembling video...", total=None)
            if dry_run:
                results["assembly"] = {"dry_run": True}
                progress.update(task, description="[yellow]Assembly: dry run (skipped)")
            else:
                from scripts.assembly import VideoAssembler

                assembler = VideoAssembler(config)

                timestamps_path = results.get("timestamps_path", str(output_dir / "timestamps.json"))
                srt_path = str(output_dir / "subtitles.srt")
                assembler.generate_srt(timestamps_path, srt_path)

                script_data = results.get("script", {})
                sections = script_data.get("sections", [])
                visual_queries = [s.get("visual_notes", topic) for s in sections] or [topic]

                clips_dir = str(output_dir / "footage")
                all_clips = []
                for query in visual_queries[:5]:
                    clips = assembler.fetch_stock_footage(query, assembler.clip_duration, clips_dir)
                    all_clips.extend(clips)

                audio_path = results.get("audio_path", str(output_dir / "narration.mp3"))
                video_path = str(output_dir / "final.mp4")
                assembler.assemble(audio_path, srt_path, all_clips, video_path)
                results["video_path"] = video_path
                progress.update(task, description="[green]Assembly: video complete")

        # --- Stage 5: Upload Prep ---
        if "upload" in stages_to_run:
            task = progress.add_task("Preparing upload...", total=None)
            from scripts.upload import UploadPrep

            uploader = UploadPrep(config)
            script_data = results.get("script", {"title": topic, "description": "", "tags": []})
            video_path = results.get("video_path", str(output_dir / "final.mp4"))
            audio_path = results.get("audio_path", str(output_dir / "narration.mp3"))

            metadata = uploader.generate_metadata(script_data, video_path, audio_path)
            issues = uploader.validate_metadata(metadata)

            metadata_path = str(output_dir / "metadata.json")
            uploader.save_metadata(metadata, metadata_path)
            results["metadata_path"] = metadata_path
            results["metadata_issues"] = issues

            if issues:
                progress.update(task, description=f"[yellow]Upload: {len(issues)} issue(s)")
            else:
                progress.update(task, description="[green]Upload: metadata ready")

    # --- Summary ---
    console.print("\n")
    console.print(Panel(
        f"[bold]Output directory:[/bold] {output_dir}\n"
        f"[bold]Files generated:[/bold]\n"
        + "\n".join(f"  - {p.name}" for p in output_dir.iterdir() if p.is_file()),
        title="Pipeline Complete",
        border_style="green",
    ))

    return results


def main():
    parser = argparse.ArgumentParser(description="YouTube Automation Pipeline")
    parser.add_argument("--topic", required=True, help="Video topic")
    parser.add_argument("--config", default="config.yaml", help="Config file path")
    parser.add_argument("--dry-run", action="store_true", help="Skip API calls")
    parser.add_argument("--stage", choices=STAGES, help="Start from this stage")
    args = parser.parse_args()

    config = load_config(args.config)

    if args.dry_run:
        config.setdefault("pipeline", {})["dry_run"] = True

    results = run_pipeline(
        topic=args.topic,
        config=config,
        dry_run=args.dry_run,
        start_stage=args.stage,
    )

    return results


if __name__ == "__main__":
    main()
