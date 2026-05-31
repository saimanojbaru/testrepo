package com.hitit.domain.momentum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentumScoreTest {

    @Test
    fun perfectAcrossAllPillarsIs100() {
        val s = MomentumScore.score(
            repsScheduled = 4, repsMet = 4, checkedIn = true,
            hitsCompletedToday = 3, focusMinutesToday = 60,
        )
        assertEquals(100, s)
    }

    @Test
    fun emptyDayIsZero() {
        val s = MomentumScore.score(
            repsScheduled = 4, repsMet = 0, checkedIn = false,
            hitsCompletedToday = 0, focusMinutesToday = 0,
        )
        assertEquals(0, s)
    }

    @Test
    fun noRepsScheduledDoesNotPenalizeRepsPillar() {
        val s = MomentumScore.score(
            repsScheduled = 0, repsMet = 0, checkedIn = false,
            hitsCompletedToday = 0, focusMinutesToday = 0,
        )
        assertEquals(MomentumScore.REPS_WEIGHT, s) // 50 from the "nothing scheduled" allowance
    }

    @Test
    fun partialDayAccumulatesPillars() {
        // 2/4 reps (25) + checkin (20) = 45
        val s = MomentumScore.score(
            repsScheduled = 4, repsMet = 2, checkedIn = true,
            hitsCompletedToday = 0, focusMinutesToday = 0,
        )
        assertEquals(45, s)
    }

    @Test
    fun focusAndHitsCapOut() {
        val s = MomentumScore.score(
            repsScheduled = 1, repsMet = 0, checkedIn = false,
            hitsCompletedToday = 99, focusMinutesToday = 9999,
        )
        assertEquals(MomentumScore.HITS_WEIGHT + MomentumScore.FOCUS_WEIGHT, s) // 30
    }

    @Test
    fun labelsAreOnBrand() {
        assertTrue(MomentumScore.label(95).contains("fire"))
        assertEquals("Let's go", MomentumScore.label(0))
    }
}
