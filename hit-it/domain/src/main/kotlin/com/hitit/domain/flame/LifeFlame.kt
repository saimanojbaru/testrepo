package com.hitit.domain.flame

import kotlin.math.max

/**
 * The Life Flame — a single emotional "how alive is your practice" signal, levels 1..5.
 *
 * Driven by today's [com.hitit.domain.momentum.MomentumScore] AND the current best streak, so a long
 * streak keeps the flame burning on a slow day, while a single great day can also stoke it. Pure and
 * deterministic (no Android, no clock) — mirrors the MomentumScore / TrophyCatalog style.
 */
object LifeFlame {
    const val MIN_LEVEL = 1
    const val MAX_LEVEL = 5

    /** Number of recent days with no positive Momentum that marks the flame as "fading". */
    const val FADING_WINDOW = 3

    /** Flame level 1..5 from today's score (0..100) and the best current streak (days). */
    fun levelFor(score: Int, bestStreak: Int): Int =
        max(scoreTier(score), streakTier(bestStreak)).coerceIn(MIN_LEVEL, MAX_LEVEL)

    private fun scoreTier(score: Int): Int = when {
        score >= 85 -> 5
        score >= 60 -> 4
        score >= 40 -> 3
        score >= 20 -> 2
        else -> 1
    }

    private fun streakTier(bestStreak: Int): Int = when {
        bestStreak >= 60 -> 5
        bestStreak >= 21 -> 4
        bestStreak >= 7 -> 3
        bestStreak >= 3 -> 2
        else -> 1
    }

    fun label(level: Int): String = when (level.coerceIn(MIN_LEVEL, MAX_LEVEL)) {
        5 -> "Inferno"
        4 -> "Strong"
        3 -> "Steady"
        2 -> "Weak"
        else -> "Dying"
    }

    fun tagline(level: Int): String = when (level.coerceIn(MIN_LEVEL, MAX_LEVEL)) {
        5 -> "Unstoppable. Keep feeding the fire."
        4 -> "Burning bright — stay on it."
        3 -> "Holding steady. One more rep stokes it."
        2 -> "Flame's low. Log a rep to build it back."
        else -> "Your flame is fading — reignite it today."
    }

    /**
     * Whether the flame is fading: the last [FADING_WINDOW] days all earned no positive Momentum.
     * [recentDailyMomentum] is most-recent-first or oldest-first — order doesn't matter; we only
     * check that every value in the window is <= 0. An empty/short list is NOT fading (not enough
     * evidence) — avoids nagging brand-new users.
     */
    fun isFading(recentDailyMomentum: List<Int>): Boolean {
        if (recentDailyMomentum.size < FADING_WINDOW) return false
        return recentDailyMomentum.take(FADING_WINDOW).all { it <= 0 }
    }
}
