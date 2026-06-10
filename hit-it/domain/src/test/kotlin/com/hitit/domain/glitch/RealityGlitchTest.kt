package com.hitit.domain.glitch

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RealityGlitchTest {

    @Test
    fun firesOnlyAtExtremeMomentum() {
        assertTrue(RealityGlitch.isEligible(90))
        assertTrue(RealityGlitch.isEligible(100))
        assertFalse(RealityGlitch.isEligible(89))
        assertFalse(RealityGlitch.isEligible(0))
    }

    @Test
    fun praiseIsDeterministicAndCycles() {
        assertEquals(RealityGlitch.praise(0), RealityGlitch.praise(RealityGlitch.praiseCount))
        assertEquals(RealityGlitch.praise(2), RealityGlitch.praise(2))
        // Negative seeds are safe.
        assertTrue(RealityGlitch.praise(-1).isNotBlank())
        // All lines reachable and distinct-ish.
        val all = (0 until RealityGlitch.praiseCount).map { RealityGlitch.praise(it) }.toSet()
        assertEquals(RealityGlitch.praiseCount, all.size)
    }
}
