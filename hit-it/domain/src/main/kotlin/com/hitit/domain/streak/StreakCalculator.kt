package com.hitit.domain.streak

import com.hitit.domain.model.HitDay
import com.hitit.domain.model.RepCore
import com.hitit.domain.model.ScheduleEvaluator
import com.hitit.domain.model.ScheduleType
import com.hitit.domain.model.StreakResult
import java.time.LocalDate
import java.time.temporal.WeekFields

/**
 * Computes current and longest streaks for a Rep.
 *
 * Rules (see BLUEPRINT.md for worked examples):
 *  - Day-based schedules (DAILY/WEEKDAYS/CUSTOM): a streak is a run of "met" scheduled days.
 *    Non-scheduled days are invisible. A day is "met" when its hit count reaches the target.
 *  - Rest days (skip protection): up to [RepCore.restDaysAllowed] consecutive missed scheduled
 *    days are bridged per gap; the budget refills after every met day. A gap larger than the
 *    budget breaks the streak. Bridged misses do NOT add to the count.
 *  - Rest mode (vacation): scheduled days inside the window are removed entirely (never count,
 *    never break, never consume budget).
 *  - "Today" (or the current ISO week for WEEKLY), if scheduled but not yet met, is *pending* —
 *    neutral, so a streak isn't reported broken just because today isn't done yet.
 *  - Weekly schedules use a week-based streak: a week is met when its total hits reach the
 *    weekly target; the streak is consecutive met ISO weeks.
 *  - Future-dated hits and scheduled days before the Rep's creation are ignored.
 */
class StreakCalculator {

    fun calculate(rep: RepCore, hits: List<HitDay>, today: LocalDate): StreakResult {
        if (rep.createdDate.isAfter(today)) {
            return StreakResult(currentStreak = 0, longestStreak = 0, isResting = false, isPending = false)
        }
        return when (rep.scheduleType) {
            ScheduleType.WEEKLY -> calculateWeekly(rep, hits, today)
            else -> calculateDaily(rep, hits, today)
        }
    }

    // --- Day-based (DAILY / WEEKDAYS / CUSTOM) ---

    private fun calculateDaily(rep: RepCore, hits: List<HitDay>, today: LocalDate): StreakResult {
        val counts: Map<LocalDate, Int> = hits
            .asSequence()
            .filter { !it.date.isAfter(today) } // ignore future-dated hits
            .groupBy { it.date }
            .mapValues { (_, dayHits) -> dayHits.sumOf { it.hitCount } }

        val restEnd = rep.restModeEnd ?: rep.restModeStart?.let { today } // ongoing rest mode

        fun scheduled(d: LocalDate): Boolean = ScheduleEvaluator.isActiveOn(rep, d)

        fun resting(d: LocalDate): Boolean {
            val start = rep.restModeStart ?: return false
            val end = restEnd ?: return false
            return !d.isBefore(start) && !d.isAfter(end)
        }

        fun met(d: LocalDate): Boolean = (counts[d] ?: 0) >= rep.targetCount

        // Eligible (scheduled, non-rest) days from createdDate..today in chronological order.
        // A pending "today" is excluded and tracked separately so it stays neutral.
        val eligible = ArrayList<Boolean>()
        var headPending = false
        var d = rep.createdDate
        while (!d.isAfter(today)) {
            if (scheduled(d) && !resting(d)) {
                if (d.isEqual(today) && !met(d)) {
                    headPending = true
                } else {
                    eligible.add(met(d))
                }
            }
            d = d.plusDays(1)
        }

        val runs = computeRuns(eligible, rep.restDaysAllowed)
        return StreakResult(
            currentStreak = runs.current,
            longestStreak = runs.longest,
            isResting = resting(today),
            isPending = headPending,
        )
    }

    // --- Week-based (WEEKLY) ---

    private fun calculateWeekly(rep: RepCore, hits: List<HitDay>, today: LocalDate): StreakResult {
        val iso = WeekFields.ISO

        fun weekKey(date: LocalDate): Long {
            val year = date.get(iso.weekBasedYear())
            val week = date.get(iso.weekOfWeekBasedYear())
            return year * 100L + week
        }

        fun mondayOf(date: LocalDate): LocalDate = date.with(iso.dayOfWeek(), 1L)

        val weekTotals = HashMap<Long, Int>()
        for (h in hits) {
            if (h.date.isAfter(today) || h.date.isBefore(rep.createdDate)) continue
            val key = weekKey(h.date)
            weekTotals[key] = (weekTotals[key] ?: 0) + h.hitCount
        }

        val restStart = rep.restModeStart
        val restEnd = rep.restModeEnd ?: restStart?.let { today }

        fun weekFullyRest(monday: LocalDate): Boolean {
            if (restStart == null || restEnd == null) return false
            val sunday = monday.plusDays(6)
            return !monday.isBefore(restStart) && !sunday.isAfter(restEnd)
        }

        val lastMonday = mondayOf(today)
        val eligible = ArrayList<Boolean>()
        var headPending = false
        var week = mondayOf(rep.createdDate)
        while (!week.isAfter(lastMonday)) {
            if (!weekFullyRest(week)) {
                val met = (weekTotals[weekKey(week)] ?: 0) >= rep.weeklyTarget
                if (week.isEqual(lastMonday) && !met) {
                    headPending = true // current partial week not yet met
                } else {
                    eligible.add(met)
                }
            }
            week = week.plusWeeks(1)
        }

        val isResting = restStart != null && restEnd != null &&
            !today.isBefore(restStart) && !today.isAfter(restEnd)

        val runs = computeRuns(eligible, rep.restDaysAllowed)
        return StreakResult(
            currentStreak = runs.current,
            longestStreak = runs.longest,
            isResting = isResting,
            isPending = headPending,
        )
    }

    // --- Shared run computation over eligible "met" flags (chronological ascending) ---

    private data class Runs(val current: Int, val longest: Int)

    private fun computeRuns(metFlags: List<Boolean>, budget: Int): Runs {
        // Longest run over all history, bridging gaps of <= budget consecutive misses.
        var longest = 0
        var run = 0
        var miss = 0
        for (met in metFlags) {
            if (met) {
                run++
                miss = 0
                if (run > longest) longest = run
            } else {
                miss++
                if (miss > budget) {
                    run = 0
                    miss = 0
                }
            }
        }

        // Current run: walk backward from the most recent eligible unit.
        var current = 0
        var backMiss = 0
        for (i in metFlags.indices.reversed()) {
            if (metFlags[i]) {
                current++
                backMiss = 0
            } else {
                backMiss++
                if (backMiss > budget) break
            }
        }

        return Runs(current = current, longest = longest)
    }
}
