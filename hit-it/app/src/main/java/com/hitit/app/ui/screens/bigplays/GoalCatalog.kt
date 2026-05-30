package com.hitit.app.ui.screens.bigplays

/** Categories and horizons for Big Plays (UI labels + stored keys). */
object GoalCatalog {
    val CATEGORIES = listOf("Health", "Career", "Finance", "Learning", "Relationships", "Personal")

    val HORIZONS = listOf(
        "QUARTER" to "Quarter",
        "YEAR" to "Year",
        "TWO_YEAR" to "2-Year",
        "THREE_YEAR" to "3-Year",
    )

    fun horizonLabel(key: String): String = HORIZONS.firstOrNull { it.first == key }?.second ?: key
}
