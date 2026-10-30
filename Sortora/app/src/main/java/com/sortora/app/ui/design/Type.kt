package com.sortora.app.ui.design

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.sortora.app.R

// Both fonts are bundled variable fonts (SIL OFL 1.1, see assets/licenses). Nothing is downloaded.

@OptIn(ExperimentalTextApi::class)
private fun inter(weight: Int, opticalSize: Float) = Font(
    R.font.inter_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opticalSize)),
)

@OptIn(ExperimentalTextApi::class)
private fun newsreader(weight: Int, opticalSize: Float) = Font(
    R.font.newsreader_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight), FontVariation.Setting("opsz", opticalSize)),
)

/** Inter at text optical size: body copy, labels, buttons. */
val InterText = FontFamily(inter(400, 14f), inter(500, 14f), inter(600, 14f), inter(700, 14f))

/** Inter at display optical size: tighter, for titles. */
val InterDisplay = FontFamily(inter(500, 28f), inter(600, 28f), inter(700, 28f))

/** Newsreader, an editorial serif, for headlines. */
val Serif = FontFamily(newsreader(400, 48f), newsreader(500, 48f), newsreader(600, 48f))

private val Trim = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(family: FontFamily, weight: Int, size: Float, line: Float, tracking: Float) = TextStyle(
    fontFamily = family,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
    lineHeightStyle = Trim,
)

val SortoraTypography = Typography(
    displayLarge = style(Serif, 400, 52f, 56f, -0.025f),
    displayMedium = style(Serif, 400, 42f, 46f, -0.022f),
    displaySmall = style(Serif, 400, 34f, 38f, -0.018f),
    headlineLarge = style(Serif, 400, 30f, 34f, -0.016f),
    headlineMedium = style(Serif, 400, 26f, 31f, -0.012f),
    headlineSmall = style(Serif, 500, 22f, 27f, -0.008f),
    titleLarge = style(InterDisplay, 600, 20f, 26f, -0.015f),
    titleMedium = style(InterDisplay, 600, 16f, 22f, -0.011f),
    titleSmall = style(InterText, 600, 14f, 20f, -0.006f),
    bodyLarge = style(InterText, 400, 16f, 24f, -0.009f),
    bodyMedium = style(InterText, 400, 14f, 21f, -0.005f),
    bodySmall = style(InterText, 400, 12.5f, 18f, 0f),
    labelLarge = style(InterText, 600, 14.5f, 20f, -0.004f),
    labelMedium = style(InterText, 500, 12.5f, 16f, 0.004f),
    labelSmall = style(InterText, 600, 11f, 14f, 0.06f),
)

/** Monospace for patterns and file names in examples. */
val MonoStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 19.sp, letterSpacing = 0.em)
