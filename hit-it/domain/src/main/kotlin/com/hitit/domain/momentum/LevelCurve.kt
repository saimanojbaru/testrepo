package com.hitit.domain.momentum

import kotlin.math.pow
import kotlin.math.roundToLong

/**
 * Maps total accumulated Momentum (XP) to a Level in 1..[MAX_LEVEL] using a smooth power curve,
 * so we never hardcode per-level thresholds.
 *
 * cumulativeFor(L) = C * (L - 1)^P  (level 1 needs 0 Momentum).
 */
object LevelCurve {
    const val MAX_LEVEL = 100
    private const val C = 100.0
    private const val P = 1.5

    /** Total Momentum required to *reach* [level]. */
    fun cumulativeFor(level: Int): Long {
        val l = level.coerceIn(1, MAX_LEVEL)
        return (C * (l - 1).toDouble().pow(P)).roundToLong()
    }

    /** Highest level whose cumulative requirement is satisfied by [totalMomentum] (capped at max). */
    fun levelFor(totalMomentum: Long): Int {
        if (totalMomentum <= 0) return 1
        var level = 1
        for (l in 1..MAX_LEVEL) {
            if (cumulativeFor(l) <= totalMomentum) level = l else break
        }
        return level
    }

    /** Progress in [0,1] from the current level toward the next (1.0 at max level). */
    fun progressToNext(totalMomentum: Long): Float {
        val level = levelFor(totalMomentum)
        if (level >= MAX_LEVEL) return 1f
        val cur = cumulativeFor(level)
        val next = cumulativeFor(level + 1)
        if (next <= cur) return 1f
        return ((totalMomentum - cur).toDouble() / (next - cur).toDouble())
            .toFloat()
            .coerceIn(0f, 1f)
    }

    /** Momentum still needed to reach the next level (0 at max level). */
    fun momentumToNext(totalMomentum: Long): Long {
        val level = levelFor(totalMomentum)
        if (level >= MAX_LEVEL) return 0
        return (cumulativeFor(level + 1) - totalMomentum).coerceAtLeast(0)
    }
}
