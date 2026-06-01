package com.hitit.domain.trophy

/** A Trophy definition: the unlock rule plus its display data and Momentum bonus. */
data class TrophyDef(
    val id: String,
    val name: String,
    val description: String,
    val emoji: String,
    val bonus: Int,
    val predicate: (TrophyStats) -> Boolean,
)

/** The full set of Trophies and the pure rule for deciding which are unlocked for given stats. */
object TrophyCatalog {
    val ALL: List<TrophyDef> = listOf(
        TrophyDef("first_step", "First Step", "Earn your first Momentum", "🌱", 5) { it.momentum > 0 },
        TrophyDef("warmed_up", "Warmed Up", "Reach Level 5", "🔥", 10) { it.level >= 5 },
        TrophyDef("contender", "Contender", "Reach Level 26", "💪", 25) { it.level >= 26 },
        TrophyDef("all_star", "All-Star", "Reach Level 51", "⭐", 50) { it.level >= 51 },
        TrophyDef("champion", "Champion", "Reach Level 76", "👑", 75) { it.level >= 76 },
        TrophyDef("goat", "G.O.A.T.", "Reach Level 100", "🐐", 100) { it.level >= 100 },
        TrophyDef("deep_worker", "Deep Worker", "Focus for 60 minutes total", "🎧", 10) { it.totalFocusMinutes >= 60 },
        TrophyDef("marathon", "Marathon", "Focus for 10 hours total", "🏃", 40) { it.totalFocusMinutes >= 600 },
        TrophyDef("reflector", "Reflector", "Log 7 Check-Ins", "📓", 15) { it.checkInCount >= 7 },
        TrophyDef("closer", "Closer", "Complete 25 Hits", "✅", 20) { it.tasksCompleted >= 25 },
        TrophyDef("visionary", "Visionary", "Complete a Big Play", "🎯", 30) { it.bigPlaysCompleted >= 1 },
        TrophyDef("streak_master", "Streak Master", "Hit a 30-day streak", "⚡", 50) { it.bestStreak >= 30 },
        TrophyDef("iron_lung", "Iron Lung", "Hit a 60-day streak", "🫁", 80) { it.bestStreak >= 60 },
        TrophyDef("centurion", "Centurion", "Complete 100 Hits", "🛡️", 60) { it.tasksCompleted >= 100 },
    )

    private val byId: Map<String, TrophyDef> = ALL.associateBy { it.id }

    fun byId(id: String): TrophyDef? = byId[id]

    /** The ids of every Trophy whose rule is satisfied by [stats]. */
    fun evaluate(stats: TrophyStats): Set<String> =
        ALL.asSequence().filter { it.predicate(stats) }.map { it.id }.toSet()
}
