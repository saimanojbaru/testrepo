package com.hitit.domain.streak

import com.hitit.domain.model.HitDay
import com.hitit.domain.model.RepCore
import com.hitit.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class WeeklyStreakTest {

    private val calc = StreakCalculator()
    private val today = LocalDate.of(2026, 5, 29) // Friday, inside ISO week Mon 25 – Sun 31

    private fun weekly(weeklyTarget: Int, skipWeeks: Int, createdDate: LocalDate) = RepCore(
        scheduleType = ScheduleType.WEEKLY,
        weeklyTarget = weeklyTarget,
        restDaysAllowed = skipWeeks, // reused as the skip-week budget for weekly schedules
        createdDate = createdDate,
    )

    private fun hit(month: Int, day: Int, count: Int = 1) = HitDay(LocalDate.of(2026, month, day), count)

    @Test
    fun consecutiveMetWeeks_currentWeekPending() {
        // Weeks: W1 May4-10, W2 May11-17, W3 May18-24 (each met), W4 (current) May25-31 not met.
        val rep = weekly(weeklyTarget = 3, skipWeeks = 0, createdDate = LocalDate.of(2026, 5, 4))
        val hits = listOf(
            hit(5, 4), hit(5, 5), hit(5, 6),       // W1 = 3
            hit(5, 11), hit(5, 12), hit(5, 13),    // W2 = 3
            hit(5, 18), hit(5, 19), hit(5, 20),    // W3 = 3
            hit(5, 25),                            // W4 current = 1 (< target) -> pending
        )
        val r = calc.calculate(rep, hits, today)
        assertEquals(3, r.currentStreak)
        assertEquals(3, r.longestStreak)
        assertTrue(r.isPending)
    }

    @Test
    fun skipWeekBudgetBridgesMissedWeek() {
        // target 1/week, 1 skip-week allowed. W1 met, W2 missed, W3 met, W4 current met.
        val rep = weekly(weeklyTarget = 1, skipWeeks = 1, createdDate = LocalDate.of(2026, 5, 4))
        val hits = listOf(
            hit(5, 4),   // W1
            // W2 missed
            hit(5, 18),  // W3
            hit(5, 25),  // W4 current -> met, not pending
        )
        val r = calc.calculate(rep, hits, today)
        assertEquals(3, r.currentStreak)
        assertFalse(r.isPending)
    }

    @Test
    fun missedWeekExceedingBudgetBreaks() {
        // Same as above but 0 skip-weeks allowed -> the missed W2 breaks the chain.
        val rep = weekly(weeklyTarget = 1, skipWeeks = 0, createdDate = LocalDate.of(2026, 5, 4))
        val hits = listOf(
            hit(5, 4),   // W1
            // W2 missed
            hit(5, 18),  // W3
            hit(5, 25),  // W4 current met
        )
        val r = calc.calculate(rep, hits, today)
        assertEquals(2, r.currentStreak) // W4 + W3 only
    }
}
