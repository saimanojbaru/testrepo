package com.hitit.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FutureSelfTest {

    @Test
    fun zeroBurnProjectsZero() {
        assertEquals(0L, FutureSelf.futureValuePaise(0))
        val v = FutureSelf.vision(0)
        assertEquals(0L, v.yearlyBurnPaise)
        assertTrue(v.brokeLine.contains("starves"))
    }

    @Test
    fun compoundBeatsSimpleAccumulation() {
        val weekly = 1_000_00L // ₹1000/week
        val fv = FutureSelf.futureValuePaise(weekly, years = 5)
        val plainSavings = weekly * 52 * 5
        assertTrue(fv > plainSavings) // compounding must beat the mattress
    }

    @Test
    fun futureValueMatchesAnnuityFormulaSpotCheck() {
        // ₹1000/week -> monthly 52000/12 ≈ 4333.33; 12%/yr monthly comp for 60 months
        // FV = P * ((1+i)^n - 1)/i ≈ 4333.33 * 81.6697 ≈ 353,902 rupees
        val fv = FutureSelf.futureValuePaise(1_000_00L, years = 5)
        val rupees = fv / 100
        assertTrue("got $rupees", rupees in 350_000..358_000)
    }

    @Test
    fun yearlyBurnIsWeeklyTimes52() {
        assertEquals(52_000_00L, FutureSelf.vision(1_000_00L).yearlyBurnPaise)
    }

    @Test
    fun copyTiersEscalate() {
        assertTrue(FutureSelf.vision(3_000_00L).brokeLine.contains("fridge")) // 156k/yr -> top tier
        assertTrue(FutureSelf.vision(100_00L).glowLine.contains("₹"))
    }
}
