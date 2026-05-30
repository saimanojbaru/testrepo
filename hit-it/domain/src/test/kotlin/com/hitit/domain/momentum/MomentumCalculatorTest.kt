package com.hitit.domain.momentum

import com.hitit.domain.model.Priority
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
}
