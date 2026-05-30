package com.hitit.domain.lockin

import org.junit.Assert.assertEquals
import org.junit.Test

class LockInClockTest {

    private val total = 25 * 60_000L // 25 minutes

    @Test
    fun formatRoundsUpAtStart() {
        assertEquals("25:00", LockInClock.format(total))
    }

    @Test
    fun formatShowsSecondsAndZero() {
        assertEquals("0:01", LockInClock.format(1L))
        assertEquals("0:00", LockInClock.format(0L))
        assertEquals("1:30", LockInClock.format(90_000L))
    }

    @Test
    fun progressIsFractionElapsed() {
        assertEquals(0f, LockInClock.progress(total, total), 0.0001f)
        assertEquals(1f, LockInClock.progress(0L, total), 0.0001f)
        assertEquals(0.5f, LockInClock.progress(total / 2, total), 0.0001f)
    }

    @Test
    fun progressIsZeroForZeroTotal() {
        assertEquals(0f, LockInClock.progress(0L, 0L), 0.0001f)
    }

    @Test
    fun focusedMinutesCountsWholeMinutes() {
        assertEquals(0, LockInClock.focusedMinutes(total, total))
        assertEquals(25, LockInClock.focusedMinutes(total, 0L))
        assertEquals(10, LockInClock.focusedMinutes(total, 15 * 60_000L))
    }
}
