package com.hitit.domain.identity

import com.hitit.domain.trophy.TrophyStats

/**
 * An Identity Card: an earned "who you are becoming" badge that grants a permanent **per-action
 * Momentum bonus** ([bonusPercent]) while unlocked. Pure rule + display data, mirroring TrophyDef.
 */
data class IdentityDef(
    val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val bonusPercent: Int,
    val predicate: (TrophyStats) -> Boolean,
)

/** The full set of Identities + pure rules for which are unlocked and the combined bonus. */
object IdentityCatalog {
    val ALL: List<IdentityDef> = listOf(
        IdentityDef("consistent_beast", "Consistent Beast", "Hold a 7-day streak", "🐺", 5) { it.bestStreak >= 7 },
        IdentityDef("deep_thinker", "Deep Thinker", "Focus 5 hours total", "🧠", 5) { it.totalFocusMinutes >= 300 },
        IdentityDef("closer", "The Closer", "Complete 50 Hits", "🎯", 5) { it.tasksCompleted >= 50 },
        IdentityDef("reflector", "Reflective", "Log 14 Check-Ins", "🪞", 5) { it.checkInCount >= 14 },
        IdentityDef("unbreakable", "Unbreakable", "Hold a 30-day streak", "💎", 10) { it.bestStreak >= 30 },
    )

    private val byId: Map<String, IdentityDef> = ALL.associateBy { it.id }

    fun byId(id: String): IdentityDef? = byId[id]

    /** The ids of every Identity whose rule is satisfied by [stats]. */
    fun evaluate(stats: TrophyStats): Set<String> =
        ALL.asSequence().filter { it.predicate(stats) }.map { it.id }.toSet()

    /**
     * The combined Momentum bonus percent from a set of unlocked identity ids. Bonuses **stack
     * additively** and are capped to keep the economy sane.
     */
    const val MAX_BONUS_PERCENT = 30
    fun totalBonusPercent(unlockedIds: Set<String>): Int =
        unlockedIds.sumOf { byId(it)?.bonusPercent ?: 0 }.coerceIn(0, MAX_BONUS_PERCENT)
}
