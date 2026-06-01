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

    /** Momentum for completing a Big Play checkpoint, and for completing the whole Big Play. */
    const val CHECKPOINT = 15
    const val BIG_PLAY = 50
    fun awardForCheckpoint(): Int = CHECKPOINT
    fun awardForBigPlay(): Int = BIG_PLAY

    /** Bonus multiplier applied to a hit's award when it completes a "Perfect Day". */
    const val PERFECT_DAY_MULTIPLIER = 1.5

    /**
     * Whether completing one more scheduled rep makes today perfect: every scheduled rep met.
     * [repsScheduled] must be > 0 and [repsMetBefore] is the count met before this completion.
     */
    fun completesPerfectDay(repsScheduled: Int, repsMetBefore: Int): Boolean =
        repsScheduled > 0 && repsMetBefore + 1 >= repsScheduled

    /** A hit's award with the Perfect Day multiplier applied when [perfect] is true. */
    fun applyPerfectDay(baseAward: Int, perfect: Boolean): Int =
        if (perfect) (baseAward * PERFECT_DAY_MULTIPLIER).toInt() else baseAward

    /**
     * Apply an Identity Cards bonus to a base award: `base * (1 + percent/100)`, floored.
     * Order is fixed and documented: identity bonus is applied to the BASE award FIRST, then the
     * Perfect Day multiplier (see [hitAward]). [bonusPercent] is clamped to a sane 0..100 here.
     */
    fun applyIdentityBonus(baseAward: Int, bonusPercent: Int): Int {
        val pct = bonusPercent.coerceIn(0, 100)
        return baseAward + (baseAward * pct / 100)
    }

    /**
     * The single source of truth for a met-rep's award, so `logHit` and `clearHit` stay symmetric:
     * base from streak → identity bonus → Perfect Day. Same inputs always yield the same number, so
     * an undo reverses exactly (no ledger drift).
     */
    fun hitAward(currentStreak: Int, identityBonusPercent: Int, perfectDay: Boolean): Int =
        applyPerfectDay(applyIdentityBonus(awardForHit(currentStreak), identityBonusPercent), perfectDay)

    /** Reduced Momentum for an Active Recovery action (logging something light on a rest day). */
    const val RECOVERY = 4
    fun awardForRecovery(): Int = RECOVERY

    /**
     * The Momentum penalty for missing a Sudden Death rep on a scheduled day. Loss aversion: a
     * miss costs roughly a full day's solid effort. Returned as a positive magnitude; callers post
     * it as a negative ledger entry.
     */
    const val SUDDEN_DEATH_PENALTY = 30
    fun suddenDeathPenalty(): Int = SUDDEN_DEATH_PENALTY
}
