package com.hitit.app.lockin

import androidx.compose.ui.graphics.Color

/** A visual "Zone" theme for a Lock In session. */
data class FocusZone(val id: String, val label: String, val color: Color)

object FocusZones {
    const val DEFAULT = "Deep"

    val ALL = listOf(
        FocusZone("Deep", "Deep Focus", Color(0xFF1E2A78)),
        FocusZone("Forest", "Forest", Color(0xFF14532D)),
        FocusZone("LoFi", "Lo-Fi", Color(0xFF4A148C)),
        FocusZone("Night", "Night", Color(0xFF0E1430)),
    )

    fun color(id: String): Color = ALL.firstOrNull { it.id == id }?.color ?: ALL.first().color

    fun label(id: String): String = ALL.firstOrNull { it.id == id }?.label ?: id
}
