package com.hitit.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Bright "Aurora Glass" — a light, airy canvas with vivid accents and frosted-glass surfaces.
private val HitItColors = lightColorScheme(
    primary = AuroraViolet,
    onPrimary = Color.White,
    secondary = AuroraCyan,
    onSecondary = Color.White,
    tertiary = AuroraPink,
    onTertiary = Color.White,
    background = AuroraBase,
    onBackground = AuroraInk,
    surface = Color.White,
    onSurface = AuroraInk,
    surfaceVariant = AuroraMist,
    onSurfaceVariant = AuroraMuted,
    outline = AuroraMuted,
    error = AuroraPink,
)

// Punchy, athletic type scale: tight, bold, oversized display/headlines; readable body.
private val base = Typography()
private val HitItTypography = base.copy(
    displayLarge = base.displayLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-1.5).sp),
    displayMedium = base.displayMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-1).sp),
    headlineLarge = base.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineMedium = base.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.5).sp),
    headlineSmall = base.headlineSmall.copy(fontWeight = FontWeight.Bold),
    titleLarge = base.titleLarge.copy(fontWeight = FontWeight.Bold),
    titleMedium = base.titleMedium.copy(fontWeight = FontWeight.Bold),
    labelLarge = base.labelLarge.copy(fontWeight = FontWeight.Bold),
    labelMedium = base.labelMedium.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.8.sp),
)

/** A heavy, condensed style for big numbers (Momentum score, level, streak counts). */
val StatNumberStyle = TextStyle(fontWeight = FontWeight.Black, fontSize = 40.sp, letterSpacing = (-1.5).sp)

/** Wide-tracked condensed label for metric headers ("CURRENT MOMENTUM", "LV.5"). */
val AthleticLabelStyle = TextStyle(
    fontWeight = FontWeight.Bold,
    fontSize = 11.sp,
    letterSpacing = 1.8.sp,
)

/**
 * Tabular-figures number style: `tnum` freezes digit widths so rolling/odometer numbers don't
 * jitter the layout as they change. Used for the hero metrics.
 */
val TabularNumberStyle = TextStyle(
    fontWeight = FontWeight.Black,
    fontSize = 38.sp,
    letterSpacing = (-1).sp,
    fontFeatureSettings = "tnum",
)

@Composable
fun HitItTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HitItColors,
        typography = HitItTypography,
        content = content,
    )
}
