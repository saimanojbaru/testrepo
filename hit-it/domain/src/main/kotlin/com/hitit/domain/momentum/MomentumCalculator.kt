package com.hitit.domain.momentum

import com.hitit.domain.model.Priority

/** Decides how much Momentum (XP) an action awards. */
object MomentumCalculator {
    const val BASE = 10
    const val STREAK_BONUS_CAP = 20

    /**
     * Momentum awarded for completing a Rep, rewarding longer streaks.
     * award = BASE + min(currentStreak, STREAK_BONUS_CAP)
     */
    fun awardForHit(currentStreak: Int): Int =
        BASE + currentStreak.coerceIn(0, STREAK_BONUS_CAP)

    /**
     * Momentum awarded for completing a Hit (task), scaled by its priority.
     * HIGH = 20, MEDIUM = 15, LOW = 10.
     */
    fun awardForTask(priority: Priority): Int = BASE + when (priority) {
        Priority.HIGH -> 10
        Priority.MEDIUM -> 5
        Priority.LOW -> 0
    }

    /** Momentum awarded for a Lock In session: 1 per focused minute. */
    fun awardForFocus(focusedMinutes: Int): Int = focusedMinutes.coerceAtLeast(0)

    /** Flat Momentum awarded for a journal entry (a morning or an evening Check-In). */
    const val CHECK_IN = 8
    fun awardForCheckIn(): Int = CHECK_IN
}
