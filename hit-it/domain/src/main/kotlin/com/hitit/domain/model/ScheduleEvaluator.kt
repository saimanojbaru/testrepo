package com.hitit.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/** Shared schedule logic used by both the streak engine and the UI. */
object ScheduleEvaluator {

    /**
     * Whether a Rep is "active" (expected to be acted on) on [date].
     * WEEKLY reps are active every day since the target can be hit on any day of the week.
     */
    fun isActiveOn(rep: RepCore, date: LocalDate): Boolean = when (rep.scheduleType) {
        ScheduleType.DAILY -> true
        ScheduleType.WEEKDAYS -> date.dayOfWeek != DayOfWeek.SATURDAY && date.dayOfWeek != DayOfWeek.SUNDAY
        ScheduleType.CUSTOM -> rep.customDays.contains(date.dayOfWeek)
        ScheduleType.WEEKLY -> true
    }
}
