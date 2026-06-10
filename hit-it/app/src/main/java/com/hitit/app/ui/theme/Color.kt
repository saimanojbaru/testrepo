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

// ── Cyber-Dreamcore (dark cosmic theme) ──────────────────────────────────────────────────────
// Deep navy → midnight-purple base with electric neon accents used sparingly + glow. Eye-friendly:
// the canvas is dark and desaturated, contrast lives only on key interactive elements. The Aurora*
// names are kept (every screen reads them) but now carry the dark-cosmic values, so the whole app
// re-themes from here. Glass = dark translucent fill over the nebula with a neon rim + glow shadow.
val AuroraBase = Color(0xFF0C0A1F)    // deep cosmic base (gradient bottom is CosmicTop below)
val AuroraInk = Color(0xFFEDEBFF)     // primary text — soft white-lilac, not harsh white
val AuroraMuted = Color(0xFF9A97C2)   // secondary text
val AuroraMist = Color(0xFF2A2550)    // dark surfaces / dividers / progress tracks
val AuroraViolet = Color(0xFF8B5CF6)  // primary accent (electric violet)
val AuroraCyan = Color(0xFF2DE2E2)    // secondary accent (electric cyan)
val AuroraPink = Color(0xFFFF2E97)    // tertiary accent (hot magenta)
val AuroraAmber = Color(0xFFFFB23E)   // warm accent (mood/energy)
val ToxicGreen = Color(0xFF7CFF4F)    // rare "peak" accent, used very sparingly

// Cosmic backdrop: a vertical nebula gradient + drifting desaturated blobs + particle flow.
val CosmicTop = Color(0xFF0C0A1F)
val CosmicBottom = Color(0xFF1A1633)
val NebulaViolet = Color(0xFF3A2A6E)
val NebulaTeal = Color(0xFF14414A)
val NebulaMagenta = Color(0xFF4A1E48)

// Dark glass surface (translucent fill over the nebula reads as smoky glass without real blur).
val CyberGlass = Color(0xFF1C1838)

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
