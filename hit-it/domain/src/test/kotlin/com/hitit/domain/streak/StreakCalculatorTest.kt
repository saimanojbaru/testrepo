package com.hitit.domain.streak

import com.hitit.domain.model.HitDay
import com.hitit.domain.model.RepCore
import com.hitit.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class StreakCalculatorTest {

    private val calc = StreakCalculator()
    private val today = LocalDate.of(2026, 5, 29) // Friday

    private fun daily(createdDaysAgo: Int, restDays: Int = 2, target: Int = 1) = RepCore(
        scheduleType = ScheduleType.DAILY,
        targetCount = target,
        restDaysAllowed = restDays,
        createdDate = today.minusDays(createdDaysAgo.toLong()),
    )

    /** Hits, expressed as "days ago" from [today], one hit each. */
    private fun hitsOn(vararg daysAgo: Int): List<HitDay> =
        daysAgo.map { HitDay(today.minusDays(it.toLong()), 1) }

    @Test
    fun newRep_noHits_isZeroAndPending() {
        val r = calc.calculate(daily(createdDaysAgo = 0), emptyList(), today)
        assertEquals(0, r.currentStreak)
        assertEquals(0, r.longestStreak)
        assertTrue(r.isPending)
    }

    @Test
    fun perfectRun_daily() {
        val r = calc.calculate(daily(createdDaysAgo = 4), hitsOn(0, 1, 2, 3, 4), today)
        assertEquals(5, r.currentStreak)
        assertEquals(5, r.longestStreak)
        assertFalse(r.isPending)
    }

    @Test
    fun todayPending_doesNotBreakStreak() {
        // Hit the last four days but not today yet.
        val r = calc.calculate(daily(createdDaysAgo = 4), hitsOn(1, 2, 3, 4), today)
        assertEquals(4, r.currentStreak)
        assertTrue(r.isPending)
    }

    @Test
    fun restDayBridgesGapWithinBudget() {
        // met t0,t1 ; miss t2 ; met t3,t4   (budget 2)
        val r = calc.calculate(daily(createdDaysAgo = 4, restDays = 2), hitsOn(0, 1, 3, 4), today)
        assertEquals(4, r.currentStreak)
    }

    @Test
    fun gapExceedingBudgetBreaks() {
        // met t0,t1 ; miss t2,t3,t4 (3 > budget 2) ; met t5
        val r = calc.calculate(daily(createdDaysAgo = 5, restDays = 2), hitsOn(0, 1, 5), today)
        assertEquals(2, r.currentStreak)
    }

    @Test
    fun weekdays_skipsWeekendWithoutBreaking() {
        val rep = RepCore(
            scheduleType = ScheduleType.WEEKDAYS,
            restDaysAllowed = 0, // weekend must be invisible, not bridged via budget
            createdDate = LocalDate.of(2026, 5, 25), // Monday
        )
        val mondayToday = LocalDate.of(2026, 6, 1) // Monday
        val hits = listOf(25, 26, 27, 28, 29).map { HitDay(LocalDate.of(2026, 5, it), 1) } +
            HitDay(LocalDate.of(2026, 6, 1), 1)
        val r = calc.calculate(rep, hits, mondayToday)
        assertEquals(6, r.currentStreak)
        assertFalse(r.isPending)
    }

    @Test
    fun customDays_onlyCountScheduled() {
        val rep = RepCore(
            scheduleType = ScheduleType.CUSTOM,
            customDays = setOf(DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY),
            restDaysAllowed = 0,
            createdDate = LocalDate.of(2026, 5, 25), // Monday
        )
        // Scheduled in range: Mon 25, Wed 27, Fri 29 (today). Tue/Thu invisible.
        val hits = listOf(25, 27, 29).map { HitDay(LocalDate.of(2026, 5, it), 1) }
        val r = calc.calculate(rep, hits, today)
        assertEquals(3, r.currentStreak)
    }

    @Test
    fun restModeRemovesDays_noBreakEvenWithZeroBudget() {
        val rep = daily(createdDaysAgo = 5, restDays = 0).copy(
            restModeStart = today.minusDays(4),
            restModeEnd = today.minusDays(2),
        )
        // met t0,t1 ; t2,t3,t4 in rest mode (removed) ; met t5
        val r = calc.calculate(rep, hitsOn(0, 1, 5), today)
        assertEquals(3, r.currentStreak)
        assertFalse(r.isResting) // today is outside the rest window
    }

    @Test
    fun createdMidHistory_ignoresPreCreationDays() {
        val r = calc.calculate(daily(createdDaysAgo = 2), hitsOn(0, 1, 2), today)
        assertEquals(3, r.currentStreak)
        assertEquals(3, r.longestStreak)
    }

    @Test
    fun futureDatedHitsIgnored() {
        val r = calc.calculate(daily(createdDaysAgo = 0), listOf(HitDay(today.plusDays(1), 1)), today)
        assertEquals(0, r.currentStreak)
    }

    @Test
    fun multiHit_requiresTargetCount() {
        val rep = daily(createdDaysAgo = 1, target = 3)
        val hits = listOf(
            HitDay(today.minusDays(1), 3), // met
            HitDay(today, 2), // below target -> pending
        )
        val r = calc.calculate(rep, hits, today)
        assertEquals(1, r.currentStreak)
        assertTrue(r.isPending)
    }
}
