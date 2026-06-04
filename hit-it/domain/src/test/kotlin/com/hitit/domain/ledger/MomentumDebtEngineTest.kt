package com.hitit.domain.ledger

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentumDebtEngineTest {

    private fun day(
        score: Int = 100,
        scheduled: Int = 3,
        met: Int = 3,
        suddenDeathMissed: Boolean = false,
    ) = DayRecord(
        momentumScore = score,
        repsScheduled = scheduled,
        repsMet = met,
        hitsCompleted = 0,
        focusMinutes = 0,
        checkedIn = true,
        suddenDeathMissed = suddenDeathMissed,
    )

    @Test
    fun strongDayOwesNoDebt() {
        val r = MomentumDebtEngine.calculate(day(score = 80, scheduled = 3, met = 3))
        assertEquals(0, r.debt)
        assertFalse(r.reconciliationDue)
    }

    @Test
    fun lowScoreAddsDebt() {
        // score 40 (<50) = 30 debt; all reps met so no rep penalty.
        val r = MomentumDebtEngine.calculate(day(score = 40, scheduled = 3, met = 3))
        assertEquals(MomentumDebtEngine.LOW_SCORE_DEBT, r.debt)
        assertTrue(r.reconciliationDue) // 30 > 25
    }

    @Test
    fun midScoreSmallerDebtNoReconciliation() {
        val r = MomentumDebtEngine.calculate(day(score = 65, scheduled = 3, met = 3))
        assertEquals(MomentumDebtEngine.MID_SCORE_DEBT, r.debt) // 15
        assertFalse(r.reconciliationDue) // 15 < 25
    }

    @Test
    fun missedRepsStackDebt() {
        // score 75 (no score debt) + 2 missed reps * 20 = 40.
        val r = MomentumDebtEngine.calculate(day(score = 75, scheduled = 3, met = 1))
        assertEquals(40, r.debt)
        assertTrue(r.reconciliationDue)
    }

    @Test
    fun suddenDeathMissIsHeavy() {
        // score 30 (30) + 3 missed (60) + sudden death (40) -> capped at 100.
        val r = MomentumDebtEngine.calculate(day(score = 30, scheduled = 3, met = 0, suddenDeathMissed = true))
        assertEquals(MomentumDebtEngine.MAX_DEBT, r.debt)
        assertTrue(r.reconciliationDue)
    }

    @Test
    fun suggestionsScaleWithDebt() {
        assertTrue(MomentumDebtEngine.reconciliationSuggestion(0).contains("square"))
        assertTrue(MomentumDebtEngine.reconciliationSuggestion(70).contains("60-min"))
        assertTrue(MomentumDebtEngine.reconciliationSuggestion(40).contains("45-min"))
        assertTrue(MomentumDebtEngine.reconciliationSuggestion(10).isNotBlank())
    }
}
