package com.hitit.domain.vibe

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VibeScoreTest {

    @Test
    fun composeBlendsAllThreePillars() {
        // 80*0.5 + 60*0.25 + 40*0.25 = 65
        assertEquals(65, VibeScore.compose(habit = 80, body = 60, money = 40))
    }

    @Test
    fun missingPillarsRedistributeWeight() {
        assertEquals(80, VibeScore.compose(habit = 80, body = null, money = null))
        // habit 80 (0.5) + body 40 (0.25) over 0.75 total = (40 + 10) / 0.75 = 66.6 -> 66
        assertEquals(66, VibeScore.compose(habit = 80, body = 40, money = null))
        assertEquals(0, VibeScore.compose(null, null, null))
    }

    @Test
    fun composeClampsInputs() {
        assertEquals(100, VibeScore.compose(habit = 250, body = 100, money = 100))
        assertEquals(0, VibeScore.compose(habit = -50, body = null, money = null))
    }

    @Test
    fun bodyPulseRewardsConsistencyAndCheckIn() {
        assertEquals(16, VibeScore.bodyPulse(foodDaysLast7 = 0, checkedInToday = false))
        assertEquals(100, VibeScore.bodyPulse(foodDaysLast7 = 7, checkedInToday = true)) // 16+70+14
        assertTrue(VibeScore.bodyPulse(3, true) > VibeScore.bodyPulse(3, false))
        assertEquals(VibeScore.bodyPulse(7, false), VibeScore.bodyPulse(12, false)) // clamps days
    }

    @Test
    fun moneyPulseTracksAwarenessAndBudgetHealth() {
        assertEquals(20, VibeScore.moneyPulse(spendDaysLast7 = 0, burnerUtilization = null))
        // Under budget beats no budget beats blown budget.
        val under = VibeScore.moneyPulse(5, 0.5f)
        val none = VibeScore.moneyPulse(5, null)
        val blown = VibeScore.moneyPulse(5, 2f)
        assertTrue(under > none)
        assertTrue(none > blown)
        // Massive overshoot is floored, never below zero overall.
        assertTrue(VibeScore.moneyPulse(0, 10f) >= 0)
    }

    @Test
    fun avatarEvolvesWithScore() {
        assertEquals("😴", VibeScore.avatarFor(5))
        assertEquals("🌱", VibeScore.avatarFor(25))
        assertEquals("🙂", VibeScore.avatarFor(45))
        assertEquals("😎", VibeScore.avatarFor(65))
        assertEquals("🔥", VibeScore.avatarFor(80))
        assertEquals("👑", VibeScore.avatarFor(95))
    }

    @Test
    fun labelsCoverAllTiers() {
        listOf(5, 25, 45, 65, 80, 95).forEach { assertTrue(VibeScore.label(it).isNotBlank()) }
    }
}
