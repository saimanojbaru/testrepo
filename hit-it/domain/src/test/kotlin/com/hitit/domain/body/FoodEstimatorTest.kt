package com.hitit.domain.body

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class FoodEstimatorTest {

    @Test
    fun gramFoodsScalePer100g() {
        // 200g chicken breast = 2 * 165 = 330
        val e = FoodEstimator.estimate("200g chicken breast")!!
        assertEquals(330, e.kcal)
    }

    @Test
    fun theCanonicalSmartInput() {
        // "200g chicken breast and rice": chicken 330 + rice default portion 150g*130/100 = 195 -> 525
        val e = FoodEstimator.estimate("200g chicken breast and rice")!!
        assertEquals(525, e.kcal)
        assertEquals(2, e.matched.size)
    }

    @Test
    fun pieceFoodsCountUnits() {
        assertEquals(156, FoodEstimator.estimate("2 eggs")!!.kcal)
        assertEquals(105, FoodEstimator.estimate("banana")!!.kcal) // default 1 piece
        assertEquals(300, FoodEstimator.estimate("3 roti")!!.kcal)
    }

    @Test
    fun longestKeyWinsOverSubstring() {
        // "chicken breast" (165/100g) must match as chicken breast, not bare "chicken".
        val e = FoodEstimator.estimate("100g chicken breast")!!
        assertEquals(165, e.kcal)
    }

    @Test
    fun mixedListSumsAndTracksUnmatched() {
        val e = FoodEstimator.estimate("2 idli, 1 dosa, unicorn dust")!!
        assertEquals(2 * 40 + 130, e.kcal)
        assertEquals(1, e.unmatched.size)
    }

    @Test
    fun nothingMatchedReturnsNull() {
        assertNull(FoodEstimator.estimate("xyzzy plugh"))
        assertNull(FoodEstimator.estimate(""))
    }

    @Test
    fun caseInsensitive() {
        assertTrue(FoodEstimator.estimate("200G CHICKEN BREAST")!!.kcal > 0)
    }
}
