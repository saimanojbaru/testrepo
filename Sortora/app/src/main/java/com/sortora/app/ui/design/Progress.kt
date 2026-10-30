package com.sortora.app.ui.design

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Indeterminate spinner: one arc that breathes (grows and shrinks) while it turns, so it reads as
 * patient rather than mechanical.
 */
@Composable
fun FluidSpinner(
    modifier: Modifier = Modifier,
    size: Dp = 28.dp,
    color: Color = Sortora.colors.accent,
    track: Color = Sortora.colors.sunken,
    strokeWidth: Dp = size / 9,
) {
    val transition = rememberInfiniteTransition(label = "spinner")
    val turn by transition.animateFloat(0f, 360f, infiniteRepeatable(tween(1300, easing = LinearEasing)), label = "turn")
    val breath by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1100, easing = Motion.EaseInOutSine), RepeatMode.Reverse), label = "breath")
    Canvas(
        modifier
            .size(size)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo.Indeterminate },
    ) {
        val stroke = strokeWidth.toPx()
        val inset = stroke / 2
        val arcSize = Size(this.size.width - stroke, this.size.height - stroke)
        drawArc(track, 0f, 360f, false, Offset(inset, inset), arcSize, style = Stroke(stroke))
        val sweep = 40f + 200f * breath
        rotate(turn + breath * 120f) {
            drawArc(color, -90f, sweep, false, Offset(inset, inset), arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
        }
    }
}

/**
 * Determinate ring. The arc fades from soft to full accent along its length, with a small glowing
 * head; progress changes glide on a spring instead of jumping.
 */
@Composable
fun FluidRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 168.dp,
    strokeWidth: Dp = 10.dp,
    content: @Composable BoxScope.() -> Unit = {},
) {
    val c = Sortora.colors
    val animated by animateFloatAsState(progress.coerceIn(0f, 1f), spring(dampingRatio = 1f, stiffness = 50f), label = "ring")
    val transition = rememberInfiniteTransition(label = "ringGlow")
    val shimmer by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(2400, easing = Motion.EaseInOutSine), RepeatMode.Reverse), label = "glow")
    Box(
        modifier
            .size(size)
            .semantics { progressBarRangeInfo = ProgressBarRangeInfo(animated, 0f..1f) },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(size)) {
            val stroke = strokeWidth.toPx()
            val inset = stroke / 2 + 6.dp.toPx()
            val arcSize = Size(this.size.width - inset * 2, this.size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            drawArc(c.sunken, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
            val sweep = 360f * animated
            if (sweep > 0.5f) {
                rotate(-90f) {
                    val brush = Brush.sweepGradient(
                        0f to c.accent.copy(alpha = 0.35f),
                        (animated * 0.999f).coerceAtLeast(0.001f) to c.accent,
                        1f to c.accent.copy(alpha = 0.35f),
                        center = center,
                    )
                    drawArc(brush, 0f, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
                }
                // Glowing head.
                val angle = (sweep - 90f) * (PI / 180f).toFloat()
                val radius = arcSize.width / 2
                val head = Offset(center.x + radius * cos(angle), center.y + radius * sin(angle))
                val glowRadius = stroke * (1.6f + 0.5f * shimmer)
                drawCircle(Brush.radialGradient(listOf(c.accentGlow, Color.Transparent), head, glowRadius * 1.6f), glowRadius * 1.6f, head)
                drawCircle(c.accent, stroke * 0.5f, head)
                drawCircle(Color.White.copy(alpha = 0.55f), stroke * 0.18f, head)
            }
        }
        content()
    }
}

/**
 * Linear progress capsule. A soft highlight drifts across the filled part while work continues.
 * Pass null for an indeterminate bar.
 */
@Composable
fun FluidBar(progress: Float?, modifier: Modifier = Modifier, height: Dp = 6.dp) {
    val c = Sortora.colors
    val animated by animateFloatAsState((progress ?: 0f).coerceIn(0f, 1f), spring(dampingRatio = 1f, stiffness = 80f), label = "bar")
    val transition = rememberInfiniteTransition(label = "barShimmer")
    val sweep by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(1600, easing = Motion.EaseInOutSine)), label = "sweep")
    Canvas(
        modifier
            .fillMaxWidth()
            .height(height)
            .semantics {
                progressBarRangeInfo = if (progress == null) ProgressBarRangeInfo.Indeterminate else ProgressBarRangeInfo(animated, 0f..1f)
            },
    ) {
        val r = CornerRadius(size.height / 2)
        drawRoundRect(c.sunken, cornerRadius = r)
        if (progress == null) {
            val w = size.width * 0.32f
            val x = -w + (size.width + w) * sweep
            clipRect {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(c.accent.copy(alpha = 0f), c.accent, c.accent.copy(alpha = 0f)), startX = x, endX = x + w),
                    topLeft = Offset(x, 0f), size = Size(w, size.height), cornerRadius = r,
                )
            }
            return@Canvas
        }
        val filled = size.width * animated
        if (filled <= 0f) return@Canvas
        drawRoundRect(c.accent, size = Size(filled.coerceAtLeast(size.height), size.height), cornerRadius = r)
        if (animated < 1f) {
            val w = (filled * 0.45f).coerceAtLeast(size.height * 4)
            val x = -w + (filled + w) * sweep
            clipRect(right = filled) {
                drawRect(Brush.horizontalGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.35f), Color.White.copy(alpha = 0f)), startX = x, endX = x + w))
            }
        }
    }
}

/** Placeholder block that pulses softly while content loads. */
@Composable
fun SkeletonBlock(modifier: Modifier = Modifier, shape: Shape = Radius.Sm) {
    val c = Sortora.colors
    val transition = rememberInfiniteTransition(label = "skeleton")
    val pulse by transition.animateFloat(0.55f, 1f, infiniteRepeatable(tween(900, easing = Motion.EaseInOutSine), RepeatMode.Reverse), label = "pulse")
    Box(
        modifier
            .clip(shape)
            .drawBehind { drawRect(c.sunken.copy(alpha = pulse)) },
    )
}
