package com.hitit.domain.momentum

import org.junit.Assert.assertEquals
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
}
