package com.hitit.domain.trophy

/** A snapshot of the player's stats used to evaluate which Trophies are unlocked. */
data class TrophyStats(
    val momentum: Long = 0,
    val level: Int = 1,
    val totalFocusMinutes: Int = 0,
    val checkInCount: Int = 0,
    val tasksCompleted: Int = 0,
    val bigPlaysCompleted: Int = 0,
    val bestStreak: Int = 0,
)
