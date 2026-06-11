package com.hitit.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WealthAnalyzerTest {

    private fun fact(
        rupees: Long,
        category: SpendCategory = SpendCategory.FOOD_DELIVERY,
        impulse: Boolean = false,
        daysAgo: Int = 0,
        hour: Int = 13,
    ) = SpendFact(rupees * 100, category, impulse, daysAgo, hour)

    @Test
    fun emptyWeekGetsTheWakeUpInsight() {
        val insights = WealthAnalyzer.analyze(emptyList(), burnerBudgetPaise = 0)
        assertEquals(1, insights.size)
        assertTrue(insights.first().headline.contains("empty"))
    }

    @Test
    fun lateNightShareTriggersDemon() {
        val facts = listOf(
            fact(400, hour = 23),               // night burner spend
            fact(300, hour = 1),                // night burner spend
            fact(500, SpendCategory.GROCERIES), // non-burner, irrelevant to demon share
            fact(200, hour = 12),               // day burner spend
        )
        // night share of burner = 700/900 = 78% >= 35%
        val insights = WealthAnalyzer.analyze(facts, 0)
        assertTrue(insights.any { it.headline == "Demon detected" && it.severity == 4 })
    }

    @Test
    fun torchedBudgetIsSeverityFiveAndFirst() {
        val facts = listOf(fact(600, hour = 12), fact(600, hour = 13))
        val insights = WealthAnalyzer.analyze(facts, burnerBudgetPaise = 1_000_00) // ₹1000 cap, ₹1200 spent
        assertEquals("Burner budget torched", insights.first().headline)
        assertEquals(5, insights.first().severity)
    }

    @Test
    fun subscriptionLeakageReportsTheSum() {
        val facts = listOf(
            fact(199, SpendCategory.SUBSCRIPTIONS),
            fact(649, SpendCategory.SUBSCRIPTIONS),
        )
        val insights = WealthAnalyzer.analyze(facts, 0)
        val leak = insights.first { it.headline == "Subscription leakage" }
        assertTrue(leak.detail.contains("₹848"))
        assertEquals(3, leak.severity) // >= ₹500
    }

    @Test
    fun concentrationNamesTheDominantCategory() {
        val facts = listOf(
            fact(800, SpendCategory.FOOD_DELIVERY),
            fact(200, SpendCategory.TRAVEL),
        )
        val insights = WealthAnalyzer.analyze(facts, 0)
        assertTrue(insights.any { it.headline.contains("Food delivery ate your week") })
    }

    @Test
    fun quietWeekEarnsNoSpendAura() {
        val facts = listOf(fact(100, daysAgo = 0), fact(100, daysAgo = 1)) // 5 silent days
        val insights = WealthAnalyzer.analyze(facts, 0)
        assertTrue(insights.any { it.headline.contains("no-spend days") && it.severity == 0 })
    }

    @Test
    fun insightsSortWorstFirstAndCap() {
        val facts = listOf(
            fact(600, hour = 23), fact(700, hour = 0),                 // demon + torch contributor
            fact(300, SpendCategory.SUBSCRIPTIONS),
            fact(900, SpendCategory.FOOD_DELIVERY, daysAgo = 0),
        )
        val insights = WealthAnalyzer.analyze(facts, burnerBudgetPaise = 1_000_00)
        assertTrue(insights.size <= WealthAnalyzer.MAX_INSIGHTS)
        val sevs = insights.map { it.severity }
        assertEquals(sevs.sortedDescending(), sevs)
    }

    @Test
    fun vibeTaxRoundsUpToNextTenRupees() {
        assertEquals(300, VibeTax.roundUpDelta(43_700))  // ₹437 -> ₹3 to reach ₹440
        assertEquals(0, VibeTax.roundUpDelta(50_000))    // exact ₹500 -> nothing
        assertEquals(0, VibeTax.roundUpDelta(0))
        assertEquals(999, VibeTax.roundUpDelta(1))       // 1 paisa -> 9.99 to ₹10
    }

    @Test
    fun jarCollectsOnlyFunMoneyAndImpulse() {
        val facts = listOf(
            fact(437, SpendCategory.COFFEE),                  // burner -> +300 paise
            fact(437, SpendCategory.GROCERIES),               // not burner, not impulse -> 0
            fact(437, SpendCategory.BILLS, impulse = true),   // impulse -> +300 paise
        )
        assertEquals(600, VibeTax.jarTotal(facts))
    }

    @Test
    fun demonRoastsAreDeterministicAndCategoryAware() {
        assertEquals(
            DemonRoasts.roast(SpendCategory.COFFEE, 1),
            DemonRoasts.roast(SpendCategory.COFFEE, 1),
        )
        assertTrue(DemonRoasts.roast(SpendCategory.FOOD_DELIVERY, 0).contains("kitchen"))
        assertTrue(DemonRoasts.roast(SpendCategory.HEALTH, -3).isNotBlank()) // fallback + negative seed safe
    }
}
