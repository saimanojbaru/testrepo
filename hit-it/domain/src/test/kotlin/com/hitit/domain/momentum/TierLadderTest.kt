package com.hitit.domain.momentum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TierLadderTest {

    @Test
    fun endpointsAreRookieAndGoat() {
        assertEquals("Rookie", TierLadder.tierFor(1).name)
        assertEquals("G.O.A.T.", TierLadder.tierFor(100).name)
    }

    @Test
    fun bracketBoundaries() {
        assertEquals("Rookie", TierLadder.tierFor(8).name)
        assertEquals("Amateur", TierLadder.tierFor(9).name)
        assertEquals("Legend", TierLadder.tierFor(91).name)
        assertEquals("G.O.A.T.", TierLadder.tierFor(92).name)
    }

    @Test
    fun everyLevelMapsToAContainingTier() {
        for (level in 1..100) {
            val tier = TierLadder.tierFor(level)
            assertTrue(
                "level $level not within ${tier.name} [${tier.minLevel}, ${tier.maxLevel}]",
                level in tier.minLevel..tier.maxLevel,
            )
        }
    }

    @Test
    fun tiersAreContiguousAndCoverFullRange() {
        val sorted = TierLadder.TIERS.sortedBy { it.minLevel }
        assertEquals(1, sorted.first().minLevel)
        assertEquals(100, sorted.last().maxLevel)
        for (i in 0 until sorted.size - 1) {
            assertEquals(sorted[i].maxLevel + 1, sorted[i + 1].minLevel)
        }
    }
}
