package com.spotifymashup.generator.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val DarkScheme = darkColorScheme(
    primary = BrandPrimary,
    onPrimary = Surface0,
    primaryContainer = BrandPrimaryDark,
    onPrimaryContainer = Surface0,
    secondary = BrandSecondary,
    onSecondary = Color.White,
    tertiary = BrandAccent,
    onTertiary = Color.White,
    background = Surface0,
    onBackground = TextPrimary,
    surface = Surface1,
    onSurface = TextPrimary,
    surfaceVariant = Surface2,
    onSurfaceVariant = TextSecondary,
    outline = SurfaceOutline,
)

private val LightScheme = lightColorScheme(
    primary = BrandPrimaryDark,
    onPrimary = Color.White,
    secondary = BrandSecondary,
    tertiary = BrandAccent,
    background = Color(0xFFFAFBFD),
    onBackground = Surface0,
    surface = Color.White,
    onSurface = Surface0,
)

@Composable
fun MashupTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S,
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && darkTheme -> dynamicDarkColorScheme(context).copy(primary = BrandPrimary)
        dynamicColor && !darkTheme -> dynamicLightColorScheme(context).copy(primary = BrandPrimaryDark)
        darkTheme -> DarkScheme
        else -> LightScheme
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = !darkTheme
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = MashupTypography,
        content = content,
    )
}
