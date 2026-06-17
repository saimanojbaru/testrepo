# YouTube Automation Pipeline — Master System Prompt

## Project Overview
Automated YouTube video pipeline: topic discovery → AI script → ElevenLabs voiceover → video assembly → upload prep.

## Pipeline Stages
1. **Discovery** (`scripts/discovery.py`) — Find trending topics via Reddit, Google Trends, or manual input.
2. **Script Generation** (`scripts/script_gen.py`) — Claude generates title, description, tags, full narration script with section timestamps.
3. **Voice Synthesis** (`scripts/voice.py`) — ElevenLabs converts script to narration audio with word-level timestamps for captions.
4. **Video Assembly** (`scripts/assembly.py`) — FFmpeg combines stock footage + narration + auto-generated subtitles.
5. **Upload Prep** (`scripts/upload.py`) — Generates YouTube metadata and optionally uploads via YouTube Data API.

## Brand Voice & Video Style

### Tone
- Conversational but authoritative — like explaining to a smart friend
- Hook-driven openings: first 5 seconds must grab attention
- No filler phrases ("without further ado", "let's dive in")
- Use concrete examples over abstract claims
- End with a clear CTA (subscribe, comment, next video tease)

### Video Style
- Duration target: 8–12 minutes (optimal for ad revenue)
- Shorts variant: 30–60 seconds
- B-roll changes every 5–8 seconds to maintain visual engagement
- Subtitles always on — burned into video (accessibility + silent viewers)
- Clean lower-third for key stats/quotes

### Script Structure
```
[HOOK] — 0:00–0:15 — Attention-grabbing question or bold claim
[INTRO] — 0:15–0:45 — Context + "here's what we'll cover"
[SECTION 1–N] — Core content, each 1.5–3 min
[RECAP] — Quick summary of key points
[CTA] — Subscribe + comment prompt + next video tease
```

## Quality Gates
Before any output is finalized, verify:
- [ ] Script passes readability check (Flesch-Kincaid grade 6–8)
- [ ] Audio file exists and duration matches expected length (±10%)
- [ ] All sections have corresponding footage clips
- [ ] Subtitles are synced within 200ms tolerance
- [ ] Thumbnail title is ≤ 6 words
- [ ] Description includes at least 3 relevant tags
- [ ] No copyrighted music or footage (stock footage only)

## Configuration
All settings live in `config.yaml`. API keys in `.env` (never committed).

### Key Config Options
- `niche` — Channel topic/niche
- `target_duration` — Video length in seconds
- `voice_id` — ElevenLabs voice to use
- `footage_source` — Where to pull stock footage (pexels, local)
- `subtitle_style` — Font, size, color, position for burned-in captions

## Script Generation Prompt Template
When generating scripts, Claude should produce JSON:
```json
{
  "title": "Your Compelling Title Here",
  "description": "YouTube description with links and timestamps",
  "tags": ["tag1", "tag2", "tag3"],
  "thumbnail_text": "SHORT HOOK",
  "sections": [
    {
      "name": "hook",
      "timestamp": "0:00",
      "duration_seconds": 15,
      "narration": "The actual words to speak...",
      "visual_notes": "Description of what B-roll to show"
    }
  ],
  "total_duration_seconds": 600
}
```

## File Conventions
- Generated audio: `output/{topic_slug}/narration.mp3`
- Generated video: `output/{topic_slug}/final.mp4`
- Metadata: `output/{topic_slug}/metadata.json`
- Subtitles: `output/{topic_slug}/subtitles.srt`
- Logs: `logs/pipeline_{timestamp}.log`

## Running the Pipeline
```bash
# Full pipeline
python main.py --topic "Your Topic Here"

# Dry run (no API calls, no file generation)
python main.py --topic "Your Topic Here" --dry-run

# Single stage
python main.py --topic "Your Topic Here" --stage voice

# With custom config
python main.py --topic "Your Topic Here" --config config.yaml
```

## Example Good Script (Snippet)
```
[HOOK]
"Did you know that 90% of startups fail — but NOT for the reason you think?"

[INTRO]
"In this video, I'm breaking down the 5 real reasons startups fail,
backed by data from over 10,000 companies. And number 3 is the one
nobody talks about. Let's get into it."
```
