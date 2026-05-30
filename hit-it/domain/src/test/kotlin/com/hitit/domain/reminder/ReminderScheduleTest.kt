package com.hitit.domain.reminder

import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Duration
import java.time.LocalDateTime

class ReminderScheduleTest {

    @Test
    fun laterToday() {
        val now = LocalDateTime.of(2026, 5, 29, 8, 0)
        val millis = ReminderSchedule.millisUntilNext(now, 9, 0)
        assertEquals(Duration.ofHours(1).toMillis(), millis)
    }

    @Test
    fun alreadyPassedRollsToTomorrow() {
        val now = LocalDateTime.of(2026, 5, 29, 10, 0)
        val millis = ReminderSchedule.millisUntilNext(now, 9, 0)
        assertEquals(Duration.ofHours(23).toMillis(), millis)
    }

    @Test
    fun exactlyNowRollsToTomorrow() {
        val now = LocalDateTime.of(2026, 5, 29, 9, 0)
        val millis = ReminderSchedule.millisUntilNext(now, 9, 0)
        assertEquals(Duration.ofDays(1).toMillis(), millis)
    }

    @Test
    fun clampsOutOfRangeTime() {
        val now = LocalDateTime.of(2026, 5, 29, 8, 0)
        // hour 25 -> 23, minute 70 -> 59 => 23:59 today
        val millis = ReminderSchedule.millisUntilNext(now, 25, 70)
        val expected = Duration.between(now, now.toLocalDate().atTime(23, 59)).toMillis()
        assertEquals(expected, millis)
    }
}
