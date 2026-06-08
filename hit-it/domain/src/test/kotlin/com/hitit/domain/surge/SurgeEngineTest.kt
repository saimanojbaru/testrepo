package com.hitit.domain.surge

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SurgeEngineTest {

    private val t0 = 1_000_000L
    private val minMs = 60_000L

    @Test
    fun highDebtAndLowRateTriggersDebtRecovery() {
        val s = SurgeEngine.evaluate(SurgeMetrics(currentStreak = 2, outstandingDebt = 6, recentCompletionRate = 0.5f), t0)
        assertEquals(SurgeKind.DEBT_RECOVERY, s?.kind)
        assertEquals(t0 + SurgeEngine.DEBT_RECOVERY_MINUTES * minMs, s?.expiresAtMillis)
    }

    @Test
    fun highStreakAndHighRateTriggersMonolith() {
        val s = SurgeEngine.evaluate(SurgeMetrics(currentStreak = 8, outstandingDebt = 0, recentCompletionRate = 0.95f), t0)
        assertEquals(SurgeKind.MONOLITH_BLITZ, s?.kind)
        assertEquals(t0 + SurgeEngine.MONOLITH_BLITZ_MINUTES * minMs, s?.expiresAtMillis)
    }

    @Test
    fun debtTakesPriorityOverMonolith() {
        // Qualifies for both -> debt recovery wins (checked first).
        val s = SurgeEngine.evaluate(SurgeMetrics(currentStreak = 9, outstandingDebt = 8, recentCompletionRate = 0.6f), t0)
        assertEquals(SurgeKind.DEBT_RECOVERY, s?.kind)
    }

    @Test
    fun normalMetricsTriggerNothing() {
        assertNull(SurgeEngine.evaluate(SurgeMetrics(currentStreak = 3, outstandingDebt = 2, recentCompletionRate = 0.8f), t0))
    }

    @Test
    fun activityAndExpiryWindows() {
        val s = SurgeEngine.evaluate(SurgeMetrics(2, 6, 0.4f), t0)!!
        assertTrue(s.isActive(t0))
        assertTrue(s.isActive(t0 + 10 * minMs))
        assertFalse(s.isExpired(t0 + 10 * minMs))
        assertTrue(s.isExpired(t0 + SurgeEngine.DEBT_RECOVERY_MINUTES * minMs))
        assertFalse(s.isActive(t0 + SurgeEngine.DEBT_RECOVERY_MINUTES * minMs))
    }

    @Test
    fun remainingMillisNeverNegative() {
        val s = SurgeEngine.evaluate(SurgeMetrics(2, 6, 0.4f), t0)!!
        assertEquals(0L, s.remainingMillis(t0 + 999 * minMs))
        assertEquals(SurgeEngine.DEBT_RECOVERY_MINUTES * minMs, s.remainingMillis(t0))
    }

    @Test
    fun expiredSurgeDoublesDebtWithinCap() {
        assertEquals(40, SurgeEngine.debtOnExpiry(20))
        assertEquals(100, SurgeEngine.debtOnExpiry(60)) // 120 capped to 100
        assertEquals(0, SurgeEngine.debtOnExpiry(0))
    }

    @Test
    fun expiryDeltaIsTheDoublingAmountWithinCap() {
        assertEquals(20, SurgeEngine.expiryDebtDelta(20)) // 40 - 20
        assertEquals(40, SurgeEngine.expiryDebtDelta(60)) // capped 100 - 60
        assertEquals(0, SurgeEngine.expiryDebtDelta(0))
    }

    @Test
    fun copyMentionsStakes() {
        assertTrue(SurgeEngine.detail(SurgeKind.DEBT_RECOVERY, 45).contains("doubles"))
        assertTrue(SurgeEngine.detail(SurgeKind.MONOLITH_BLITZ, 30).contains("Overdrive"))
    }
}
