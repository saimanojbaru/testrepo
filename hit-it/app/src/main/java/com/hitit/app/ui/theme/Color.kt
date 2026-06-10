package com.hitit.app.ui.theme

import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

// Layered dark palette — NOT pure black, so cards read as elevated surfaces against the background.
val Background = Color(0xFF0B0B10)
val Surface = Color(0xFF17171F)      // elevated card
val SurfaceHigh = Color(0xFF1F202B)  // higher elevation / inputs
val SurfaceVariant = Color(0xFF262732)
val OnDark = Color(0xFFF4F4F8)
val OnDarkMuted = Color(0xFF9A9AAB)

// Neon accents (action / athletic vibe)
val NeonCyan = Color(0xFF00E5C0)     // primary — matches the "Start Lock In" energy
val NeonLime = Color(0xFFB8FF3C)
val NeonMagenta = Color(0xFFFF4D8D)
val NeonViolet = Color(0xFFA78BFA)
val NeonAmber = Color(0xFFFFC857)

// Hero gradient (cyan -> violet -> magenta) for the dashboard momentum hero.
val HeroGradient = Brush.linearGradient(listOf(Color(0xFF00E5C0), Color(0xFF7C5CFF), Color(0xFFFF4D8D)))

// ── Bright Aurora ("Aurora Glass" light theme) ───────────────────────────────────────────────
// A near-white canvas the drifting aurora paints onto, deep-but-soft ink for text, and vivid
// accents that pop on light. Frosted glass = translucent white over the aurora (no real blur on
// minSdk 26 — the milky fill reads as frost in light mode).
val AuroraBase = Color(0xFFF7F7FE)    // app background (the aurora's canvas)
val AuroraInk = Color(0xFF1B1C30)     // primary text — soft indigo-charcoal, not pure black
val AuroraMuted = Color(0xFF73758F)   // secondary text
val AuroraMist = Color(0xFFECEDF8)    // low surfaces / dividers / tracks
val AuroraViolet = Color(0xFF7C5CFF)  // primary accent
val AuroraCyan = Color(0xFF00C2A8)    // secondary accent (deepened for contrast on white)
val AuroraPink = Color(0xFFFF4D8D)    // tertiary accent
val AuroraAmber = Color(0xFFFF9E3D)

// Soft aurora blob tints (drawn as big radial gradients on the near-white base, drifting slowly).
val AuroraBlobCyan = Color(0xFF5BE9D5)
val AuroraBlobViolet = Color(0xFFB49BFF)
val AuroraBlobPink = Color(0xFFFF9DC0)
val AuroraBlobPeach = Color(0xFFFFD3A8)

// Heatmap intensity ramp for "The Grid" (0..4) — a cohesive 2026 "tech-glow" ladder:
// charcoal base -> calm recovery teal -> rich emerald -> electric cyan -> peak cyber-purple.
val Heat0 = Color(0xFF16161E)
val Heat1 = Color(0xFF0E6E78)
val Heat2 = Color(0xFF12A36B)
val Heat3 = Color(0xFF00E5C0)
val Heat4 = Color(0xFF9D5BFF)

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
    "#00E5C0", "#B8FF3C", "#FF4D8D", "#FFC857", "#A78BFA", "#4ADE80", "#FB7185", "#38BDF8",
)
