package com.hitit.domain.flame

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LifeFlameTest {

    @Test
    fun deadColdStartIsDying() {
        assertEquals(1, LifeFlame.levelFor(score = 0, bestStreak = 0))
        assertEquals("Dying", LifeFlame.label(1))
    }

    @Test
    fun perfectScoreIsInferno() {
        assertEquals(5, LifeFlame.levelFor(score = 100, bestStreak = 0))
        assertEquals("Inferno", LifeFlame.label(5))
    }

    @Test
    fun scoreTierBoundaries() {
        assertEquals(2, LifeFlame.levelFor(20, 0))
        assertEquals(3, LifeFlame.levelFor(40, 0))
        assertEquals(4, LifeFlame.levelFor(60, 0))
        assertEquals(5, LifeFlame.levelFor(85, 0))
    }

    @Test
    fun longStreakRescuesABadDay() {
        // Score is low (tier 1) but a 60-day streak keeps the flame at Inferno.
        assertEquals(5, LifeFlame.levelFor(score = 5, bestStreak = 60))
        // A 7-day streak holds at Steady even on a zero-score day.
        assertEquals(3, LifeFlame.levelFor(score = 0, bestStreak = 7))
    }

    @Test
    fun takesTheHigherOfScoreAndStreak() {
        // Great day (tier 5) outranks a short streak (tier 2).
        assertEquals(5, LifeFlame.levelFor(score = 90, bestStreak = 4))
    }

    @Test
    fun levelIsAlwaysInRange() {
        for (s in -10..120) {
            for (streak in intArrayOf(0, 1, 3, 7, 21, 60, 999)) {
                val lvl = LifeFlame.levelFor(s, streak)
                assertTrue(lvl in LifeFlame.MIN_LEVEL..LifeFlame.MAX_LEVEL)
            }
        }
    }

    @Test
    fun fadingNeedsAFullWindowOfZeroDays() {
        assertTrue(LifeFlame.isFading(listOf(0, 0, 0)))
        assertTrue(LifeFlame.isFading(listOf(0, 0, 0, 50))) // only the window matters
        assertFalse(LifeFlame.isFading(listOf(0, 10, 0)))   // a good day in the window
        assertFalse(LifeFlame.isFading(listOf(0, 0)))       // not enough days yet
        assertFalse(LifeFlame.isFading(emptyList()))
    }
}
