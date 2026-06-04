package com.hitit.domain.coach

import com.hitit.domain.ledger.DayRecord
import kotlin.math.roundToInt

/**
 * A finalized day plus the weekday it fell on (1=Mon..7=Sun), so the coach can find
 * day-of-week patterns. Pure data — no Android, no java.time leakage into the rules.
 */
data class CoachDay(
    val isoDayOfWeek: Int,
    val isWeekend: Boolean,
    val record: DayRecord,
)

/** Severity colors the tone of a line ("you bled momentum" vs "solid week"). */
enum class InsightTone { POSITIVE, NEUTRAL, WARNING, CRITICAL }

data class CoachInsight(val tone: InsightTone, val headline: String, val detail: String)

/**
 * The rule-based "brutally honest coach": pure analysis over a window of finalized days. Produces a
 * structured weekly review and a single most-relevant daily nudge. It never sugar-coats, but always
 * ends actionable. This is both the always-on coach AND the structured brain an on-device LLM can
 * later rephrase generatively (Phase I) — the LLM consumes these `CoachInsight`s, it doesn't invent.
 */
object CoachEngine {

    /** A full weekly review: an overall verdict line plus specific findings. */
    fun weeklyReview(days: List<CoachDay>): List<CoachInsight> {
        if (days.isEmpty()) {
            return listOf(
                CoachInsight(
                    InsightTone.NEUTRAL,
                    "No history yet",
                    "Log a few days and I'll start calling out your patterns.",
                ),
            )
        }
        val insights = ArrayList<CoachInsight>()
        insights += verdict(days)
        consistency(days)?.let { insights += it }
        weekdayVsWeekend(days)?.let { insights += it }
        checkInGap(days)?.let { insights += it }
        debtTrend(days)?.let { insights += it }
        bestPillar(days)?.let { insights += it }
        return insights
    }

    /** The single highest-priority nudge for right now, given recent days. */
    fun dailyNudge(days: List<CoachDay>): CoachInsight {
        if (days.isEmpty()) {
            return CoachInsight(InsightTone.NEUTRAL, "Fresh start", "Log your first rep to light the flame.")
        }
        // Prioritize: active debt > a fading streak of low days > weak pillar > encouragement.
        val recentDebt = days.takeLast(3).sumOf { it.record.let { r -> debtProxy(r) } }
        if (recentDebt > 0) {
            return CoachInsight(
                InsightTone.WARNING,
                "You owe momentum",
                "The last few days slipped. Clear every rep today before you do anything else.",
            )
        }
        val avg = days.takeLast(3).map { it.record.momentumScore }.average()
        if (avg < 45) {
            return CoachInsight(
                InsightTone.CRITICAL,
                "Your flame is dying",
                "Three flat days in a row. One honest rep right now turns it around.",
            )
        }
        if (avg < 70) {
            return CoachInsight(
                InsightTone.NEUTRAL,
                "Push for a strong day",
                "You're coasting. Hit every scheduled rep and you'll feel the difference.",
            )
        }
        return CoachInsight(InsightTone.POSITIVE, "Keep the streak alive", "You're rolling — don't break the chain today.")
    }

    // --- individual rules ---

    private fun verdict(days: List<CoachDay>): CoachInsight {
        val avg = days.map { it.record.momentumScore }.average().roundToInt()
        return when {
            avg >= 80 -> CoachInsight(InsightTone.POSITIVE, "Strong week — $avg avg", "You showed up. This is what momentum looks like.")
            avg >= 60 -> CoachInsight(InsightTone.NEUTRAL, "Decent week — $avg avg", "Solid, not spectacular. You left points on the table.")
            avg >= 40 -> CoachInsight(InsightTone.WARNING, "Shaky week — $avg avg", "You're bleeding momentum. Tighten up the misses.")
            else -> CoachInsight(InsightTone.CRITICAL, "Rough week — $avg avg", "Be honest: you barely showed up. Reset starts now.")
        }
    }

    private fun consistency(days: List<CoachDay>): CoachInsight? {
        val met = days.count { it.record.repsScheduled > 0 && it.record.repsMet >= it.record.repsScheduled }
        val total = days.count { it.record.repsScheduled > 0 }
        if (total == 0) return null
        val pct = (met * 100) / total
        return when {
            pct >= 85 -> CoachInsight(InsightTone.POSITIVE, "$pct% perfect days", "Your consistency is your superpower right now.")
            pct >= 50 -> CoachInsight(InsightTone.NEUTRAL, "$pct% perfect days", "Half-measures. Aim to finish every scheduled rep.")
            else -> CoachInsight(InsightTone.WARNING, "Only $pct% perfect days", "You start days you don't finish. Fix the follow-through.")
        }
    }

    private fun weekdayVsWeekend(days: List<CoachDay>): CoachInsight? {
        val weekday = days.filter { !it.isWeekend }.map { it.record.momentumScore }
        val weekend = days.filter { it.isWeekend }.map { it.record.momentumScore }
        if (weekday.isEmpty() || weekend.isEmpty()) return null
        val wd = weekday.average()
        val we = weekend.average()
        val gap = (wd - we).roundToInt()
        return when {
            gap >= 20 -> CoachInsight(InsightTone.WARNING, "Weekends wreck you", "You drop ~$gap points on weekends. Protect at least one anchor rep.")
            gap <= -20 -> CoachInsight(InsightTone.WARNING, "Weekdays are your weak spot", "Work days cost you ~${-gap} points. Front-load your reps before 10am.")
            else -> null
        }
    }

    private fun checkInGap(days: List<CoachDay>): CoachInsight? {
        val withReps = days.count { it.record.repsScheduled > 0 }
        val checkedIn = days.count { it.record.checkedIn }
        if (withReps == 0) return null
        return if (checkedIn * 2 < days.size) {
            CoachInsight(InsightTone.NEUTRAL, "You skip Check-Ins", "Reflection is free momentum and you're leaving it. 30 seconds a day.")
        } else {
            null
        }
    }

    private fun debtTrend(days: List<CoachDay>): CoachInsight? {
        val debtDays = days.count { debtProxy(it.record) > 0 }
        if (debtDays == 0) return CoachInsight(InsightTone.POSITIVE, "Zero debt", "You owe nothing. Stay square.")
        return if (debtDays >= 3) {
            CoachInsight(InsightTone.CRITICAL, "$debtDays days in debt", "This compounds. Reconcile before it becomes your normal.")
        } else {
            CoachInsight(InsightTone.WARNING, "$debtDays day(s) in debt", "Pay it back quickly — momentum debt is heavier than it looks.")
        }
    }

    private fun bestPillar(days: List<CoachDay>): CoachInsight? {
        val focus = days.sumOf { it.record.focusMinutes }
        val hits = days.sumOf { it.record.hitsCompleted }
        return when {
            focus >= 180 -> CoachInsight(InsightTone.POSITIVE, "Deep work is your edge", "${focus}m of focus this week. Lean into it.")
            hits >= 10 -> CoachInsight(InsightTone.POSITIVE, "You close tasks", "$hits Hits done. Your execution is real.")
            else -> null
        }
    }

    /** A coarse "did this day cost momentum" proxy used by nudges (mirrors the debt engine's spirit). */
    private fun debtProxy(r: DayRecord): Int {
        var d = 0
        if (r.momentumScore < 50) d += 1
        if (r.repsScheduled > 0 && r.repsMet < r.repsScheduled) d += 1
        return d
    }
}
