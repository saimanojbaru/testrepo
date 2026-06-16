---
description: Delegate a coding or investigation task to Grok (write-capable by default)
argument-hint: "[--background|--wait] [--resume-last|--fresh] [--read-only] [--model <model>] [--effort <low|medium|high>] [what Grok should do]"
allowed-tools: Bash(node:*), AskUserQuestion, Agent
---

Invoke the `grok:grok-rescue` subagent via the `Agent` tool (`subagent_type: "grok:grok-rescue"`), forwarding the raw user request as the prompt. The final user-visible response must be Grok's output verbatim.

Raw user request:
$ARGUMENTS

Execution mode:

- If the request includes `--background`, run the `grok:grok-rescue` subagent in the background.
- If the request includes `--wait`, run it in the foreground.
- If neither flag is present, default to foreground for small bounded tasks, background for open-ended ones.
- `--background` and `--wait` are Claude Code execution flags. Do not forward them as task text.
- `--model`, `--effort`, `--read-only` are runtime controls. Preserve them but do not treat them as task text.
- If the request includes `--resume-last` or `--fresh`, the user already chose; do not ask.
- Otherwise, check for a resumable Grok task session for this repo:

```bash
node "${CLAUDE_PLUGIN_ROOT}/scripts/grok-companion.mjs" task-resume-candidate --json
```

- If that reports `available: true`, use `AskUserQuestion` exactly once with these two choices:
  - `Continue current Grok session`
  - `Start a new Grok session`
- If the user is clearly giving a follow-up ("continue", "keep going", "apply the top fix", "dig deeper"), put `Continue current Grok session (Recommended)` first; otherwise put `Start a new Grok session (Recommended)` first.
- If the user chooses continue, add `--resume-last` before routing to the subagent. If they choose new, add `--fresh`.
- If `available: false`, do not ask. Route normally.

Operating rules:

- The subagent is a thin forwarder. Return its stdout verbatim. Do not paraphrase or add commentary.
- If the companion reports Grok is missing or not logged in, stop and tell the user to run `/grok:setup`.
- If the user gave no request, ask what Grok should do.
