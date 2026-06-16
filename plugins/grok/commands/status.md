---
description: Show running and recent Grok jobs for this repository
argument-hint: "[--job <id>]"
allowed-tools: Bash(node:*)
---

Run:

```bash
node "${CLAUDE_PLUGIN_ROOT}/scripts/grok-companion.mjs" status $ARGUMENTS
```

Return the companion's stdout verbatim. If a specific `--job <id>` was given, it shows just that job; otherwise it lists recent jobs.
