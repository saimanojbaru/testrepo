package com.hitit.app.data.mapper

import com.hitit.app.data.local.entity.RepEntity
import com.hitit.app.data.local.entity.RepHitEntity
import com.hitit.domain.model.HitDay
import com.hitit.domain.model.RepCore
import com.hitit.domain.model.ScheduleType
import java.time.DayOfWeek

/** Parse a CSV of ISO day numbers ("1,3,5") into DayOfWeek values. */
fun parseCustomDays(csv: String): Set<DayOfWeek> =
    csv.split(",")
        .mapNotNull { it.trim().toIntOrNull() }
        .mapNotNull { runCatching { DayOfWeek.of(it) }.getOrNull() }
        .toSet()

/** Serialize selected days back to a CSV of ISO day numbers. */
fun formatCustomDays(days: Set<DayOfWeek>): String =
    days.sortedBy { it.value }.joinToString(",") { it.value.toString() }

fun RepEntity.toCore(): RepCore = RepCore(
    scheduleType = runCatching { ScheduleType.valueOf(scheduleType) }.getOrDefault(ScheduleType.DAILY),
    customDays = parseCustomDays(customDaysCsv),
    weeklyTarget = weeklyTarget.coerceAtLeast(1),
    targetCount = targetCount.coerceAtLeast(1),
    restDaysAllowed = restDaysAllowed.coerceAtLeast(0),
    restModeStart = restModeStart,
    restModeEnd = restModeEnd,
    createdDate = createdDate,
)

fun RepHitEntity.toHitDay(): HitDay = HitDay(date = date, hitCount = hitCount)
