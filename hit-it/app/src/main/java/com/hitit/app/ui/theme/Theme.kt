package com.hitit.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val HitItColors = darkColorScheme(
    primary = NeonCyan,
    onPrimary = Color(0xFF042029),
    secondary = NeonLime,
    onSecondary = Color(0xFF18260A),
    tertiary = NeonMagenta,
    background = Background,
    onBackground = OnDark,
    surface = Surface,
    onSurface = OnDark,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnDarkMuted,
    error = NeonMagenta,
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

@Composable
fun HitItTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HitItColors,
        typography = HitItTypography,
        content = content,
    )
}
