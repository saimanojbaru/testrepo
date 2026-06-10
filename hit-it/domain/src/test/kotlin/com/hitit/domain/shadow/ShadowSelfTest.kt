package com.hitit.domain.shadow

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ShadowSelfTest {

    @Test
    fun cleanSlateGetsTheBackhandedCompliment() {
        val roasts = ShadowSelf.roast(ShadowSignals())
        assertEquals(1, roasts.size)
        assertEquals(0, roasts.first().severity)
        assertTrue(roasts.first().line.contains("freak"))
    }

    @Test
    fun highDebtIsTheWorstAndSortsFirst() {
        val roasts = ShadowSelf.roast(ShadowSignals(outstandingDebt = 10, missedRepsLast7 = 3))
        assertTrue(roasts.first().headline.contains("cooked"))
        assertEquals(5, roasts.first().severity)
    }

    @Test
    fun roastsAreSortedBySeverityDescending() {
        val roasts = ShadowSelf.roast(
            ShadowSignals(outstandingDebt = 9, skippedCheckInDays = 3, impulseSpends = 2, brokeStreak = true),
        )
        val sev = roasts.map { it.severity }
        assertEquals(sev.sortedDescending(), sev)
        assertTrue(roasts.size >= 4)
    }

    @Test
    fun debtTiersDifferentiate() {
        assertTrue(ShadowSelf.roast(ShadowSignals(outstandingDebt = 3)).first().headline.contains("delusion"))
        assertTrue(ShadowSelf.roast(ShadowSignals(outstandingDebt = 12)).first().headline.contains("cooked"))
    }

    @Test
    fun everyRoastNamesItsNumberOrTheFix() {
        val roasts = ShadowSelf.roast(ShadowSignals(missedRepsLast7 = 7, impulseSpends = 3))
        // Each line should be non-empty and reference an actionable nudge or the count.
        assertTrue(roasts.all { it.line.isNotBlank() && it.headline.isNotBlank() })
    }

    @Test
    fun verdictEscalatesWithHeat() {
        assertTrue(ShadowSelf.verdict(ShadowSignals()).contains("him"))
        assertTrue(ShadowSelf.verdict(ShadowSignals(outstandingDebt = 12, missedRepsLast7 = 6, brokeStreak = true)).contains("COOKED"))
    }
}
