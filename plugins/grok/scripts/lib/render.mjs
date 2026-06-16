/** Small helpers for rendering companion output as readable Markdown. */

export function shorten(text, limit = 100) {
  const normalized = String(text ?? "").trim().replace(/\s+/g, " ");
  if (normalized.length <= limit) {
    return normalized;
  }
  return `${normalized.slice(0, limit - 1)}…`;
}

export function relativeTime(iso) {
  if (!iso) {
    return "unknown";
  }
  const then = new Date(iso).getTime();
  if (Number.isNaN(then)) {
    return iso;
  }
  const seconds = Math.max(0, Math.round((Date.now() - then) / 1000));
  if (seconds < 60) {
    return `${seconds}s ago`;
  }
  const minutes = Math.round(seconds / 60);
  if (minutes < 60) {
    return `${minutes}m ago`;
  }
  const hours = Math.round(minutes / 60);
  if (hours < 24) {
    return `${hours}h ago`;
  }
  return `${Math.round(hours / 24)}d ago`;
}

const STATUS_BADGE = {
  running: "🟡 running",
  finished: "✅ finished",
  cancelled: "⛔ cancelled",
  failed: "❌ failed"
};

export function statusBadge(status) {
  return STATUS_BADGE[status] ?? status ?? "unknown";
}

/** Footer that tells the user how to reopen a Grok session directly. */
export function resumeHint(sessionId) {
  if (!sessionId) {
    return "";
  }
  return `\n\n---\n_Grok session \`${sessionId}\`. Resume it directly with_ \`grok -r ${sessionId}\`.`;
}

export function renderJobLine(job) {
  const session = job.sessionId ? ` · session \`${job.sessionId}\`` : "";
  return `- **${job.id}** — ${statusBadge(job.status)} · ${job.kind ?? "task"} · ${relativeTime(job.startedAt)}${session}\n  > ${shorten(job.prompt, 120)}`;
}
