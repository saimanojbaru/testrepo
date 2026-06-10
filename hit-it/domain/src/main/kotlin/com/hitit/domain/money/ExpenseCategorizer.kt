package com.hitit.domain.money

/**
 * Offline auto-categorizer for expense descriptions ("450 at Starbucks" → Coffee, impulse). Keyword
 * rules cover the lite build; the full flavor's on-device LLM can refine later. "Burner" categories
 * are the fun-money ones a Burner Budget tracks. Pure + unit-tested.
 */
enum class SpendCategory(val label: String, val emoji: String, val burner: Boolean) {
    COFFEE("Coffee", "☕", true),
    FOOD_DELIVERY("Food delivery", "🛵", true),
    DINING("Dining out", "🍽️", true),
    GROCERIES("Groceries", "🛒", false),
    TRAVEL("Travel", "🚕", false),
    SUBSCRIPTIONS("Subscriptions", "📺", true),
    SHOPPING("Shopping", "🛍️", true),
    ENTERTAINMENT("Entertainment", "🎮", true),
    BILLS("Bills", "🧾", false),
    HEALTH("Health", "💊", false),
    OTHER("Other", "✨", false),
}

object ExpenseCategorizer {

    private val RULES: List<Pair<SpendCategory, List<String>>> = listOf(
        SpendCategory.COFFEE to listOf("starbucks", "coffee", "cafe", "café", "latte", "chai", "barista", "ccd"),
        SpendCategory.FOOD_DELIVERY to listOf("swiggy", "zomato", "dominos", "domino's", "delivery", "blinkit food", "uber eats"),
        SpendCategory.DINING to listOf("restaurant", "dinner", "lunch out", "buffet", "dhaba", "pizzeria", "mcdonald", "kfc", "burger king"),
        SpendCategory.GROCERIES to listOf("grocery", "groceries", "bigbasket", "blinkit", "zepto", "dmart", "supermarket", "vegetables"),
        SpendCategory.TRAVEL to listOf("uber", "ola", "rapido", "metro", "bus", "train", "flight", "petrol", "fuel", "cab"),
        SpendCategory.SUBSCRIPTIONS to listOf("netflix", "spotify", "prime", "youtube premium", "subscription", "hotstar", "apple music", "icloud"),
        SpendCategory.SHOPPING to listOf("amazon", "flipkart", "myntra", "ajio", "mall", "shoes", "clothes", "zara", "h&m"),
        SpendCategory.ENTERTAINMENT to listOf("movie", "cinema", "pvr", "concert", "game", "steam", "bgmi", "tickets"),
        SpendCategory.BILLS to listOf("electricity", "rent", "wifi", "broadband", "recharge", "bill", "emi", "insurance"),
        SpendCategory.HEALTH to listOf("pharmacy", "medicine", "doctor", "gym", "protein", "hospital", "apollo"),
    )

    /** Keyword match; first category whose any-keyword hits (rules ordered by specificity). */
    fun categorize(description: String): SpendCategory {
        val d = description.lowercase()
        return RULES.firstOrNull { (_, keys) -> keys.any { d.contains(it) } }?.first ?: SpendCategory.OTHER
    }

    /**
     * Impulse heuristic: fun-money categories bought late at night (22:00–04:59) — the classic
     * 1am "late night eats" order. Used to tag spends the Coach can call out.
     */
    fun isImpulse(category: SpendCategory, hourOfDay: Int): Boolean =
        category.burner && (hourOfDay >= 22 || hourOfDay < 5)
}
