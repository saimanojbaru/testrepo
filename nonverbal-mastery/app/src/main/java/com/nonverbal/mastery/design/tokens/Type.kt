package com.nonverbal.mastery.design.tokens

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.nonverbal.mastery.R

/** Newsreader at its text optical size: the reading face. */
val Newsreader = FontFamily(
    Font(R.font.newsreader_regular, FontWeight.Normal),
    Font(R.font.newsreader_medium, FontWeight.Medium),
    Font(R.font.newsreader_semibold, FontWeight.SemiBold),
    Font(R.font.newsreader_italic, FontWeight.Normal, FontStyle.Italic),
    Font(R.font.newsreader_medium_italic, FontWeight.Medium, FontStyle.Italic),
)

/** Newsreader cut at its 72pt optical size: drop caps, titles, pull quotes, numerals. */
val NewsreaderDisplay = FontFamily(
    Font(R.font.newsreader_display_medium, FontWeight.Medium),
    Font(R.font.newsreader_display_italic, FontWeight.Normal, FontStyle.Italic),
)

/** Inter: the interface face. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Cap height of Newsreader as a fraction of the em, from its OS/2 table (1340/2000). */
const val NewsreaderCapHeight = 0.67f

private val Tight = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
private val NoPad = PlatformTextStyle(includeFontPadding = false)

private fun serif(size: TextUnit, lineHeight: Float, italic: Boolean = false, weight: FontWeight = FontWeight.Normal) =
    TextStyle(
        fontFamily = Newsreader,
        fontSize = size,
        lineHeight = (size.value * lineHeight).sp,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        fontWeight = weight,
        lineHeightStyle = Tight,
        platformStyle = NoPad,
    )

private fun sans(size: TextUnit, lineHeight: Float, weight: FontWeight = FontWeight.Normal, tracking: TextUnit = 0.sp) =
    TextStyle(
        fontFamily = Inter,
        fontSize = size,
        lineHeight = (size.value * lineHeight).sp,
        fontWeight = weight,
        letterSpacing = tracking,
        lineHeightStyle = Tight,
        platformStyle = NoPad,
    )

@Immutable
data class NvType(
    // Reading (scales with the reader's chosen body size)
    val body: TextStyle,
    val bodySmall: TextStyle,
    val hook: TextStyle,
    val pullQuote: TextStyle,
    val caption: TextStyle,
    val marginalia: TextStyle,
    // Editorial display
    val displayLarge: TextStyle,
    val display: TextStyle,
    val title: TextStyle,
    val titleSmall: TextStyle,
    val numeralLarge: TextStyle,
    // Interface
    val claim: TextStyle,
    val ui: TextStyle,
    val uiSmall: TextStyle,
    val button: TextStyle,
    val label: TextStyle,
    val labelSmall: TextStyle,
    val numeral: TextStyle,
) {
    val bodySize: Float get() = body.fontSize.value
}

const val DefaultReadingSize = 17

fun nvType(readingSize: Int = DefaultReadingSize): NvType {
    val b = readingSize.coerceIn(15, 22).toFloat()
    val scale = b / DefaultReadingSize
    return NvType(
        body = serif(b.sp, 1.65f),
        bodySmall = serif((15f * scale).sp, 1.55f),
        hook = serif((b * 1.24f).sp, 1.48f),
        pullQuote = TextStyle(
            fontFamily = NewsreaderDisplay,
            fontStyle = FontStyle.Italic,
            fontSize = (23f * scale).sp,
            lineHeight = (23f * scale * 1.38f).sp,
            textAlign = TextAlign.Center,
            lineHeightStyle = Tight,
            platformStyle = NoPad,
        ),
        caption = serif((13f * scale).sp, 1.45f, italic = true),
        marginalia = serif((15f * scale).sp, 1.55f, italic = true),
        displayLarge = TextStyle(
            fontFamily = NewsreaderDisplay, fontWeight = FontWeight.Medium,
            fontSize = 40.sp, lineHeight = 44.sp, letterSpacing = (-0.01).em,
            lineHeightStyle = Tight, platformStyle = NoPad,
        ),
        display = TextStyle(
            fontFamily = NewsreaderDisplay, fontWeight = FontWeight.Medium,
            fontSize = 34.sp, lineHeight = 39.sp, letterSpacing = (-0.01).em,
            lineHeightStyle = Tight, platformStyle = NoPad,
        ),
        title = TextStyle(
            fontFamily = NewsreaderDisplay, fontWeight = FontWeight.Medium,
            fontSize = 25.sp, lineHeight = 31.sp,
            lineHeightStyle = Tight, platformStyle = NoPad,
        ),
        titleSmall = serif(19.sp, 1.3f, weight = FontWeight.Medium),
        numeralLarge = TextStyle(
            fontFamily = NewsreaderDisplay, fontWeight = FontWeight.Medium,
            fontSize = 56.sp, lineHeight = 60.sp, fontFeatureSettings = "tnum",
            lineHeightStyle = Tight, platformStyle = NoPad,
        ),
        claim = sans(16.sp, 1.42f, FontWeight.SemiBold, tracking = 0.01.em),
        ui = sans(15.sp, 1.45f),
        uiSmall = sans(13.sp, 1.4f),
        button = sans(15.sp, 1.2f, FontWeight.Medium, tracking = 0.01.em),
        label = sans(11.sp, 1.3f, FontWeight.Medium, tracking = 0.08.em),
        labelSmall = sans(10.sp, 1.3f, FontWeight.SemiBold, tracking = 0.1.em),
        numeral = sans(13.sp, 1.3f, FontWeight.Medium).copy(fontFeatureSettings = "tnum"),
    )
}

val LocalNvType = staticCompositionLocalOf { nvType() }
