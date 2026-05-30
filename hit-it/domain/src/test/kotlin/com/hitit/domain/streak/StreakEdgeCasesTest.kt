package com.hitit.domain.streak

import com.hitit.domain.model.HitDay
import com.hitit.domain.model.RepCore
import com.hitit.domain.model.ScheduleType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** Edge cases for the streak engine beyond the happy paths in StreakCalculatorTest. */
class StreakEdgeCasesTest {

    private val calc = StreakCalculator()
    private val friday = LocalDate.of(2026, 5, 29) // Friday

    private fun daily(createdDaysAgo: Int, restDays: Int = 2, target: Int = 1) = RepCore(
        scheduleType = ScheduleType.DAILY,
        targetCount = target,
        restDaysAllowed = restDays,
        createdDate = friday.minusDays(createdDaysAgo.toLong()),
    )

    private fun metOn(vararg daysAgo: Int): List<HitDay> =
        daysAgo.map { HitDay(friday.minusDays(it.toLong()), 1) }

    @Test
    fun ongoingRestMode_todayInsideIsRestingNotPending() {
        val rep = daily(createdDaysAgo = 5, restDays = 0).copy(
            restModeStart = friday.minusDays(2),
            restModeEnd = null, // ongoing -> effectively through today
        )
        // met t5,t4,t3 ; t2,t1,today all in rest mode (removed)
        val r = calc.calculate(rep, metOn(5, 4, 3), friday)
        assertEquals(3, r.currentStreak)
        assertTrue(r.isResting)
        assertFalse(r.isPending)
    }

    @Test
    fun longestStreakSurvivesABreak() {
        // met t8..t5 (run of 4) ; miss t4,t3,t2,t1 (4 misses > budget 2 -> break) ; met today
        val r = calc.calculate(daily(createdDaysAgo = 8, restDays = 2), metOn(8, 7, 6, 5, 0), friday)
        assertEquals(1, r.currentStreak)
        assertEquals(4, r.longestStreak)
    }

    @Test
    fun bridgedGapAtBudgetBoundary_budget2Bridges() {
        // met t3 ; miss t2,t1 (2 misses == budget) ; met today -> single bridged streak of 2
        val r = calc.calculate(daily(createdDaysAgo = 3, restDays = 2), metOn(3, 0), friday)
        assertEquals(2, r.currentStreak)
        assertEquals(2, r.longestStreak)
    }

    @Test
    fun bridgedGapAtBudgetBoundary_budget1Breaks() {
        // same shape, budget 1: 2 misses > 1 -> breaks into two runs of 1
        val r = calc.calculate(daily(createdDaysAgo = 3, restDays = 1), metOn(3, 0), friday)
        assertEquals(1, r.currentStreak)
        assertEquals(1, r.longestStreak)
    }

    @Test
    fun weekdays_todayNotScheduled_streakIntactNotPending() {
        val rep = RepCore(
            scheduleType = ScheduleType.WEEKDAYS,
            restDaysAllowed = 0,
            createdDate = LocalDate.of(2026, 5, 25), // Monday
        )
        val saturday = LocalDate.of(2026, 5, 30) // not scheduled
        val hits = listOf(25, 26, 27, 28, 29).map { HitDay(LocalDate.of(2026, 5, it), 1) }
        val r = calc.calculate(rep, hits, saturday)
        assertEquals(5, r.currentStreak)
        assertFalse(r.isPending)
    }

    @Test
    fun weeklyMultiHit_singleEntryMeetsTarget() {
        val rep = RepCore(
            scheduleType = ScheduleType.WEEKLY,
            weeklyTarget = 5,
            restDaysAllowed = 0,
            createdDate = LocalDate.of(2026, 5, 4),
        )
        // One hit of count 5 in each of W1(May4-10), W2(11-17), W3(18-24); current week W4 empty.
        val hits = listOf(
            HitDay(LocalDate.of(2026, 5, 5), 5),
            HitDay(LocalDate.of(2026, 5, 12), 5),
            HitDay(LocalDate.of(2026, 5, 19), 5),
        )
        val r = calc.calculate(rep, hits, friday)
        assertEquals(3, r.currentStreak)
        assertTrue(r.isPending)
    }
}
