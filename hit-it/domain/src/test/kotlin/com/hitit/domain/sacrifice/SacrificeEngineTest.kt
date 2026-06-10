package com.hitit.domain.sacrifice

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SacrificeEngineTest {

    @Test
    fun eligibilityNeedsLongStreakAndUnusedMonth() {
        assertTrue(SacrificeEngine.isEligible(10, "2026-06", null))
        assertTrue(SacrificeEngine.isEligible(31, "2026-06", "2026-05"))
        assertFalse(SacrificeEngine.isEligible(9, "2026-06", null))
        assertFalse(SacrificeEngine.isEligible(31, "2026-06", "2026-06")) // already used this month
    }

    @Test
    fun payoutScalesAndCaps() {
        assertEquals(250L, SacrificeEngine.offerFor(10).xp)
        assertEquals(750L, SacrificeEngine.offerFor(30).xp)
        assertEquals(1500L, SacrificeEngine.offerFor(60).xp)   // 60*25=1500 exactly at cap
        assertEquals(1500L, SacrificeEngine.offerFor(100).xp)  // capped
    }

    @Test
    fun relicTiersByStreakLength() {
        assertEquals("relic_ember", SacrificeEngine.offerFor(12).relicId)
        assertEquals("relic_blade", SacrificeEngine.offerFor(20).relicId)
        assertEquals("relic_eclipse", SacrificeEngine.offerFor(30).relicId)
    }

    @Test
    fun monthKeyFormatsPadded() {
        assertEquals("2026-06", SacrificeEngine.monthKey(2026, 6))
        assertEquals("2026-11", SacrificeEngine.monthKey(2026, 11))
    }

    @Test
    fun ritualCopyStatesTheDealAndTheNoRefund() {
        val offer = SacrificeEngine.offerFor(14)
        val copy = SacrificeEngine.ritualCopy(14, offer)
        assertTrue(copy.contains("14-day"))
        assertTrue(copy.contains("+${offer.xp}"))
        assertTrue(copy.contains("does not refund"))
    }
}
