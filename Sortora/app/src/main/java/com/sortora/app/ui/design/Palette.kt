package com.sortora.app.ui.design

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Sortora's palette: warm, soft neutrals and a single clay accent. Everything else (success,
 * warning, danger) is muted so the accent stays the only voice on screen.
 *
 * Text pairs meet WCAG AA (4.5:1) on canvas and surface; [inkTertiary] is for hints and
 * disabled text only.
 */
@Immutable
data class SortoraColors(
    val dark: Boolean,
    /** Page background. */
    val canvas: Color,
    /** Cards and sheets. */
    val surface: Color,
    /** Pressed / inset areas, tracks, input fields. */
    val sunken: Color,
    /** Floating things above surface (menus, the edit card). */
    val raised: Color,
    /** Tint laid over blurred content on glass bars. */
    val glass: Color,
    /** 1px borders and dividers. */
    val hairline: Color,
    /** Top-edge highlight on glass and raised surfaces. */
    val highlight: Color,
    val ink: Color,
    val inkSecondary: Color,
    val inkTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    /** Accent at low strength: selected pills, progress tracks, glows. */
    val accentSoft: Color,
    val accentGlow: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val dangerSoft: Color,
    /** Colour of soft shadows (never pure black, so depth stays warm). */
    val shadow: Color,
    val scrim: Color,
    /** Placeholder behind thumbnails while they decode. */
    val thumbnail: Color,
)

val LightPalette = SortoraColors(
    dark = false,
    canvas = Color(0xFFFAF9F6),
    surface = Color(0xFFFFFFFF),
    sunken = Color(0xFFF2F0EB),
    raised = Color(0xFFFFFFFF),
    glass = Color(0xFFFAF9F6).copy(alpha = 0.8f),
    hairline = Color(0xFF1F1E1B).copy(alpha = 0.08f),
    highlight = Color.White.copy(alpha = 0.9f),
    ink = Color(0xFF1F1E1B),
    inkSecondary = Color(0xFF6B675F),
    inkTertiary = Color(0xFF8F8A80),
    accent = Color(0xFFB5583A),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFF3E4DC),
    accentGlow = Color(0xFFB5583A).copy(alpha = 0.22f),
    success = Color(0xFF4F7D5C),
    warning = Color(0xFF9A6B26),
    danger = Color(0xFFB3463F),
    dangerSoft = Color(0xFFF6E3E0),
    shadow = Color(0xFF3B2A1E),
    scrim = Color(0xFF1F1E1B).copy(alpha = 0.28f),
    thumbnail = Color(0xFFEDEAE3),
)

val DarkPalette = SortoraColors(
    dark = true,
    canvas = Color(0xFF181715),
    surface = Color(0xFF201F1C),
    sunken = Color(0xFF141311),
    raised = Color(0xFF292724),
    glass = Color(0xFF181715).copy(alpha = 0.76f),
    hairline = Color.White.copy(alpha = 0.075f),
    highlight = Color.White.copy(alpha = 0.06f),
    ink = Color(0xFFF1EEE8),
    inkSecondary = Color(0xFFA39E95),
    inkTertiary = Color(0xFF7A766E),
    accent = Color(0xFFE6906E),
    onAccent = Color(0xFF1D140F),
    accentSoft = Color(0xFF3A2A22),
    accentGlow = Color(0xFFE6906E).copy(alpha = 0.26f),
    success = Color(0xFF8DBB98),
    warning = Color(0xFFD9A85C),
    danger = Color(0xFFE57F74),
    dangerSoft = Color(0xFF3B2220),
    shadow = Color(0xFF000000),
    scrim = Color.Black.copy(alpha = 0.5f),
    thumbnail = Color(0xFF2A2825),
)

val LocalSortoraColors = staticCompositionLocalOf { LightPalette }

/** Maps the palette onto Material slots so stock components (sheets, dialogs, switches) match. */
fun SortoraColors.toColorScheme(): ColorScheme {
    val base = if (dark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accentSoft,
        onPrimaryContainer = ink,
        inversePrimary = accent,
        secondary = inkSecondary,
        onSecondary = canvas,
        secondaryContainer = sunken,
        onSecondaryContainer = ink,
        tertiary = warning,
        onTertiary = canvas,
        tertiaryContainer = accentSoft,
        onTertiaryContainer = ink,
        background = canvas,
        onBackground = ink,
        surface = canvas,
        onSurface = ink,
        surfaceVariant = sunken,
        onSurfaceVariant = inkSecondary,
        surfaceTint = Color.Transparent,
        inverseSurface = ink,
        inverseOnSurface = canvas,
        error = danger,
        onError = if (dark) Color(0xFF2A0F0C) else Color.White,
        errorContainer = dangerSoft,
        onErrorContainer = ink,
        outline = inkTertiary,
        outlineVariant = hairline,
        scrim = scrim,
        surfaceBright = raised,
        surfaceDim = sunken,
        surfaceContainerLowest = surface,
        surfaceContainerLow = surface,
        surfaceContainer = raised,
        surfaceContainerHigh = raised,
        surfaceContainerHighest = sunken,
    )
}
