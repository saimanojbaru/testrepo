package com.hitit.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

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

private val HitItTypography = Typography()

@Composable
fun HitItTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = HitItColors,
        typography = HitItTypography,
        content = content,
    )
}
