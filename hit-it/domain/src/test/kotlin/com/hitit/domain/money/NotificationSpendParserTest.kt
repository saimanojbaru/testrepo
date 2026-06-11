package com.hitit.domain.money

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NotificationSpendParserTest {

    @Test
    fun zomatoOrderCapturesWithFixedCategory() {
        val spend = NotificationSpendParser.parse(
            "com.application.zomato",
            "Order placed successfully",
            "Your order of ₹450 from Biryani Blues is being prepared",
        )!!
        assertEquals(45_000, spend.amountPaise)
        assertEquals("Zomato", spend.merchant)
        assertEquals(SpendCategory.FOOD_DELIVERY, spend.category)
    }

    @Test
    fun uberTripFareCaptures() {
        val spend = NotificationSpendParser.parse(
            "com.ubercab",
            "Trip completed",
            "Your trip fare was Rs. 249",
        )!!
        assertEquals(24_900, spend.amountPaise)
        assertEquals(SpendCategory.TRAVEL, spend.category)
    }

    @Test
    fun gpayExtractsMerchantAndCategorizes() {
        val spend = NotificationSpendParser.parse(
            "com.google.android.apps.nbu.paisa.user",
            "Payment successful",
            "You paid ₹120 to Chai Point using Google Pay",
        )!!
        assertEquals(12_000, spend.amountPaise)
        assertEquals("Chai Point", spend.merchant)
        assertEquals(SpendCategory.COFFEE, spend.category) // "chai" keyword
    }

    @Test
    fun commaAndDecimalAmountsParse() {
        val spend = NotificationSpendParser.parse(
            "com.phonepe.app",
            "Payment of INR 1,249.50",
            "Paid to Amazon Pay",
        )!!
        assertEquals(124_950, spend.amountPaise)
    }

    @Test
    fun refundsCreditsAndPromosAreRejected() {
        assertNull(
            NotificationSpendParser.parse(
                "com.application.zomato", "Refund processed", "₹450 refund for your order",
            ),
        )
        assertNull(
            NotificationSpendParser.parse(
                "net.one97.paytm", "Money received", "₹500 credited to your wallet",
            ),
        )
        assertNull(
            NotificationSpendParser.parse(
                "in.swiggy.android", "60% off today!", "Order now and save ₹120",
            ),
        )
        assertNull(
            NotificationSpendParser.parse(
                "com.phonepe.app", "OTP", "Use 482913 to pay ₹500",
            ),
        )
    }

    @Test
    fun unknownPackagesAndMissingAmountsAreIgnored() {
        assertNull(NotificationSpendParser.parse("com.whatsapp", "Paid ₹450", "totally a payment"))
        assertNull(NotificationSpendParser.parse("com.ubercab", "Trip completed", "Hope you enjoyed the ride!"))
    }

    @Test
    fun noPaymentHintMeansNoCapture() {
        assertNull(
            NotificationSpendParser.parse(
                "com.application.zomato", "Craving biryani?", "Dishes from ₹99 near you",
            ),
        )
    }

    @Test
    fun insaneAmountsAreRejected() {
        assertNull(
            NotificationSpendParser.parse(
                "com.phonepe.app", "Payment successful", "You paid ₹99,99,999 to scam corp",
            ),
        )
    }
}
