package com.hitit.domain.momentum

/** A named rank covering a contiguous range of levels. */
data class Tier(
    val index: Int,
    val name: String,
    val minLevel: Int,
    val maxLevel: Int,
)

/**
 * The 12-tier athletic progression ladder spanning levels 1..100.
 * Brackets are contiguous and exhaustive over the level range.
 */
object TierLadder {
    val TIERS: List<Tier> = listOf(
        Tier(1, "Rookie", 1, 8),
        Tier(2, "Amateur", 9, 16),
        Tier(3, "Prospect", 17, 25),
        Tier(4, "Contender", 26, 33),
        Tier(5, "Starter", 34, 41),
        Tier(6, "Pro", 42, 50),
        Tier(7, "All-Star", 51, 58),
        Tier(8, "Veteran", 59, 66),
        Tier(9, "Elite", 67, 75),
        Tier(10, "Champion", 76, 83),
        Tier(11, "Legend", 84, 91),
        Tier(12, "G.O.A.T.", 92, 100),
    )

    fun tierFor(level: Int): Tier {
        val l = level.coerceIn(1, 100)
        return TIERS.first { l in it.minLevel..it.maxLevel }
    }
}
