package com.hitit.domain.momentum

import kotlin.math.roundToInt

/**
 * The daily Momentum Score (0..100) — a single "how's today going" number for the dashboard hero.
 * Pure and deterministic. Weighted across the day's pillars:
 *  - Reps     : up to 50 (fraction of today's scheduled reps that are met)
 *  - Check-In : 20 (logged or not)
 *  - Hits     : up to 15 (caps at [HITS_FOR_MAX] completed today)
 *  - Focus    : up to 15 (caps at [FOCUS_MIN_FOR_MAX] minutes today)
 */
object MomentumScore {
    const val REPS_WEIGHT = 50
    const val CHECKIN_WEIGHT = 20
    const val HITS_WEIGHT = 15
    const val FOCUS_WEIGHT = 15
    const val HITS_FOR_MAX = 3
    const val FOCUS_MIN_FOR_MAX = 60

    fun score(
        repsScheduled: Int,
        repsMet: Int,
        checkedIn: Boolean,
        hitsCompletedToday: Int,
        focusMinutesToday: Int,
    ): Int {
        val repsPart = if (repsScheduled <= 0) {
            REPS_WEIGHT // nothing scheduled -> don't penalize
        } else {
            (repsMet.coerceIn(0, repsScheduled).toDouble() / repsScheduled * REPS_WEIGHT).roundToInt()
        }
        val checkInPart = if (checkedIn) CHECKIN_WEIGHT else 0
        val hitsPart = (hitsCompletedToday.coerceIn(0, HITS_FOR_MAX).toDouble() / HITS_FOR_MAX * HITS_WEIGHT).roundToInt()
        val focusPart = (focusMinutesToday.coerceIn(0, FOCUS_MIN_FOR_MAX).toDouble() / FOCUS_MIN_FOR_MAX * FOCUS_WEIGHT).roundToInt()
        return (repsPart + checkInPart + hitsPart + focusPart).coerceIn(0, 100)
    }

    /** A short, positive, on-brand label for a score. */
    fun label(score: Int): String = when {
        score >= 90 -> "On fire 🔥"
        score >= 70 -> "Strong day 💪"
        score >= 40 -> "Building momentum ⚡"
        score > 0 -> "Warming up"
        else -> "Let's go"
    }
}
