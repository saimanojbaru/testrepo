package com.hitit.app.ui.theme

import androidx.compose.ui.graphics.Color

// Core dark palette
val Background = Color(0xFF0E0E12)
val Surface = Color(0xFF16161D)
val SurfaceVariant = Color(0xFF20212B)
val OnDark = Color(0xFFECECF1)
val OnDarkMuted = Color(0xFF9A9AA8)

// Neon accents (action / athletic vibe)
val NeonCyan = Color(0xFF33E1FF)
val NeonLime = Color(0xFFB8FF3C)
val NeonMagenta = Color(0xFFFF4D8D)

// Heatmap intensity ramp for "The Grid" (0..4)
val Heat0 = Color(0xFF1C1C24)
val Heat1 = Color(0xFF0E5A3A)
val Heat2 = Color(0xFF13855A)
val Heat3 = Color(0xFF21C07E)
val Heat4 = Color(0xFF59F0A8)

/** Heatmap color for an intensity bucket 0..4. */
fun heatColor(intensity: Int): Color = when (intensity.coerceIn(0, 4)) {
    1 -> Heat1
    2 -> Heat2
    3 -> Heat3
    4 -> Heat4
    else -> Heat0
}

/** Parse a "#RRGGBB" or "#AARRGGBB" hex string into a Color, falling back on failure. */
fun parseHexColor(hex: String, fallback: Color): Color = runCatching {
    val cleaned = hex.removePrefix("#")
    val value = cleaned.toLong(16)
    val argb = when (cleaned.length) {
        6 -> 0xFF000000L or value
        8 -> value
        else -> return fallback
    }
    Color(argb.toInt())
}.getOrDefault(fallback)

/** Suggested accent colors for new Reps. */
val RepPalette = listOf(
    "#33E1FF", "#B8FF3C", "#FF4D8D", "#FFC857", "#A78BFA", "#4ADE80", "#FB7185", "#38BDF8",
)
