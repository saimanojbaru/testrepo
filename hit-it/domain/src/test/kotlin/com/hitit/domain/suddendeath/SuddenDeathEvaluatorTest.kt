package com.hitit.domain.suddendeath

import com.hitit.domain.model.HitDay
import com.hitit.domain.model.RepCore
import com.hitit.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SuddenDeathEvaluatorTest {

    private val today = LocalDate.of(2026, 5, 29) // Friday

    private fun daily(createdDaysAgo: Int) = RepCore(
        scheduleType = ScheduleType.DAILY,
        restDaysAllowed = 0,
        createdDate = today.minusDays(createdDaysAgo.toLong()),
    )

    private fun hitsOn(vararg daysAgo: Int): List<HitDay> =
        daysAgo.map { HitDay(today.minusDays(it.toLong()), 1) }

    @Test
    fun reportsMissedScheduledDaysBeforeToday() {
        // created 3 days ago; hit t3 and t1; missed t2. Today (t0) is pending, not a miss.
        val misses = SuddenDeathEvaluator.newMisses(daily(3), hitsOn(3, 1), emptySet(), today)
        assertEquals(listOf(today.minusDays(2)), misses)
    }

    @Test
    fun todayIsNeverAMiss() {
        // nothing logged at all; today must not appear (still pending).
        val misses = SuddenDeathEvaluator.newMisses(daily(0), emptyList(), emptySet(), today)
        assertTrue(misses.none { it == today })
    }

    @Test
    fun alreadyPenalizedDaysAreExcluded() {
        val missedDay = today.minusDays(2)
        val misses = SuddenDeathEvaluator.newMisses(daily(3), hitsOn(3, 1), setOf(missedDay), today)
        assertTrue(misses.isEmpty())
    }

    @Test
    fun restModeDaysAreNotMisses() {
        val rep = daily(3).copy(restModeStart = today.minusDays(2), restModeEnd = today.minusDays(2))
        val misses = SuddenDeathEvaluator.newMisses(rep, hitsOn(3, 1), emptySet(), today)
        assertTrue(misses.isEmpty())
    }

    @Test
    fun weeklyRepsAreUnsupported() {
        val rep = RepCore(scheduleType = ScheduleType.WEEKLY, weeklyTarget = 3, createdDate = today.minusDays(20))
        assertTrue(SuddenDeathEvaluator.newMisses(rep, emptyList(), emptySet(), today).isEmpty())
    }

    @Test
    fun weekdaysScheduleIgnoresWeekend() {
        // Rep created Mon 25; today Fri 29. Hit Mon/Tue/Wed, missed Thu 28. Weekend irrelevant.
        val rep = RepCore(
            scheduleType = ScheduleType.WEEKDAYS,
            restDaysAllowed = 0,
            createdDate = LocalDate.of(2026, 5, 25),
        )
        val hits = listOf(25, 26, 27).map { HitDay(LocalDate.of(2026, 5, it), 1) }
        val misses = SuddenDeathEvaluator.newMisses(rep, hits, emptySet(), today)
        assertEquals(listOf(LocalDate.of(2026, 5, 28)), misses)
    }
}
