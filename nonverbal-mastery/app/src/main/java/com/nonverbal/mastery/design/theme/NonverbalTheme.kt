package com.nonverbal.mastery.design.theme

import androidx.compose.foundation.text.selection.LocalTextSelectionColors
import androidx.compose.foundation.text.selection.TextSelectionColors
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import com.nonverbal.mastery.design.tokens.DarkPalette
import com.nonverbal.mastery.design.tokens.DefaultReadingSize
import com.nonverbal.mastery.design.tokens.LightPalette
import com.nonverbal.mastery.design.tokens.LocalNvColors
import com.nonverbal.mastery.design.tokens.LocalNvType
import com.nonverbal.mastery.design.tokens.LocalReducedMotion
import com.nonverbal.mastery.design.tokens.NvColors
import com.nonverbal.mastery.design.tokens.NvType
import com.nonverbal.mastery.design.tokens.nvType

/** The accent of whatever category the reader is inside. Defaults to the app's ink blue. */
@Immutable
data class CategoryTint(val accent: Color)

val LocalCategoryTint = staticCompositionLocalOf<CategoryTint?> { null }

object Nv {
    val colors: NvColors
        @Composable @ReadOnlyComposable get() = LocalNvColors.current
    val type: NvType
        @Composable @ReadOnlyComposable get() = LocalNvType.current
    val reducedMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReducedMotion.current

    /** The current category accent, or the app accent outside any category. */
    val tint: Color
        @Composable @ReadOnlyComposable get() = LocalCategoryTint.current?.accent ?: LocalNvColors.current.accent
}

@Composable
fun NonverbalTheme(
    darkTheme: Boolean,
    reducedMotion: Boolean = false,
    readingSize: Int = DefaultReadingSize,
    content: @Composable () -> Unit,
) {
    val colors = if (darkTheme) DarkPalette else LightPalette
    val type = remember(readingSize) { nvType(readingSize) }
    val selection = remember(colors) {
        TextSelectionColors(handleColor = colors.accent, backgroundColor = colors.accent.copy(alpha = 0.22f))
    }
    CompositionLocalProvider(
        LocalNvColors provides colors,
        LocalNvType provides type,
        LocalReducedMotion provides reducedMotion,
        LocalTextSelectionColors provides selection,
    ) {
        MaterialTheme(colorScheme = colors.toMaterial(), content = content)
    }
}

/** Material components are used sparingly (slider, menus, text fields); map them onto the tokens. */
private fun NvColors.toMaterial(): ColorScheme {
    val base = if (isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = accent,
        onPrimary = onAccent,
        primaryContainer = accent.copy(alpha = 0.14f),
        onPrimaryContainer = ink,
        secondary = brass,
        onSecondary = onAccent,
        tertiary = live,
        background = background,
        onBackground = ink,
        surface = surface,
        onSurface = ink,
        surfaceVariant = surfaceSunken,
        onSurfaceVariant = inkSecondary,
        surfaceContainer = surface,
        surfaceContainerHigh = surface,
        surfaceContainerHighest = surfaceSunken,
        surfaceContainerLow = surface,
        surfaceContainerLowest = background,
        outline = divider,
        outlineVariant = divider,
        error = dontRed,
        onError = onAccent,
        scrim = scrim,
    )
}
