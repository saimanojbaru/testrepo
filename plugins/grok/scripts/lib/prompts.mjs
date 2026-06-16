/** Prompt builders for the grok companion commands. */

export function buildReviewPrompt({ target, summary, diff }) {
  return [
    "You are performing a focused, read-only code review. Do not modify any files.",
    `Review target: ${target}.`,
    "",
    "Identify correctness bugs, security issues, race conditions, missing error handling,",
    "and risky design choices. Prioritize high-confidence, actionable findings. For each",
    "finding give: the file/line if known, severity (high/medium/low), what is wrong, and a",
    "concrete fix. If the change looks safe, say so plainly. Be concise.",
    "",
    "You may read repository files for context with read_file/grep/list_dir.",
    "",
    "## Change summary",
    "```",
    summary || "(no stat available)",
    "```",
    "",
    "## Diff",
    "```diff",
    diff || "(diff embedded above)",
    "```"
  ].join("\n");
}

export function buildSearchPrompt(query) {
  return [
    "You are Grok, with native, real-time access to X (x.com) and the web.",
    "Use the web_search and web_fetch tools to answer the request below using the most",
    "current information available. Prioritize primary sources on X (posts, threads,",
    "author accounts) when the topic is social, breaking, or opinion-driven; fall back to",
    "the broader web for documentation and reference facts.",
    "",
    "Return:",
    "1. A direct, well-organized answer.",
    "2. A short 'Sources' list with URLs (include x.com post links when used).",
    "3. Note recency where it matters (e.g. 'as of <date>').",
    "",
    "Request:",
    query
  ].join("\n");
}
