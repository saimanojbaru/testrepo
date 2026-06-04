package com.hitit.domain.coach

/**
 * Optionally rewrites the rule-based [CoachInsight]s into a single flowing, generative paragraph.
 * Implementations (e.g. an on-device LLM) live in :app. Returning null means "no rephrase available"
 * — the caller then shows the structured rule-based text. This keeps the LLM strictly additive: the
 * app is fully functional, and brutally honest, with or without it.
 */
interface CoachRephraser {
    /** True when a model is actually loaded and ready; UI uses this to label the coach's mode. */
    suspend fun isReady(): Boolean

    /** Generative rewrite of [insights], or null on any unavailability/failure (caller falls back). */
    suspend fun rephrase(insights: List<CoachInsight>): String?
}

/** A no-op rephraser: always unavailable. The default wiring, so the app ships working with no model. */
object NoOpCoachRephraser : CoachRephraser {
    override suspend fun isReady(): Boolean = false
    override suspend fun rephrase(insights: List<CoachInsight>): String? = null
}

/** Builds the LLM prompt from structured insights. Pure + unit-tested so the wording is verifiable. */
object CoachPrompt {
    fun build(insights: List<CoachInsight>): String {
        val findings = insights.joinToString("\n") { "- [${it.tone}] ${it.headline}: ${it.detail}" }
        return buildString {
            appendLine("You are a private, brutally honest performance coach inside a habit app.")
            appendLine("Rewrite the findings below as ONE short paragraph (max 80 words):")
            appendLine("direct, no sugar-coating, second person, end with one concrete action.")
            appendLine("Do not invent facts beyond the findings. Do not use bullet points.")
            appendLine()
            appendLine("Findings:")
            append(findings)
        }
    }
}
