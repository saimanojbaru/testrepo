---
description: Check whether the local Grok CLI is installed and authenticated
argument-hint: ""
allowed-tools: Bash(node:*)
---

Run:

```bash
node "${CLAUDE_PLUGIN_ROOT}/scripts/grok-companion.mjs" setup --json
```

Then present a short, friendly summary of the result to the user:

- If `available` is false: tell them the Grok CLI was not found and to install it from https://docs.x.ai (or set `GROK_BIN` to the binary path), then rerun `/grok:setup`.
- If `available` is true but `loggedIn` is false: tell them Grok is installed but not signed in, and to run `!grok login`, then rerun `/grok:setup`.
- If both are true: confirm Grok is ready and report the account and default model.

Do not run any other commands.
