package com.nonverbal.mastery.design.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

/**
 * Two palettes, "Paper" and "Ink". The specified hues are kept for graphics. Where a hue is
 * also used for small text and fell below WCAG AA (4.5:1) on its background, a text variant
 * of the same hue is provided: [inkTertiary], [brassText], [liveText], [doText], [dontText].
 * Measured contrasts are noted beside each.
 */
@Immutable
data class NvColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceSunken: Color,
    val ink: Color,            // reading text, AAA
    val inkSecondary: Color,
    val inkTertiary: Color,    // metadata text, AA
    val inkFaint: Color,       // decoration only, never text
    val divider: Color,
    val accent: Color,
    val onAccent: Color,
    val brass: Color,          // graphics: rings, diamonds, progress
    val brassText: Color,
    val live: Color,           // graphics: the current level's dot
    val liveText: Color,
    val doGreen: Color,
    val doText: Color,
    val dontRed: Color,
    val dontText: Color,
    val figureFill: Color,
    val figureFar: Color,      // limbs on the far side of the body
    val figureLine: Color,     // silhouette hairline, >= 3:1 against the page
    val scrim: Color,
)

val LightPalette = NvColors(
    isDark = false,
    background = Color(0xFFF7F4EE),
    surface = Color(0xFFFDFBF7),
    surfaceSunken = Color(0xFFEFEAE1),
    ink = Color(0xFF1B1A17),          // 15.9:1
    inkSecondary = Color(0xFF5C5852), // 6.4:1
    inkTertiary = Color(0xFF6F6A62),  // 4.9:1 (spec #8A857D measured 3.3:1)
    inkFaint = Color(0xFF8A857D),
    divider = Color(0xFFE8E3D9),
    accent = Color(0xFF2A3A5C),       // 10.3:1
    onAccent = Color(0xFFFDFBF7),
    brass = Color(0xFFB08442),
    brassText = Color(0xFF86622A),    // 5.0:1 (spec #B08442 measured 3.1:1)
    live = Color(0xFF4F6BED),
    liveText = Color(0xFF3F59D6),     // 5.3:1
    doGreen = Color(0xFF3E7B54),
    doText = Color(0xFF367049),       // 5.4:1
    dontRed = Color(0xFFB04A3E),
    dontText = Color(0xFFA3443A),     // 5.6:1
    figureFill = Color(0xFFC9C2B6),
    figureFar = Color(0xFFB7AFA2),
    figureLine = Color(0xFF8A8275),   // 3.5:1
    scrim = Color(0x991B1A17),
)

val DarkPalette = NvColors(
    isDark = true,
    background = Color(0xFF121317),
    surface = Color(0xFF1B1D23),
    surfaceSunken = Color(0xFF17181D),
    ink = Color(0xFFECE7DD),          // 15.1:1
    inkSecondary = Color(0xFFA7A39B), // 7.4:1
    inkTertiary = Color(0xFF938E86),  // 5.7:1 (spec #6E6A63 measured 3.5:1)
    inkFaint = Color(0xFF6E6A63),
    divider = Color(0xFF2A2C33),
    accent = Color(0xFF8FA3D9),
    onAccent = Color(0xFF121317),
    brass = Color(0xFFD9B36C),
    brassText = Color(0xFFD9B36C),    // 9.4:1
    live = Color(0xFF8CA0FF),
    liveText = Color(0xFF8CA0FF),
    doGreen = Color(0xFF7FB68A),
    doText = Color(0xFF7FB68A),
    dontRed = Color(0xFFD99086),
    dontText = Color(0xFFD99086),
    figureFill = Color(0xFF3A3D45),
    figureFar = Color(0xFF30333A),
    figureLine = Color(0xFF7A7E8A),   // 4.6:1
    scrim = Color(0xB3000000),
)

val LocalNvColors = staticCompositionLocalOf { LightPalette }

/** Parses "#RRGGBB" or "#AARRGGBB". Falls back to the ink accent rather than crashing. */
fun parseHex(hex: String, fallback: Color = LightPalette.accent): Color {
    val clean = hex.trim().removePrefix("#")
    val value = clean.toLongOrNull(16) ?: return fallback
    return when (clean.length) {
        6 -> Color(0xFF000000 or value)
        8 -> Color(value)
        else -> fallback
    }
}

/**
 * Category accents are authored once, for paper. On ink they are lifted toward the reading
 * colour so they keep their identity and still read as text.
 */
fun categoryAccent(hex: String, colors: NvColors): Color {
    val base = parseHex(hex)
    return if (colors.isDark) lerp(base, colors.ink, 0.42f) else base
}

/** A whisper of a colour laid over the page: used for tinted surfaces. */
fun Color.tint(on: Color, amount: Float): Color = lerp(on, this, amount)

fun annotationColor(token: String, colors: NvColors): Color = when (token) {
    "accent-warm" -> colors.brass
    "accent-cool" -> colors.live
    "do-green" -> colors.doGreen
    "don't-red", "dont-red" -> colors.dontRed
    else -> colors.accent
}
