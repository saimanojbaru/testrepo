package com.hitit.domain.vibe

/**
 * The cross-pillar "Vibe Score" — one 0..100 number blending the habit game (Momentum), BodyFlow
 * (fuel + check-ins) and MoneyVibe (spend awareness). Habits stay the spine at half the weight;
 * pillars the user hasn't started yet are null and their weight is redistributed, so a habits-only
 * user isn't punished for not logging food or money. Pure + deterministic.
 */
object VibeScore {
    const val HABIT_WEIGHT = 0.50f
    const val BODY_WEIGHT = 0.25f
    const val MONEY_WEIGHT = 0.25f

    /** Blend available pillar scores (each 0..100); null pillars redistribute their weight. */
    fun compose(habit: Int?, body: Int?, money: Int?): Int {
        val parts = listOfNotNull(
            habit?.let { it.coerceIn(0, 100) to HABIT_WEIGHT },
            body?.let { it.coerceIn(0, 100) to BODY_WEIGHT },
            money?.let { it.coerceIn(0, 100) to MONEY_WEIGHT },
        )
        if (parts.isEmpty()) return 0
        val totalWeight = parts.sumOf { it.second.toDouble() }
        val blended = parts.sumOf { (score, w) -> score * (w / totalWeight) }
        return blended.toInt().coerceIn(0, 100)
    }

    /**
     * BodyFlow pillar: consistency of fuelling (days with food logged in the last 7) plus today's
     * Vibe Check. Null when the pillar is untouched (no logs ever) — handled by the caller.
     */
    fun bodyPulse(foodDaysLast7: Int, checkedInToday: Boolean): Int {
        val days = foodDaysLast7.coerceIn(0, 7)
        val base = 16 + days * 10            // 16..86 — showing up is most of the score
        val checkIn = if (checkedInToday) 14 else 0
        return (base + checkIn).coerceIn(0, 100)
    }

    /**
     * MoneyVibe pillar: spend *awareness* (days tracked in the last 7) plus burner-budget health.
     * [burnerUtilization] = spent/budget for the week (null = no budget set). Under budget earns a
     * bonus; blowing it bleeds the score — but tracking always counts for something.
     */
    fun moneyPulse(spendDaysLast7: Int, burnerUtilization: Float?): Int {
        val days = spendDaysLast7.coerceIn(0, 7)
        val base = 20 + days * 8              // 20..76
        val budget = when {
            burnerUtilization == null -> 0
            burnerUtilization <= 1f -> (24 * (1f - burnerUtilization * 0.5f)).toInt() // 12..24
            else -> (-32 * (burnerUtilization - 1f)).toInt().coerceAtLeast(-40)
        }
        return (base + budget).coerceIn(0, 100)
    }

    /** The evolving avatar — the face of the whole OS. */
    fun avatarFor(score: Int): String = when {
        score >= 90 -> "👑"
        score >= 75 -> "🔥"
        score >= 60 -> "😎"
        score >= 40 -> "🙂"
        score >= 20 -> "🌱"
        else -> "😴"
    }

    fun label(score: Int): String = when {
        score >= 90 -> "Immaculate vibes"
        score >= 75 -> "Vibing hard"
        score >= 60 -> "In your era"
        score >= 40 -> "Finding the groove"
        score >= 20 -> "Warming up"
        else -> "Low battery"
    }
}
