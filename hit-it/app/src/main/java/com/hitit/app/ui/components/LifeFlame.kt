package com.hitit.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hitit.app.ui.theme.NeonAmber
import com.hitit.app.ui.theme.NeonLime
import com.hitit.app.ui.theme.NeonMagenta
import com.hitit.app.ui.theme.NeonViolet
import com.hitit.domain.flame.LifeFlame as FlameModel

/**
 * The animated Life Flame — a hand-rolled Canvas flame (no extra dependency, same approach as
 * [ConfettiOverlay]). [level] 1..5 maps to color/heat/animation intensity:
 * 1 Dying (dim violet, barely moving) → 5 Inferno (lime/white core, fast flicker + big glow).
 */
@Composable
fun LifeFlame(
    level: Int,
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
) {
    val lvl = level.coerceIn(FlameModel.MIN_LEVEL, FlameModel.MAX_LEVEL)

    val transition = rememberInfiniteTransition(label = "flame")
    // Faster flicker at higher levels.
    val flickerDuration = (1400 - lvl * 180).coerceAtLeast(420)
    val flicker by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(flickerDuration), RepeatMode.Reverse),
        label = "flicker",
    )
    val sway by transition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(flickerDuration + 300), RepeatMode.Reverse),
        label = "sway",
    )

    val (core, body, glow) = flameColors(lvl)
    // Higher levels burn taller/brighter; dying flames are short and faint.
    val heat = lvl / 5f
    val bodyAlpha = 0.45f + 0.55f * heat
    val glowAlpha = (0.10f + 0.06f * lvl) * (0.7f + 0.3f * flicker)

    Canvas(modifier = modifier.size(size)) {
        val w = this.size.width
        val h = this.size.height
        val cx = w / 2f
        // Flicker modulates flame height; sway nudges the tip.
        val flameHeight = h * (0.55f + 0.40f * heat) * (0.92f + 0.08f * flicker)
        val baseY = h * 0.92f
        val tipX = cx + sway * w * 0.06f * heat
        val tipY = baseY - flameHeight
        val halfWidth = w * (0.16f + 0.16f * heat)

        // Outer glow.
        drawCircle(
            color = glow.copy(alpha = glowAlpha),
            radius = w * (0.34f + 0.10f * heat),
            center = Offset(cx, baseY - flameHeight * 0.45f),
        )

        // Flame body: a teardrop from the base, curving to the swaying tip.
        val bodyPath = Path().apply {
            moveTo(cx - halfWidth, baseY)
            cubicTo(
                cx - halfWidth * 1.1f, baseY - flameHeight * 0.45f,
                tipX - halfWidth * 0.5f, tipY + flameHeight * 0.30f,
                tipX, tipY,
            )
            cubicTo(
                tipX + halfWidth * 0.5f, tipY + flameHeight * 0.30f,
                cx + halfWidth * 1.1f, baseY - flameHeight * 0.45f,
                cx + halfWidth, baseY,
            )
            cubicTo(cx + halfWidth * 0.6f, baseY + h * 0.04f, cx - halfWidth * 0.6f, baseY + h * 0.04f, cx - halfWidth, baseY)
            close()
        }
        drawPath(bodyPath, color = body.copy(alpha = bodyAlpha))

        // Inner core (brighter, shorter) — only meaningful from Steady up.
        if (lvl >= 3) {
            val coreHeight = flameHeight * 0.55f
            val coreHalf = halfWidth * 0.5f
            val coreTipY = baseY - coreHeight
            val corePath = Path().apply {
                moveTo(cx - coreHalf, baseY)
                cubicTo(
                    cx - coreHalf, baseY - coreHeight * 0.5f,
                    tipX - coreHalf * 0.4f, coreTipY + coreHeight * 0.3f,
                    cx + sway * w * 0.03f, coreTipY,
                )
                cubicTo(
                    tipX + coreHalf * 0.4f, coreTipY + coreHeight * 0.3f,
                    cx + coreHalf, baseY - coreHeight * 0.5f,
                    cx + coreHalf, baseY,
                )
                close()
            }
            drawPath(corePath, color = core.copy(alpha = 0.85f))
        }
    }
}

/** (core, body, glow) colors per level. */
private fun flameColors(level: Int): Triple<Color, Color, Color> = when (level) {
    5 -> Triple(Color.White, NeonLime, NeonAmber)
    4 -> Triple(NeonLime, NeonAmber, NeonAmber)
    3 -> Triple(NeonAmber, NeonAmber, NeonMagenta)
    2 -> Triple(NeonMagenta, NeonMagenta, NeonViolet)
    else -> Triple(NeonViolet, NeonViolet, NeonViolet)
}

@Preview
@Composable
private fun LifeFlamePreview() {
    LifeFlame(level = 5)
}
