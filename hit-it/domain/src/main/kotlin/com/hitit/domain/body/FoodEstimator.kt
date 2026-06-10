package com.hitit.domain.body

/**
 * Offline calorie estimator for natural food descriptions like "200g chicken breast and rice" or
 * "2 eggs, a banana". A compact built-in table (no network, no model) powers the lite build; the
 * full flavor's on-device LLM can refine this later. Deterministic and unit-tested: gram-priced
 * foods scale per 100g (with a default portion when no weight is given) and piece-priced foods
 * count units. Returns null when nothing matched, so the UI falls back to manual entry.
 */
object FoodEstimator {

    const val DEFAULT_PORTION_G = 150

    data class Estimate(val kcal: Int, val matched: List<String>, val unmatched: List<String>)

    private data class Food(val keys: List<String>, val per100g: Int? = null, val perPiece: Int? = null)

    // kcal per 100g for weighable foods; kcal per piece for countable ones. Compact on purpose.
    private val TABLE = listOf(
        Food(listOf("chicken breast", "chicken"), per100g = 165),
        Food(listOf("rice", "fried rice"), per100g = 130),
        Food(listOf("biryani"), per100g = 180),
        Food(listOf("dal", "lentils"), per100g = 120),
        Food(listOf("paneer"), per100g = 265),
        Food(listOf("curd", "yogurt", "yoghurt"), per100g = 60),
        Food(listOf("milk"), per100g = 62),
        Food(listOf("oats", "oatmeal"), per100g = 380),
        Food(listOf("salad"), per100g = 60),
        Food(listOf("fries", "french fries"), per100g = 310),
        Food(listOf("noodles", "maggi", "pasta"), per100g = 170),
        Food(listOf("fish"), per100g = 140),
        Food(listOf("egg", "eggs"), perPiece = 78),
        Food(listOf("roti", "chapati"), perPiece = 100),
        Food(listOf("dosa"), perPiece = 130),
        Food(listOf("idli"), perPiece = 40),
        Food(listOf("banana"), perPiece = 105),
        Food(listOf("apple"), perPiece = 95),
        Food(listOf("bread", "toast"), perPiece = 70),
        Food(listOf("pizza"), perPiece = 285),
        Food(listOf("burger"), perPiece = 295),
        Food(listOf("samosa"), perPiece = 260),
        Food(listOf("coffee", "latte", "chai", "tea"), perPiece = 60),
        Food(listOf("protein shake", "shake"), perPiece = 180),
        Food(listOf("chocolate"), perPiece = 220),
    )

    private val GRAMS = Regex("""(\d+)\s*(?:g|gm|gms|grams?)\b""")
    private val COUNT = Regex("""(?:^|\s)(\d+)\s+(?!g\b|gm\b)""")

    fun estimate(text: String): Estimate? {
        val segments = text.lowercase().split(",", " and ", "+", "&", " with ").map { it.trim() }.filter { it.isNotEmpty() }
        var kcal = 0
        val matched = mutableListOf<String>()
        val unmatched = mutableListOf<String>()
        for (seg in segments) {
            // Longest key wins so "chicken breast" beats "chicken".
            val food = TABLE
                .flatMap { f -> f.keys.map { it to f } }
                .filter { (key, _) -> seg.contains(key) }
                .maxByOrNull { (key, _) -> key.length }
                ?.second
            if (food == null) {
                unmatched += seg
                continue
            }
            val grams = GRAMS.find(seg)?.groupValues?.get(1)?.toIntOrNull()
            val count = COUNT.find(seg)?.groupValues?.get(1)?.toIntOrNull()
            kcal += when {
                food.per100g != null -> ((grams ?: DEFAULT_PORTION_G) * food.per100g) / 100
                food.perPiece != null -> (count ?: 1) * food.perPiece
                else -> 0
            }
            matched += seg
        }
        return if (matched.isEmpty()) null else Estimate(kcal, matched, unmatched)
    }
}
