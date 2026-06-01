package com.hitit.domain.suddendeath

import com.hitit.domain.model.HitDay
import com.hitit.domain.model.RepCore
import com.hitit.domain.model.ScheduleEvaluator
import com.hitit.domain.model.ScheduleType
import java.time.LocalDate

/**
 * Detects Sudden Death misses for a day-based Rep. A "miss" is a scheduled, non-rest-mode day
 * strictly before today on which the Rep's target was not met. The result is the set of such dates
 * that have NOT already been penalized — making the evaluator idempotent so the daily worker can
 * run repeatedly (or catch up after the device was off) without double-charging.
 *
 * WEEKLY reps are not supported (no single "scheduled day" to fail); returns empty.
 */
object SuddenDeathEvaluator {

    /** How many days back to scan for unpenalized misses (catch-up after the device was off). */
    const val LOOKBACK_DAYS = 14L

    fun newMisses(
        rep: RepCore,
        hits: List<HitDay>,
        alreadyPenalized: Set<LocalDate>,
        today: LocalDate,
    ): List<LocalDate> {
        if (rep.scheduleType == ScheduleType.WEEKLY) return emptyList()

        val counts: Map<LocalDate, Int> = hits
            .asSequence()
            .filter { !it.date.isAfter(today) }
            .groupBy { it.date }
            .mapValues { (_, d) -> d.sumOf { it.hitCount } }

        val restStart = rep.restModeStart
        val restEnd = rep.restModeEnd ?: restStart?.let { today }
        fun resting(d: LocalDate): Boolean =
            restStart != null && restEnd != null && !d.isBefore(restStart) && !d.isAfter(restEnd)

        val start = maxOf(rep.createdDate, today.minusDays(LOOKBACK_DAYS))
        val misses = ArrayList<LocalDate>()
        var d = start
        while (d.isBefore(today)) { // strictly before today: today is still pending
            val scheduled = ScheduleEvaluator.isActiveOn(rep, d)
            val met = (counts[d] ?: 0) >= rep.targetCount
            if (scheduled && !resting(d) && !met && d !in alreadyPenalized) {
                misses.add(d)
            }
            d = d.plusDays(1)
        }
        return misses
    }
}
