import { spawnSync } from "node:child_process";

function git(cwd, args) {
  const result = spawnSync("git", args, { cwd, encoding: "utf8", maxBuffer: 32 * 1024 * 1024 });
  if (result.error || result.status !== 0) {
    return null;
  }
  return result.stdout ?? "";
}

export function isGitRepo(cwd) {
  return git(cwd, ["rev-parse", "--is-inside-work-tree"])?.trim() === "true";
}

/**
 * Collect a review target description plus the diff text.
 * If `base` is given, compares the working tree against that ref; otherwise
 * shows uncommitted work (staged + unstaged) against HEAD.
 *
 * @returns {{ ok: boolean, detail: string, summary?: string, diff?: string, target?: string }}
 */
export function collectReviewDiff(cwd, base = null) {
  if (!isGitRepo(cwd)) {
    return { ok: false, detail: "Not inside a git repository. /grok:review needs a git repo to diff." };
  }

  const target = base ? `branch changes vs \`${base}\`` : "uncommitted changes";
  const diffArgs = base ? ["diff", `${base}...HEAD`] : ["diff", "HEAD"];
  const statArgs = base ? ["diff", "--stat", `${base}...HEAD`] : ["diff", "--stat", "HEAD"];

  let summary = git(cwd, statArgs) ?? "";
  let diff = git(cwd, diffArgs) ?? "";

  // Include untracked files for an uncommitted-changes review.
  if (!base) {
    const untracked = (git(cwd, ["ls-files", "--others", "--exclude-standard"]) ?? "")
      .split("\n")
      .map((l) => l.trim())
      .filter(Boolean);
    if (untracked.length) {
      summary += `\nUntracked files:\n${untracked.map((f) => `  ${f}`).join("\n")}`;
    }
  }

  if (!diff.trim() && !summary.trim()) {
    return { ok: false, detail: `No changes found for ${target}.`, target };
  }

  // Cap the diff so we never blow past model context on huge changes.
  const MAX = 180_000;
  let truncated = false;
  if (diff.length > MAX) {
    diff = `${diff.slice(0, MAX)}\n\n[diff truncated at ${MAX} characters]`;
    truncated = true;
  }

  return { ok: true, detail: "", summary: summary.trim(), diff, target, truncated };
}
