package com.hitit.domain.ledger

/** The finalized facts of a single past day — the immutable record the coach and debt engine read. */
data class DayRecord(
    val momentumScore: Int,
    val repsScheduled: Int,
    val repsMet: Int,
    val hitsCompleted: Int,
    val focusMinutes: Int,
    val checkedIn: Boolean,
    val suddenDeathMissed: Boolean = false,
)

/** The debt owed for an underperforming day plus whether it forces a reconciliation. */
data class DebtResult(val debt: Int, val reconciliationDue: Boolean)

/**
 * The Gravity / Momentum-Debt engine: turns an underperforming finalized day into a debt the user
 * must reconcile. Pure and deterministic (no Android, no clock) — the "brutally honest ledger".
 *
 * Debt is intentionally asymmetric with reward: showing up is cheap, but a wasted day costs you.
 */
object MomentumDebtEngine {
    const val MAX_DEBT = 100
    const val LOW_SCORE_DEBT = 30        // score < 50
    const val MID_SCORE_DEBT = 15        // score < 70
    const val PER_MISSED_REP_DEBT = 20
    const val SUDDEN_DEATH_DEBT = 40
    const val RECONCILIATION_THRESHOLD = 25

    fun calculate(record: DayRecord): DebtResult {
        var debt = 0

        debt += when {
            record.momentumScore < 50 -> LOW_SCORE_DEBT
            record.momentumScore < 70 -> MID_SCORE_DEBT
            else -> 0
        }

        val missedReps = (record.repsScheduled - record.repsMet).coerceAtLeast(0)
        debt += missedReps * PER_MISSED_REP_DEBT

        if (record.suddenDeathMissed) debt += SUDDEN_DEATH_DEBT

        debt = debt.coerceIn(0, MAX_DEBT)
        return DebtResult(debt = debt, reconciliationDue = debt > RECONCILIATION_THRESHOLD)
    }

    /** A concrete, high-effort "pay it back" suggestion sized to the debt. */
    fun reconciliationSuggestion(debt: Int): String = when {
        debt <= 0 -> "You're square. Keep the flame fed."
        debt > 60 -> "Heavy debt. Reconcile: a 60-min Lock In + finish every rep today."
        debt > 35 -> "Reconcile: a 45-min focused session on your weakest rep today."
        else -> "Reconcile: clear all of today's reps plus one bonus Hit."
    }
}
