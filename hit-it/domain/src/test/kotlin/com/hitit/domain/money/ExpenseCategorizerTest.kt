package com.hitit.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExpenseCategorizerTest {

    @Test
    fun knownMerchantsCategorize() {
        assertEquals(SpendCategory.COFFEE, ExpenseCategorizer.categorize("Starbucks grande"))
        assertEquals(SpendCategory.FOOD_DELIVERY, ExpenseCategorizer.categorize("zomato order"))
        assertEquals(SpendCategory.SUBSCRIPTIONS, ExpenseCategorizer.categorize("Netflix monthly"))
        assertEquals(SpendCategory.TRAVEL, ExpenseCategorizer.categorize("Uber to office"))
        assertEquals(SpendCategory.SHOPPING, ExpenseCategorizer.categorize("amazon haul"))
        assertEquals(SpendCategory.BILLS, ExpenseCategorizer.categorize("wifi recharge"))
    }

    @Test
    fun unknownFallsToOther() {
        assertEquals(SpendCategory.OTHER, ExpenseCategorizer.categorize("mystery box"))
    }

    @Test
    fun caseInsensitive() {
        assertEquals(SpendCategory.COFFEE, ExpenseCategorizer.categorize("STARBUCKS"))
    }

    @Test
    fun impulseIsLateNightFunMoney() {
        assertTrue(ExpenseCategorizer.isImpulse(SpendCategory.FOOD_DELIVERY, hourOfDay = 23))
        assertTrue(ExpenseCategorizer.isImpulse(SpendCategory.SHOPPING, hourOfDay = 1))
        assertFalse(ExpenseCategorizer.isImpulse(SpendCategory.FOOD_DELIVERY, hourOfDay = 13))
        assertFalse(ExpenseCategorizer.isImpulse(SpendCategory.GROCERIES, hourOfDay = 23)) // not burner
    }

    @Test
    fun burnerFlagsMarkFunMoney() {
        assertTrue(SpendCategory.COFFEE.burner)
        assertTrue(SpendCategory.SUBSCRIPTIONS.burner)
        assertFalse(SpendCategory.BILLS.burner)
        assertFalse(SpendCategory.GROCERIES.burner)
    }
}
