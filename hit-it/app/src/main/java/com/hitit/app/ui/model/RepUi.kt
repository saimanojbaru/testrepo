package com.hitit.app.ui.model

import com.hitit.app.data.local.entity.RepEntity
import com.hitit.app.data.local.entity.RepHitEntity
import com.hitit.app.data.mapper.parseCustomDays
import java.time.LocalDate
import java.time.format.TextStyle
import java.time.temporal.WeekFields
import java.util.Locale

/** Pure helpers for turning a Rep + its hits into the small values the rows display. */
object RepUi {

    fun todayCount(hits: List<RepHitEntity>, today: LocalDate): Int =
        hits.firstOrNull { it.date == today }?.hitCount ?: 0

    fun weekCount(hits: List<RepHitEntity>, today: LocalDate): Int {
        val iso = WeekFields.ISO
        val year = today.get(iso.weekBasedYear())
        val week = today.get(iso.weekOfWeekBasedYear())
        return hits
            .filter {
                !it.date.isAfter(today) &&
                    it.date.get(iso.weekBasedYear()) == year &&
                    it.date.get(iso.weekOfWeekBasedYear()) == week
            }
            .sumOf { it.hitCount }
    }

    fun isWeekly(rep: RepEntity): Boolean = rep.scheduleType == "WEEKLY"

    fun progressText(rep: RepEntity, hits: List<RepHitEntity>, today: LocalDate): String =
        if (isWeekly(rep)) "${weekCount(hits, today)}/${rep.weeklyTarget} wk"
        else "${todayCount(hits, today)}/${rep.targetCount}"

    /** Whether today's toggle button should appear "done". */
    fun buttonMet(rep: RepEntity, hits: List<RepHitEntity>, today: LocalDate): Boolean =
        todayCount(hits, today) >= rep.targetCount

    fun scheduleLabel(rep: RepEntity): String = when (rep.scheduleType) {
        "WEEKDAYS" -> "Weekdays"
        "WEEKLY" -> "${rep.weeklyTarget}×/week"
        "CUSTOM" -> {
            val days = parseCustomDays(rep.customDaysCsv).sortedBy { it.value }
            if (days.isEmpty()) "Custom"
            else days.joinToString(", ") { it.getDisplayName(TextStyle.SHORT, Locale.getDefault()) }
        }
        else -> "Daily"
    }
}
