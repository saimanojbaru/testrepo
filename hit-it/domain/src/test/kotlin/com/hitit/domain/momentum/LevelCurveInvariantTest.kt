package com.hitit.domain.momentum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** Property-style invariants over the full level curve and its interaction with the tier ladder. */
class LevelCurveInvariantTest {

    @Test
    fun levelForIsConsistentWithCumulativeAtEveryLevel() {
        for (level in 1..LevelCurve.MAX_LEVEL) {
            val atThreshold = LevelCurve.cumulativeFor(level)
            assertEquals("levelFor(cumulativeFor($level))", level, LevelCurve.levelFor(atThreshold))
        }
    }

    @Test
    fun progressIsMonotonicWithinALevel() {
        // Pick a mid level and walk momentum from its threshold toward the next.
        val level = 20
        val start = LevelCurve.cumulativeFor(level)
        val end = LevelCurve.cumulativeFor(level + 1)
        var prev = -1f
        var m = start
        val step = ((end - start) / 10).coerceAtLeast(1)
        while (m < end) {
            val p = LevelCurve.progressToNext(m)
            assertTrue("progress in [0,1]", p in 0f..1f)
            assertTrue("progress non-decreasing", p >= prev)
            prev = p
            m += step
        }
    }

    @Test
    fun everyLevelMapsToAValidTierAcrossTheWholeCurve() {
        for (level in 1..LevelCurve.MAX_LEVEL) {
            val tier = TierLadder.tierFor(level)
            assertTrue(level in tier.minLevel..tier.maxLevel)
        }
    }

    @Test
    fun momentumToNextReachesExactlyTheNextThreshold() {
        for (level in 1 until LevelCurve.MAX_LEVEL) {
            val atThreshold = LevelCurve.cumulativeFor(level)
            val need = LevelCurve.momentumToNext(atThreshold)
            assertEquals(level + 1, LevelCurve.levelFor(atThreshold + need))
        }
    }
}
