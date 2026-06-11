package com.hitit.domain.money

/**
 * A week of spending, flattened for the analyzer. Domain stays date-free: [daysAgo] 0 = today.
 * [hourOfDay] is the local hour the spend was logged (for the late-night demon detection).
 */
data class SpendFact(
    val amountPaise: Long,
    val category: SpendCategory,
    val impulse: Boolean,
    val daysAgo: Int,
    val hourOfDay: Int,
)

data class WealthInsight(
    val emoji: String,
    val headline: String,
    val detail: String,
    val severity: Int, // 5 = wallet on fire … 0 = neutral/positive
)

/**
 * The Wealth Analyzer — MoneyVibe's brain. Deterministic rules over the last 7 days of facts:
 * demon detection (late-night fun-money share), subscription leakage, category concentration,
 * no-spend aura, burner-budget pace, and the Vibe Tax jar. Worst news first. The optional on-device
 * LLM can rephrase these later; the analysis itself never needs it.
 */
object WealthAnalyzer {

    const val DEMON_SHARE = 0.35f
    const val CONCENTRATION_SHARE = 0.40f
    const val MAX_INSIGHTS = 5

    fun analyze(weekFacts: List<SpendFact>, burnerBudgetPaise: Long): List<WealthInsight> {
        if (weekFacts.isEmpty()) {
            return listOf(
                WealthInsight("🫥", "Ledger's empty", "Log a spend and the analyzer wakes up. It sees everything.", 0),
            )
        }
        val insights = mutableListOf<WealthInsight>()
        val total = weekFacts.sumOf { it.amountPaise }
        val burnerFacts = weekFacts.filter { it.category.burner }
        val burnerTotal = burnerFacts.sumOf { it.amountPaise }

        // 1. Late-night demon: share of fun money spent in the gremlin hours.
        val nightSpend = burnerFacts.filter { it.hourOfDay >= 22 || it.hourOfDay < 5 }.sumOf { it.amountPaise }
        if (burnerTotal > 0 && nightSpend.toFloat() / burnerTotal >= DEMON_SHARE) {
            val pct = (nightSpend * 100 / burnerTotal).toInt()
            insights += WealthInsight(
                "👹", "Demon detected",
                "$pct% of your fun money died after dark (₹${nightSpend / 100}). The 1am you is robbing the 9am you.",
                4,
            )
        }

        // 2. Burner budget pace.
        if (burnerBudgetPaise > 0) {
            val utilization = burnerTotal.toFloat() / burnerBudgetPaise
            when {
                utilization > 1f -> insights += WealthInsight(
                    "💀", "Burner budget torched",
                    "${(utilization * 100).toInt()}% of the weekly cap is gone. The budget is now a suggestion you ignored.",
                    5,
                )
                utilization >= 0.7f -> insights += WealthInsight(
                    "⚠️", "Running hot",
                    "${(utilization * 100).toInt()}% of the burner budget spent — at this pace it's gone before Sunday.",
                    3,
                )
            }
        }

        // 3. Subscription leakage.
        val subs = weekFacts.filter { it.category == SpendCategory.SUBSCRIPTIONS }.sumOf { it.amountPaise }
        if (subs > 0) {
            insights += WealthInsight(
                "📺", "Subscription leakage",
                "₹${subs / 100} drained by subscriptions this week. Which one haven't you opened this month?",
                if (subs >= 50_000) 3 else 2,
            )
        }

        // 4. Category concentration.
        val topEntry = weekFacts.groupBy { it.category }.mapValues { (_, f) -> f.sumOf { it.amountPaise } }
            .maxByOrNull { it.value }
        if (topEntry != null && total >= 50_000 && topEntry.value.toFloat() / total >= CONCENTRATION_SHARE) {
            val pct = (topEntry.value * 100 / total).toInt()
            insights += WealthInsight(
                topEntry.key.emoji, "${topEntry.key.label} ate your week",
                "$pct% of everything you spent went to ${topEntry.key.label.lowercase()}. That's a relationship now.",
                3,
            )
        }

        // 5. No-spend aura (positive).
        val noSpendDays = 7 - weekFacts.map { it.daysAgo.coerceIn(0, 6) }.distinct().size
        if (noSpendDays >= 3) {
            insights += WealthInsight(
                "🧊", "$noSpendDays no-spend days",
                "Days where the wallet never opened. Rare aura. Keep collecting them.",
                0,
            )
        }

        // 6. Vibe Tax jar (positive).
        val jar = VibeTax.jarTotal(weekFacts)
        if (jar > 0) {
            insights += WealthInsight(
                "🫙", "Vibe Tax jar: ₹${jar / 100}",
                "Auto round-ups skimmed off your impulse buys this week. Future you says thanks.",
                0,
            )
        }

        return insights.sortedByDescending { it.severity }.take(MAX_INSIGHTS)
    }
}

/**
 * Vibe Tax: every fun-money spend is rounded up to the next ₹10 and the difference is "collected"
 * into the Future Self Jar. Derived (never stored), so it's always consistent with the ledger.
 */
object VibeTax {
    const val UNIT_PAISE = 1_000L // ₹10

    fun roundUpDelta(amountPaise: Long, unitPaise: Long = UNIT_PAISE): Long =
        if (amountPaise <= 0) 0 else (unitPaise - amountPaise % unitPaise) % unitPaise

    fun jarTotal(facts: List<SpendFact>): Long =
        facts.filter { it.category.burner || it.impulse }.sumOf { roundUpDelta(it.amountPaise) }
}

/** Savage-but-caring one-liners for the Impulse Demons list. Deterministic per (category, seed). */
object DemonRoasts {
    private val BY_CATEGORY: Map<SpendCategory, List<String>> = mapOf(
        SpendCategory.FOOD_DELIVERY to listOf(
            "the kitchen is RIGHT THERE",
            "delivery fee said thanks for the donation",
            "this is why the gym rep is crying",
        ),
        SpendCategory.COFFEE to listOf(
            "₹450 bean water. iconic.",
            "the chai at home: 'am I a joke to you?'",
        ),
        SpendCategory.SHOPPING to listOf(
            "the cart said add and you said yes sir",
            "you already own three of these",
        ),
        SpendCategory.SUBSCRIPTIONS to listOf(
            "paying rent for an app you forgot",
            "cancel it. you won't. but cancel it.",
        ),
        SpendCategory.ENTERTAINMENT to listOf(
            "fun was had. the budget wasn't.",
        ),
        SpendCategory.DINING to listOf(
            "the bill split conveniently forgot you",
        ),
    )
    private val FALLBACK = listOf(
        "the void thanks you for your contribution",
        "money said bye with no notice period",
    )

    fun roast(category: SpendCategory, seed: Int): String {
        val pool = BY_CATEGORY[category] ?: FALLBACK
        return pool[((seed % pool.size) + pool.size) % pool.size]
    }
}
