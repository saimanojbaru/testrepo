package com.spotifymashup.generator.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val sans = FontFamily.SansSerif

val MashupTypography = Typography(
    displayLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Black,    fontSize = 48.sp, letterSpacing = (-1).sp),
    displayMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold,    fontSize = 34.sp, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold,   fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold,   fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold,  fontSize = 16.sp),
    bodyLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal,      fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal,     fontSize = 14.sp),
    bodySmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Normal,      fontSize = 12.sp),
    labelLarge = TextStyle(fontFamily = sans, fontWeight = FontWeight.SemiBold,   fontSize = 14.sp, letterSpacing = 0.4.sp),
    labelSmall = TextStyle(fontFamily = sans, fontWeight = FontWeight.Bold,       fontSize = 11.sp, letterSpacing = 1.5.sp),
)
