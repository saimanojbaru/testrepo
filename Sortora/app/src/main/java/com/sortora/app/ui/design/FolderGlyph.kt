package com.sortora.app.ui.design

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

/**
 * A drawn folder that reacts to what's happening to it.
 *
 * - [pulseKey]: bump it each time a file lands. The front flap tips open, the papers inside lift,
 *   the folder bounces once and a soft ring of light spreads out. Rapid arrivals keep it open.
 * - [open]: hold it open (e.g. while selected).
 * - [active]: warms the paper toward the accent.
 */
@Composable
fun FolderGlyph(
    modifier: Modifier = Modifier,
    width: Dp = 64.dp,
    pulseKey: Int = 0,
    open: Boolean = false,
    active: Boolean = false,
    icon: ImageVector? = null,
) {
    val c = Sortora.colors
    val reduceMotion = Sortora.reduceMotion
    val held = remember { Animatable(if (open) 1f else 0f) }
    val flap = remember { Animatable(0f) }
    val bounce = remember { Animatable(1f) }
    val ring = remember { Animatable(1f) }
    val warmth = remember { Animatable(if (active) 1f else 0f) }

    LaunchedEffect(open) { held.animateTo(if (open) 1f else 0f, Motion.bouncy()) }
    LaunchedEffect(active) { warmth.animateTo(if (active) 1f else 0f, tween(400)) }
    LaunchedEffect(pulseKey) {
        if (pulseKey == 0 || reduceMotion) return@LaunchedEffect
        coroutineScope {
            launch {
                ring.snapTo(0f)
                ring.animateTo(1f, tween(700, easing = Motion.EaseOutQuint))
            }
            launch {
                bounce.snapTo(1f)
                bounce.animateTo(1f, spring(dampingRatio = 0.38f, stiffness = 420f), initialVelocity = 2.4f)
            }
            flap.animateTo(1f, spring(dampingRatio = 0.7f, stiffness = 520f))
            delay(340)
            flap.animateTo(0f, spring(dampingRatio = 0.52f, stiffness = 260f))
        }
    }

    val height = width * 0.82f
    Box(
        modifier
            .size(width, height)
            .graphicsLayer {
                scaleX = bounce.value
                scaleY = 2f - bounce.value // squash and stretch: wider when lower
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0.5f, 1f)
            },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(width, height)) {
            val openness = max(held.value, flap.value).coerceIn(-0.2f, 1.25f)
            val warm = warmth.value
            val back = lerp(lerp(c.sunken, c.accent, if (c.dark) 0.22f else 0.17f), c.accent, 0.12f * warm)
            val front = lerp(lerp(if (c.dark) c.raised else c.surface, c.accent, if (c.dark) 0.16f else 0.11f), c.accent, 0.22f * warm)
            val paper = if (c.dark) lerp(c.raised, Color.White, 0.75f) else Color.White

            // Light ring on arrival.
            if (ring.value < 1f) {
                val r = size.width * (0.45f + 0.35f * ring.value)
                drawCircle(
                    Brush.radialGradient(listOf(c.accentGlow.copy(alpha = c.accentGlow.alpha * (1f - ring.value) * 2f), Color.Transparent), center, r),
                    r,
                )
            }
            drawFolderBack(back)
            drawPapers(paper, openness)
            drawFront(front, c.highlight, openness)
        }
        if (icon != null) {
            Icon(
                icon,
                contentDescription = null,
                tint = lerp(c.inkSecondary, c.accent, warmth.value * 0.8f).copy(alpha = 0.85f),
                modifier = Modifier
                    .size(width * 0.3f)
                    .graphicsLayer {
                        val openness = max(held.value, flap.value)
                        translationY = height.toPx() * (0.16f + 0.1f * openness)
                        scaleY = 1f - 0.18f * openness
                    },
            )
        }
    }
}

private fun DrawScope.drawFolderBack(color: Color) {
    val w = size.width
    val h = size.height
    val r = w * 0.09f
    val path = Path().apply {
        moveTo(0f, h * 0.2f + r)
        quadraticTo(0f, h * 0.2f, r, h * 0.2f)
        lineTo(w * 0.04f, h * 0.2f)
        // Tab.
        lineTo(w * 0.06f, h * 0.06f)
        quadraticTo(w * 0.07f, 0f, w * 0.13f, 0f)
        lineTo(w * 0.36f, 0f)
        quadraticTo(w * 0.41f, 0f, w * 0.44f, h * 0.06f)
        lineTo(w * 0.48f, h * 0.13f)
        lineTo(w - r, h * 0.13f)
        quadraticTo(w, h * 0.13f, w, h * 0.13f + r)
        lineTo(w, h - r)
        quadraticTo(w, h, w - r, h)
        lineTo(r, h)
        quadraticTo(0f, h, 0f, h - r)
        close()
    }
    drawPath(path, color)
}

private fun DrawScope.drawPapers(color: Color, openness: Float) {
    val w = size.width
    val h = size.height
    val lift = h * 0.12f * openness
    val sheet = Size(w * 0.76f, h * 0.62f)
    rotate(-4f * openness, Offset(w * 0.5f, h)) {
        drawRoundRect(color.copy(alpha = 0.82f), Offset(w * 0.1f, h * 0.24f - lift * 0.6f), sheet, CornerRadius(w * 0.05f))
    }
    rotate(3f * openness, Offset(w * 0.5f, h)) {
        drawRoundRect(color, Offset(w * 0.14f, h * 0.29f - lift), sheet, CornerRadius(w * 0.05f))
    }
}

/** The front flap, drawn as a trapezoid that tips toward the viewer as it opens. */
private fun DrawScope.drawFront(color: Color, highlight: Color, openness: Float) {
    val w = size.width
    val h = size.height
    val r = w * 0.09f
    val top = h * (0.34f + 0.2f * openness)
    val spread = w * 0.05f * openness
    val path = Path().apply {
        moveTo(-spread + r * 0.6f, top)
        lineTo(w + spread - r * 0.6f, top)
        quadraticTo(w + spread, top, w + spread * 0.7f, top + r)
        lineTo(w, h - r)
        quadraticTo(w, h, w - r, h)
        lineTo(r, h)
        quadraticTo(0f, h, 0f, h - r)
        lineTo(-spread * 0.7f, top + r)
        quadraticTo(-spread, top, -spread + r * 0.6f, top)
        close()
    }
    // Opening tilts the flap away from the light, so it darkens slightly.
    drawPath(path, lerp(color, Color.Black, 0.06f * openness.coerceAtLeast(0f)))
    drawLine(highlight, Offset(r, top + 1f), Offset(w - r, top + 1f), strokeWidth = 1.2f)
}
