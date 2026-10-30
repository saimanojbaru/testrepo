package com.sortora.app.ui.design

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.SpringSpec
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

/** 4pt spacing scale. Screens use [gutter] at the edges and [section] between groups. */
object Space {
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
    val gutter = 20.dp
    val section = 28.dp
}

/**
 * One radius family. Nested shapes step down one size (a card at [lg] holds thumbnails at [sm]),
 * which keeps corners optically concentric.
 */
object Radius {
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 22.dp
    val xl = 28.dp
    val xxl = 36.dp

    val Xs = RoundedCornerShape(xs)
    val Sm = RoundedCornerShape(sm)
    val Md = RoundedCornerShape(md)
    val Lg = RoundedCornerShape(lg)
    val Xl = RoundedCornerShape(xl)
    val Xxl = RoundedCornerShape(xxl)
    val Pill = RoundedCornerShape(percent = 50)
}

val SortoraShapes = Shapes(
    extraSmall = Radius.Xs,
    small = Radius.Sm,
    medium = Radius.Md,
    large = Radius.Lg,
    extraLarge = Radius.Xl,
)

/** Motion vocabulary. Springs for anything the user touches; tweens only for colour and fades. */
object Motion {
    /** Long, soft deceleration: things settling into place. */
    val EaseOutQuint = CubicBezierEasing(0.22f, 1f, 0.36f, 1f)
    /** Symmetric, for loops and breathing. */
    val EaseInOutSine = CubicBezierEasing(0.37f, 0f, 0.63f, 1f)
    val EaseOutBack = CubicBezierEasing(0.34f, 1.4f, 0.64f, 1f)

    /** Layout and position changes. */
    fun <T> gentle(): SpringSpec<T> = spring(dampingRatio = 0.86f, stiffness = 260f)
    /** Press feedback, toggles. */
    fun <T> snappy(): SpringSpec<T> = spring(dampingRatio = 0.72f, stiffness = 700f)
    /** Arrivals and celebrations: a visible, single overshoot. */
    fun <T> bouncy(): SpringSpec<T> = spring(dampingRatio = 0.5f, stiffness = 420f)
    /** Slow drift for ambient movement. */
    fun <T> lazy(): SpringSpec<T> = spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 60f)

    fun <T> fade(durationMs: Int = 260): FiniteAnimationSpec<T> = tween(durationMs, easing = EaseOutQuint)
}
