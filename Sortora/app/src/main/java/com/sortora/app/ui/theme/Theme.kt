package com.sortora.app.ui.theme

import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import com.sortora.app.domain.model.ThemeMode
import com.sortora.app.ui.design.DarkPalette
import com.sortora.app.ui.design.LightPalette
import com.sortora.app.ui.design.LocalSortoraColors
import com.sortora.app.ui.design.SortoraShapes
import com.sortora.app.ui.design.SortoraTypography
import com.sortora.app.ui.design.toColorScheme

/** True when the user turned animations off (Developer options or Accessibility → Remove animations). */
val LocalReduceMotion = staticCompositionLocalOf { false }

@Composable
fun isDarkTheme(themeMode: ThemeMode): Boolean = when (themeMode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun SortoraTheme(
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = isDarkTheme(themeMode)
    val context = LocalContext.current
    val base = if (dark) DarkPalette else LightPalette
    // Dynamic colour only swaps the accent: the warm neutrals stay, so the app keeps its character.
    val colors = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val scheme = if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        base.copy(
            accent = scheme.primary,
            onAccent = scheme.onPrimary,
            accentSoft = lerp(base.canvas, scheme.primary, if (dark) 0.18f else 0.12f),
            accentGlow = scheme.primary.copy(alpha = 0.24f),
        )
    } else {
        base
    }
    val reduceMotion = remember(context) {
        runCatching { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
            .getOrDefault(false)
    }
    CompositionLocalProvider(LocalSortoraColors provides colors, LocalReduceMotion provides reduceMotion) {
        MaterialTheme(
            colorScheme = colors.toColorScheme(),
            typography = SortoraTypography,
            shapes = SortoraShapes,
            content = content,
        )
    }
}
