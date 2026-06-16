---
description: Cancel a running background Grok job
argument-hint: "[--job <id>]"
allowed-tools: Bash(node:*)
---

Run:

```bash
node "${CLAUDE_PLUGIN_ROOT}/scripts/grok-companion.mjs" cancel $ARGUMENTS
```

Return the companion's stdout verbatim. With no `--job`, it cancels the most recent running job.
