package com.hitit.domain.reminder

import java.time.Duration
import java.time.LocalDateTime

/** Pure scheduling math for daily reminders, so the Android scheduler stays thin and testable. */
object ReminderSchedule {

    /**
     * Millis from [now] until the next occurrence of [hour]:[minute] (local time). If that time has
     * already passed (or is exactly now) today, the next occurrence is tomorrow.
     */
    fun millisUntilNext(now: LocalDateTime, hour: Int, minute: Int): Long {
        val h = hour.coerceIn(0, 23)
        val m = minute.coerceIn(0, 59)
        var next = now.toLocalDate().atTime(h, m)
        if (!next.isAfter(now)) next = next.plusDays(1)
        return Duration.between(now, next).toMillis()
    }
}
