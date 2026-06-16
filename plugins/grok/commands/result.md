---
description: Show the final output of a completed Grok job
argument-hint: "[--job <id>]"
allowed-tools: Bash(node:*)
---

Run:

```bash
node "${CLAUDE_PLUGIN_ROOT}/scripts/grok-companion.mjs" result $ARGUMENTS
```

Return the companion's stdout verbatim. With no `--job`, it shows the most recently completed job. The output includes the Grok session id so the user can resume that run directly with `grok -r <session-id>`.
