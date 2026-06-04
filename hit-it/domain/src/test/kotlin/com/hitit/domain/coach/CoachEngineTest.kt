package com.hitit.domain.coach

import com.hitit.domain.ledger.DayRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CoachEngineTest {

    private fun day(
        iso: Int,
        score: Int,
        scheduled: Int = 3,
        met: Int = 3,
        checkedIn: Boolean = true,
        focus: Int = 0,
        hits: Int = 0,
    ) = CoachDay(
        isoDayOfWeek = iso,
        isWeekend = iso >= 6,
        record = DayRecord(
            momentumScore = score,
            repsScheduled = scheduled,
            repsMet = met,
            hitsCompleted = hits,
            focusMinutes = focus,
            checkedIn = checkedIn,
        ),
    )

    @Test
    fun emptyHistoryGivesAGentleStart() {
        val review = CoachEngine.weeklyReview(emptyList())
        assertEquals(1, review.size)
        assertEquals(InsightTone.NEUTRAL, review.first().tone)
    }

    @Test
    fun strongWeekVerdictIsPositive() {
        val days = (1..7).map { day(iso = it, score = 85) }
        val verdict = CoachEngine.weeklyReview(days).first()
        assertEquals(InsightTone.POSITIVE, verdict.tone)
        assertTrue(verdict.headline.contains("Strong"))
    }

    @Test
    fun roughWeekVerdictIsCritical() {
        val days = (1..7).map { day(iso = it, score = 20, met = 0) }
        val verdict = CoachEngine.weeklyReview(days).first()
        assertEquals(InsightTone.CRITICAL, verdict.tone)
    }

    @Test
    fun detectsWeekendCollapse() {
        // Weekdays strong, weekends terrible.
        val days = (1..5).map { day(iso = it, score = 90) } +
            listOf(day(iso = 6, score = 30, met = 0), day(iso = 7, score = 25, met = 0))
        val review = CoachEngine.weeklyReview(days)
        assertTrue(review.any { it.headline.contains("Weekend", ignoreCase = true) })
    }

    @Test
    fun dailyNudgeFlagsDebtFirst() {
        // Last 3 days low + missed reps => debt nudge.
        val days = listOf(
            day(iso = 1, score = 90),
            day(iso = 2, score = 30, met = 0),
            day(iso = 3, score = 35, met = 1),
            day(iso = 4, score = 40, met = 0),
        )
        val nudge = CoachEngine.dailyNudge(days)
        assertEquals(InsightTone.WARNING, nudge.tone)
        assertTrue(nudge.headline.contains("owe", ignoreCase = true))
    }

    @Test
    fun dailyNudgeEncouragesWhenStrong() {
        val days = (1..4).map { day(iso = it, score = 88) }
        val nudge = CoachEngine.dailyNudge(days)
        assertEquals(InsightTone.POSITIVE, nudge.tone)
    }

    @Test
    fun reviewAlwaysIncludesAVerdictPlusFindings() {
        val days = (1..7).map { day(iso = it, score = 65, met = 2, checkedIn = false) }
        val review = CoachEngine.weeklyReview(days)
        assertTrue(review.size >= 2) // verdict + at least one finding
    }
}
