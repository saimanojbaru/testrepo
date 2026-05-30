package com.hitit.domain.trophy

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrophyCatalogTest {

    @Test
    fun emptyStatsUnlockNothing() {
        assertTrue(TrophyCatalog.evaluate(TrophyStats()).isEmpty())
    }

    @Test
    fun firstMomentumUnlocksFirstStep() {
        val unlocked = TrophyCatalog.evaluate(TrophyStats(momentum = 1, level = 1))
        assertEquals(setOf("first_step"), unlocked)
    }

    @Test
    fun levelGatesAreInclusive() {
        val unlocked = TrophyCatalog.evaluate(TrophyStats(momentum = 100, level = 51))
        assertTrue(unlocked.containsAll(listOf("warmed_up", "contender", "all_star")))
        assertFalse(unlocked.contains("champion"))
    }

    @Test
    fun maxLevelUnlocksAllLevelTrophies() {
        val unlocked = TrophyCatalog.evaluate(TrophyStats(momentum = 999_999, level = 100))
        assertTrue(unlocked.containsAll(listOf("warmed_up", "contender", "all_star", "champion", "goat")))
    }

    @Test
    fun focusAndStreakAndGoalTrophies() {
        val stats = TrophyStats(
            momentum = 5,
            level = 2,
            totalFocusMinutes = 600,
            checkInCount = 7,
            tasksCompleted = 25,
            bigPlaysCompleted = 1,
            bestStreak = 30,
        )
        val unlocked = TrophyCatalog.evaluate(stats)
        assertTrue(
            unlocked.containsAll(
                listOf("deep_worker", "marathon", "reflector", "closer", "visionary", "streak_master"),
            ),
        )
    }

    @Test
    fun idsAreUniqueAndLookupWorks() {
        assertEquals(TrophyCatalog.ALL.size, TrophyCatalog.ALL.map { it.id }.toSet().size)
        assertEquals("G.O.A.T.", TrophyCatalog.byId("goat")?.name)
        assertEquals(null, TrophyCatalog.byId("nope"))
    }
}
