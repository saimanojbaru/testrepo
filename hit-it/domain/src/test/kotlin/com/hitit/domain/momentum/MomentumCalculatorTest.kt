package com.hitit.domain.momentum

import com.hitit.domain.model.Priority
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MomentumCalculatorTest {

    @Test
    fun baseAwardWithNoStreak() {
        assertEquals(MomentumCalculator.BASE, MomentumCalculator.awardForHit(0))
    }

    @Test
    fun streakAddsBonus() {
        assertEquals(MomentumCalculator.BASE + 5, MomentumCalculator.awardForHit(5))
    }

    @Test
    fun bonusIsCapped() {
        assertEquals(
            MomentumCalculator.BASE + MomentumCalculator.STREAK_BONUS_CAP,
            MomentumCalculator.awardForHit(1000),
        )
    }

    @Test
    fun negativeStreakClampsToBase() {
        assertEquals(MomentumCalculator.BASE, MomentumCalculator.awardForHit(-3))
    }

    @Test
    fun taskAwardScalesByPriority() {
        assertEquals(20, MomentumCalculator.awardForTask(Priority.HIGH))
        assertEquals(15, MomentumCalculator.awardForTask(Priority.MEDIUM))
        assertEquals(10, MomentumCalculator.awardForTask(Priority.LOW))
    }

    @Test
    fun focusAwardIsOnePerMinute() {
        assertEquals(25, MomentumCalculator.awardForFocus(25))
        assertEquals(0, MomentumCalculator.awardForFocus(0))
        assertEquals(0, MomentumCalculator.awardForFocus(-5))
    }

    @Test
    fun checkInAwardIsFlat() {
        assertEquals(MomentumCalculator.CHECK_IN, MomentumCalculator.awardForCheckIn())
        assertEquals(8, MomentumCalculator.awardForCheckIn())
    }

    @Test
    fun goalAwardsAreFlat() {
        assertEquals(15, MomentumCalculator.awardForCheckpoint())
        assertEquals(50, MomentumCalculator.awardForBigPlay())
    }

    @Test
    fun recoveryAndSuddenDeathConstants() {
        assertEquals(4, MomentumCalculator.awardForRecovery())
        assertEquals(30, MomentumCalculator.suddenDeathPenalty())
    }

    @Test
    fun perfectDayDetection() {
        assertTrue(MomentumCalculator.completesPerfectDay(repsScheduled = 4, repsMetBefore = 3))
        assertFalse(MomentumCalculator.completesPerfectDay(repsScheduled = 4, repsMetBefore = 2))
        assertFalse(MomentumCalculator.completesPerfectDay(repsScheduled = 0, repsMetBefore = 0))
    }

    @Test
    fun perfectDayMultiplierApplied() {
        assertEquals(15, MomentumCalculator.applyPerfectDay(10, perfect = true)) // 10 * 1.5
        assertEquals(10, MomentumCalculator.applyPerfectDay(10, perfect = false))
    }

    @Test
    fun identityBonusAppliedToBase() {
        assertEquals(11, MomentumCalculator.applyIdentityBonus(10, 10)) // +10%
        assertEquals(13, MomentumCalculator.applyIdentityBonus(10, 30)) // +30%
        assertEquals(10, MomentumCalculator.applyIdentityBonus(10, 0))
    }

    @Test
    fun hitAwardIsDeterministicForUndoSymmetry() {
        // Same inputs => same number, so clearHit reverses logHit exactly (no ledger drift).
        val streak = 5
        val a = MomentumCalculator.hitAward(streak, identityBonusPercent = 20, perfectDay = true)
        val b = MomentumCalculator.hitAward(streak, identityBonusPercent = 20, perfectDay = true)
        assertEquals(a, b)
        // Order: base(10+5=15) -> identity +20% (=18) -> perfect 1.5x (=27).
        assertEquals(27, a)
        // Without bonus/perfect it's just the base award.
        assertEquals(MomentumCalculator.awardForHit(streak), MomentumCalculator.hitAward(streak, 0, false))
    }
}
