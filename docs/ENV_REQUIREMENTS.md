# Environment Requirements for Unity APK Build

This Claude Code on the web environment must have a network policy that allows the following hosts so the Unity 2022.3 LTS headless build pipeline can run.

## Required hosts

### Unity
- `download.unity3d.com` — editor + Android Build Support module tarballs
- `unity.com` — release archive page (hash lookup if pinned URL drifts)
- `license.unity3d.com` — manual `.alf` → `.ulf` activation flow
- `licensing.unity3d.com` — alternate licensing endpoint
- `*.unity3d.com` / `*.unity.com` — safest catch-all

### Android toolchain (used by Unity's Gradle build step)
- `dl.google.com` — Android SDK command-line tools, build-tools, platforms, NDK
- `services.gradle.org` — Gradle wrapper distribution
- `repo1.maven.org`, `repo.maven.apache.org` — Maven Central (Android dependencies)
- `plugins.gradle.org`

### Already allowed (no action needed)
- `github.com` / `raw.githubusercontent.com` — Ink Unity Integration package, source push, version.json
- `pypi.org` — Python packages (Pillow for placeholder art generation)

## How to update

1. Go to claude.ai/code → Environments → (this environment) → Network policy
2. Select the most permissive option, or add the explicit allows above
3. If the change cannot be hot-applied, recreate the environment with the same repo (`saimanojbaru/testrepo`) and branch (`claude/corporate-dragon-game-LMg9H`), then say "resume the Unity build" in the new session

## State preserved in this branch

- `docs/BUILD_PLAN.md` — the approved implementation plan (unchanged from `/root/.claude/plans/...`)
- `docs/ENV_REQUIREMENTS.md` — this file

No game code has been written yet. The session was blocked at the host-prep stage when Unity hosts returned `403 host_not_allowed`.
