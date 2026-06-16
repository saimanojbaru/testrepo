---
description: Run a read-only Grok code review of your uncommitted changes or a branch
argument-hint: "[--base <ref>] [--background|--wait] [--model <model>] [optional focus text]"
allowed-tools: Bash(node:*)
---

Run a read-only code review with Grok. Grok will not modify any files.

Raw user request:
$ARGUMENTS

Run:

```bash
node "${CLAUDE_PLUGIN_ROOT}/scripts/grok-companion.mjs" review $ARGUMENTS
```

Rules:

- Default target is uncommitted changes. `--base <ref>` reviews the current branch against that ref (e.g. `--base main`).
- Any leftover text after the flags is forwarded as extra reviewer focus.
- `--background` runs the review detached; report the job id and point the user at `/grok:status` and `/grok:result`. Otherwise run in the foreground.
- This command is read-only. It does not change code.
- Return the companion's stdout verbatim. Do not summarize or editorialize.
- If the companion reports no changes, relay that. If it reports Grok is missing or not logged in, tell the user to run `/grok:setup`.
