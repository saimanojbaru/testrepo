package com.hitit.domain.identity

import com.hitit.domain.trophy.TrophyStats
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class IdentityCatalogTest {

    @Test
    fun emptyStatsUnlockNothing() {
        assertTrue(IdentityCatalog.evaluate(TrophyStats()).isEmpty())
    }

    @Test
    fun streakUnlocksConsistentBeastThenUnbreakable() {
        assertEquals(setOf("consistent_beast"), IdentityCatalog.evaluate(TrophyStats(bestStreak = 7)))
        val at30 = IdentityCatalog.evaluate(TrophyStats(bestStreak = 30))
        assertTrue(at30.containsAll(listOf("consistent_beast", "unbreakable")))
    }

    @Test
    fun aggregateThresholds() {
        val s = TrophyStats(totalFocusMinutes = 300, tasksCompleted = 50, checkInCount = 14)
        val unlocked = IdentityCatalog.evaluate(s)
        assertTrue(unlocked.containsAll(listOf("deep_thinker", "closer", "reflector")))
        assertFalse(unlocked.contains("consistent_beast"))
    }

    @Test
    fun bonusStacksAdditivelyAndCaps() {
        // consistent_beast(5) + unbreakable(10) + deep_thinker(5) = 20
        val ids = setOf("consistent_beast", "unbreakable", "deep_thinker")
        assertEquals(20, IdentityCatalog.totalBonusPercent(ids))
        // All five: 5+5+5+5+10 = 30, at the cap.
        assertEquals(IdentityCatalog.MAX_BONUS_PERCENT, IdentityCatalog.totalBonusPercent(IdentityCatalog.ALL.map { it.id }.toSet()))
        assertEquals(0, IdentityCatalog.totalBonusPercent(emptySet()))
        assertEquals(0, IdentityCatalog.totalBonusPercent(setOf("nope")))
    }

    @Test
    fun idsAreUnique() {
        assertEquals(IdentityCatalog.ALL.size, IdentityCatalog.ALL.map { it.id }.toSet().size)
    }
}
