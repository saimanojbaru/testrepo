package com.hitit.domain.model

import java.time.DayOfWeek
import java.time.LocalDate

/** How often a Rep (habit) is expected to be hit. */
enum class ScheduleType {
    /** Every day. */
    DAILY,

    /** Monday–Friday. */
    WEEKDAYS,

    /** A specific set of weekdays (see [RepCore.customDays]). */
    CUSTOM,

    /** A target number of hits per ISO week (see [RepCore.weeklyTarget]). */
    WEEKLY,
}

/**
 * The streak-relevant configuration of a Rep, decoupled from persistence/Room.
 *
 * All date math is performed on [LocalDate]; callers pass `today` in explicitly so the
 * engines stay deterministic and timezone-correct (no hidden `LocalDate.now()`).
 */
data class RepCore(
    val scheduleType: ScheduleType,
    /** Used when [scheduleType] == CUSTOM. */
    val customDays: Set<DayOfWeek> = emptySet(),
    /** Required hits per ISO week when [scheduleType] == WEEKLY. */
    val weeklyTarget: Int = 1,
    /** Hits needed in a single day for a day-based schedule to count as "met" (multi-hit). */
    val targetCount: Int = 1,
    /** Skip-protection budget: consecutive misses bridged per gap before the streak breaks. */
    val restDaysAllowed: Int = 2,
    /** Inclusive start of a "rest mode" (vacation) window; `null` if none. */
    val restModeStart: LocalDate? = null,
    /** Inclusive end of the rest-mode window; `null` with a non-null start means "ongoing". */
    val restModeEnd: LocalDate? = null,
    /** Day the Rep was created; scheduled days before this are never counted as misses. */
    val createdDate: LocalDate,
)

/** Aggregated hits for a Rep on a single calendar day. */
data class HitDay(
    val date: LocalDate,
    val hitCount: Int = 1,
)

/** Result of a streak computation. */
data class StreakResult(
    /** Length of the streak currently alive (counts only "met" units). */
    val currentStreak: Int,
    /** Longest streak ever achieved in the Rep's history. */
    val longestStreak: Int,
    /** True when `today` falls inside the rest-mode window. */
    val isResting: Boolean,
    /** True when today/this-week is scheduled but not yet met (neutral, not a miss). */
    val isPending: Boolean,
)
