package com.nonverbal.mastery.design.tokens

import androidx.compose.ui.unit.dp

/** Base unit 4dp. Every distance in the app is a multiple of it. */
object Space {
    val x1 = 4.dp
    val x2 = 8.dp
    val x3 = 12.dp
    val x4 = 16.dp
    val x5 = 20.dp
    val x6 = 24.dp
    val x7 = 28.dp
    val x8 = 32.dp
    val x10 = 40.dp
    val x12 = 48.dp
    val x14 = 56.dp
    val x16 = 64.dp
    val x20 = 80.dp

    /** Screen gutters. */
    val readingGutter = 24.dp
    val listGutter = 20.dp

    /** Vertical rhythm inside reading content. */
    val paragraph = 16.dp
    val aboveHeading = 28.dp
    val sectionBreak = 40.dp
    val aroundPullQuote = 56.dp

    /** Minimum interactive target. */
    val touchTarget = 48.dp

    /** The reading measure, in characters. */
    const val MEASURE_CHARS = 62
}
