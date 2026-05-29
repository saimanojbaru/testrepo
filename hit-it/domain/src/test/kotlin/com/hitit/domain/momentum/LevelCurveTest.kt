package com.hitit.domain.momentum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelCurveTest {

    @Test
    fun level1RequiresZero() {
        assertEquals(0L, LevelCurve.cumulativeFor(1))
        assertEquals(1, LevelCurve.levelFor(0))
        assertEquals(1, LevelCurve.levelFor(-100))
    }

    @Test
    fun cumulativeIsStrictlyIncreasing() {
        for (l in 1 until LevelCurve.MAX_LEVEL) {
            assertTrue(
                "level ${l + 1} must require more than level $l",
                LevelCurve.cumulativeFor(l + 1) > LevelCurve.cumulativeFor(l),
            )
        }
    }

    @Test
    fun levelForRespectsBoundaries() {
        val at10 = LevelCurve.cumulativeFor(10)
        assertEquals(10, LevelCurve.levelFor(at10))
        assertEquals(9, LevelCurve.levelFor(at10 - 1))
    }

    @Test
    fun levelIsCappedAtMax() {
        val atMax = LevelCurve.cumulativeFor(LevelCurve.MAX_LEVEL)
        assertEquals(LevelCurve.MAX_LEVEL, LevelCurve.levelFor(atMax))
        assertEquals(LevelCurve.MAX_LEVEL, LevelCurve.levelFor(atMax + 5_000_000))
    }

    @Test
    fun progressAndRemainderAreSane() {
        val at10 = LevelCurve.cumulativeFor(10)
        assertEquals(0f, LevelCurve.progressToNext(at10), 0.0001f)
        assertTrue(LevelCurve.progressToNext(at10 + 1) > 0f)
        assertTrue(LevelCurve.momentumToNext(at10) > 0L)

        val atMax = LevelCurve.cumulativeFor(LevelCurve.MAX_LEVEL)
        assertEquals(1f, LevelCurve.progressToNext(atMax), 0.0001f)
        assertEquals(0L, LevelCurve.momentumToNext(atMax))
    }
}
