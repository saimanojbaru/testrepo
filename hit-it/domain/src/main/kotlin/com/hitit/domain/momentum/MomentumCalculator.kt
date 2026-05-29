package com.hitit.domain.momentum

/** Decides how much Momentum (XP) a single hit awards. */
object MomentumCalculator {
    const val BASE = 10
    const val STREAK_BONUS_CAP = 20

    /**
     * Momentum awarded for completing a Rep, rewarding longer streaks.
     * award = BASE + min(currentStreak, STREAK_BONUS_CAP)
     */
    fun awardForHit(currentStreak: Int): Int =
        BASE + currentStreak.coerceIn(0, STREAK_BONUS_CAP)
}
