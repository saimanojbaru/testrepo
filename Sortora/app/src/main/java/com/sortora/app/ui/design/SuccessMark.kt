package com.sortora.app.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * The finish moment: a disc swells in on a spring, the tick draws itself, and a small ring of
 * particles drifts outward and fades. Plays once per [key]; a single confirm haptic lands with it.
 */
@Composable
fun SuccessMark(modifier: Modifier = Modifier, size: Dp = 108.dp, key: Any = Unit, tone: Color = Sortora.colors.accent) {
    val c = Sortora.colors
    val reduceMotion = Sortora.reduceMotion
    val haptics = LocalHapticFeedback.current
    val disc = remember(key) { Animatable(if (reduceMotion) 1f else 0f) }
    val tick = remember(key) { Animatable(if (reduceMotion) 1f else 0f) }
    val burst = remember(key) { Animatable(if (reduceMotion) 1f else 0f) }
    val onTone = if (tone == c.accent) c.onAccent else Color.White

    LaunchedEffect(key) {
        if (reduceMotion) return@LaunchedEffect
        launch { disc.animateTo(1f, spring(dampingRatio = 0.48f, stiffness = 260f)) }
        delay(170)
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        launch { burst.animateTo(1f, tween(900, easing = Motion.EaseOutQuint)) }
        tick.animateTo(1f, tween(420, easing = Motion.EaseOutQuint))
    }

    Canvas(modifier.size(size)) {
        val r = this.size.minDimension * 0.34f
        // Halo.
        drawCircle(Brush.radialGradient(listOf(c.accentGlow, Color.Transparent), center, r * 2.1f), r * 2.1f * disc.value.coerceIn(0f, 1.2f))
        // Particles.
        val b = burst.value
        if (b in 0.001f..0.999f) {
            repeat(12) { i ->
                val angle = (i / 12f) * 2 * PI.toFloat() + 0.26f
                val dist = r * (1.15f + 0.75f * b) + (if (i % 2 == 0) r * 0.12f else 0f)
                val p = Offset(center.x + cos(angle) * dist, center.y + sin(angle) * dist)
                drawCircle(tone.copy(alpha = (1f - b) * 0.8f), r * (if (i % 3 == 0) 0.07f else 0.045f) * (1f - b * 0.5f), p)
            }
        }
        scale(disc.value, center) {
            drawCircle(Brush.verticalGradient(listOf(lerpWhite(tone, 0.12f), tone), center.y - r, center.y + r), r)
            drawCircle(Color.White.copy(alpha = 0.18f), r - 1.dp.toPx(), style = Stroke(1.dp.toPx()))
        }
        if (tick.value > 0f) {
            val path = Path().apply {
                moveTo(center.x - r * 0.42f, center.y + r * 0.02f)
                lineTo(center.x - r * 0.12f, center.y + r * 0.32f)
                lineTo(center.x + r * 0.46f, center.y - r * 0.3f)
            }
            val measure = PathMeasure().apply { setPath(path, false) }
            val partial = Path()
            measure.getSegment(0f, measure.length * tick.value, partial, true)
            drawPath(partial, onTone, style = Stroke(r * 0.15f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun lerpWhite(color: Color, amount: Float) = androidx.compose.ui.graphics.lerp(color, Color.White, amount)
