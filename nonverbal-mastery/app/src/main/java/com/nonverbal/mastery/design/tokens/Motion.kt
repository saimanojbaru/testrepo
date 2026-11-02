package com.nonverbal.mastery.design.tokens

import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

object Motion {
    const val DURATION = 240
    const val DURATION_SHORT = 160
    const val DURATION_LONG = 420
    const val MASTERY_TRACE = 800
    const val LAUNCH_MAX = 600

    /** Entering: decisive start, long settle. */
    val Enter = CubicBezierEasing(0.2f, 0.0f, 0.0f, 1.0f)

    /** Exiting: gentle start, quick departure. */
    val Exit = CubicBezierEasing(0.4f, 0.0f, 1.0f, 1.0f)
}

/**
 * True when the reader (or the system's animator duration scale) asks for stillness.
 * Every animation in the app reads this and has a static alternative.
 */
val LocalReducedMotion = staticCompositionLocalOf { false }

@Composable
@ReadOnlyComposable
fun reducedMotion(): Boolean = LocalReducedMotion.current

/** The default entering tween, or a snap when motion is reduced. */
@Composable
@ReadOnlyComposable
fun <T> enterSpec(durationMillis: Int = Motion.DURATION, delayMillis: Int = 0): FiniteAnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else tween(durationMillis, delayMillis, Motion.Enter)

@Composable
@ReadOnlyComposable
fun <T> exitSpec(durationMillis: Int = Motion.DURATION): AnimationSpec<T> =
    if (LocalReducedMotion.current) snap() else tween(durationMillis, 0, Motion.Exit)
