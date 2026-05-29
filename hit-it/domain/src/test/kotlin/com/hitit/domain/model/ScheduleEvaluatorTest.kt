package com.hitit.domain.model

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate

class ScheduleEvaluatorTest {

    private fun rep(type: ScheduleType, custom: Set<DayOfWeek> = emptySet()) = RepCore(
        scheduleType = type,
        customDays = custom,
        createdDate = LocalDate.of(2026, 1, 1),
    )

    private val friday = LocalDate.of(2026, 5, 29)
    private val saturday = LocalDate.of(2026, 5, 30)

    @Test
    fun dailyAlwaysActive() {
        assertTrue(ScheduleEvaluator.isActiveOn(rep(ScheduleType.DAILY), saturday))
    }

    @Test
    fun weekdaysExcludeWeekend() {
        assertTrue(ScheduleEvaluator.isActiveOn(rep(ScheduleType.WEEKDAYS), friday))
        assertFalse(ScheduleEvaluator.isActiveOn(rep(ScheduleType.WEEKDAYS), saturday))
    }

    @Test
    fun customMatchesSelectedDays() {
        val r = rep(ScheduleType.CUSTOM, setOf(DayOfWeek.FRIDAY))
        assertTrue(ScheduleEvaluator.isActiveOn(r, friday))
        assertFalse(ScheduleEvaluator.isActiveOn(r, saturday))
    }

    @Test
    fun weeklyActiveEveryDay() {
        assertTrue(ScheduleEvaluator.isActiveOn(rep(ScheduleType.WEEKLY), saturday))
    }
}
