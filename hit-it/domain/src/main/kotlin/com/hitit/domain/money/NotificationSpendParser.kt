package com.hitit.domain.money

/**
 * The auto-integration brain: turns a posted notification (package + title + text) into a spend —
 * or refuses. Apps don't offer consumer APIs, but their notifications announce every order/payment,
 * so a per-app parser IS the integration: source app fixes the merchant/category (Zomato → Food
 * delivery), UPI apps (GPay/PhonePe/Paytm) get merchant extraction + keyword categorization.
 * Deliberately conservative: refunds/credits/promos/OTPs are rejected, amounts are sanity-capped,
 * and unknown packages are ignored entirely. Pure + heavily tested.
 */
data class CapturedSpend(
    val amountPaise: Long,
    val merchant: String,
    val category: SpendCategory,
)

object NotificationSpendParser {

    /** Source apps we understand: package → (display name, fixed category or null for UPI-style). */
    val KNOWN_APPS: Map<String, Pair<String, SpendCategory?>> = mapOf(
        "com.application.zomato" to ("Zomato" to SpendCategory.FOOD_DELIVERY),
        "in.swiggy.android" to ("Swiggy" to SpendCategory.FOOD_DELIVERY),
        "com.ubercab" to ("Uber" to SpendCategory.TRAVEL),
        "com.olacabs.customer" to ("Ola" to SpendCategory.TRAVEL),
        "com.rapido.passenger" to ("Rapido" to SpendCategory.TRAVEL),
        "com.urbanclap.urbanclap" to ("Urban Company" to SpendCategory.BILLS),
        "com.grofers.customerapp" to ("Blinkit" to SpendCategory.GROCERIES),
        "com.zeptoconsumerapp" to ("Zepto" to SpendCategory.GROCERIES),
        "com.bigbasket.mobileapp" to ("BigBasket" to SpendCategory.GROCERIES),
        "com.dominos" to ("Domino's" to SpendCategory.FOOD_DELIVERY),
        // UPI / wallets — category comes from the extracted merchant text.
        "com.google.android.apps.nbu.paisa.user" to ("GPay" to null),
        "com.phonepe.app" to ("PhonePe" to null),
        "net.one97.paytm" to ("Paytm" to null),
    )

    /** ₹450 / Rs. 450 / INR 1,249.50 — first match wins. */
    private val AMOUNT = Regex("""(?:₹|rs\.?\s?|inr\s?)\s*([\d,]+(?:\.\d{1,2})?)""", RegexOption.IGNORE_CASE)

    /**
     * "You paid ₹120 to Chai Point using Google Pay" → "Chai Point". Lazy capture, terminated by
     * connector words ("using"/"via"/…), punctuation, or end — so the payment-app suffix never
     * leaks into the merchant name.
     */
    private val UPI_MERCHANT = Regex(
        """(?:paid|payment)\s+(?:(?:of\s+)?₹?[\d,.]+\s+)?to\s+([A-Za-z][A-Za-z0-9 &.'_-]{0,39}?)(?:\s+(?:using|via|with|from|on)\b|[.,!\n]|$)""",
        RegexOption.IGNORE_CASE,
    )

    private val PAY_HINTS = listOf(
        "paid", "payment successful", "payment of", "debited", "spent", "charged",
        "order placed", "order confirmed", "trip", "ride", "fare", "bill",
    )
    private val REJECT_HINTS = listOf(
        "received", "credited", "refund", "cashback", "reward", "offer", "% off", "sale",
        "coupon", "otp", "arriving", "delivered by", "rate your", "failed", "declined", "request",
    )

    const val MAX_PAISE = 1_00_000_00L // ₹1,00,000 sanity cap — bigger is parsing noise

    fun parse(packageName: String, title: String, text: String): CapturedSpend? {
        val (appName, fixedCategory) = KNOWN_APPS[packageName] ?: return null
        val combined = "$title $text"
        val lower = combined.lowercase()

        if (REJECT_HINTS.any { lower.contains(it) }) return null
        if (PAY_HINTS.none { lower.contains(it) }) return null

        val amountRaw = AMOUNT.find(combined)?.groupValues?.get(1)?.replace(",", "") ?: return null
        val amountPaise = amountRaw.toDoubleOrNull()?.let { (it * 100).toLong() } ?: return null
        if (amountPaise <= 0 || amountPaise > MAX_PAISE) return null

        return if (fixedCategory != null) {
            CapturedSpend(amountPaise, appName, fixedCategory)
        } else {
            // UPI: pull the payee out of the text; categorize it by keywords.
            val merchant = UPI_MERCHANT.find(combined)?.groupValues?.get(1)?.trim()?.take(32) ?: appName
            CapturedSpend(amountPaise, merchant, ExpenseCategorizer.categorize(merchant))
        }
    }
}
