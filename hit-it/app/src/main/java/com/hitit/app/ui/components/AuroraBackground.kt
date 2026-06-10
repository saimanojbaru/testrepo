package com.hitit.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.hitit.app.ui.theme.CosmicBottom
import com.hitit.app.ui.theme.CosmicTop
import com.hitit.app.ui.theme.NebulaMagenta
import com.hitit.app.ui.theme.NebulaTeal
import com.hitit.app.ui.theme.NebulaViolet
import kotlin.math.sin
import kotlin.random.Random

/**
 * The cosmic canvas (Cyber-Dreamcore): a deep navy→midnight-purple gradient, a few slow-drifting
 * desaturated nebula clouds, and a field of faint particles that flow and breathe. Soft and dark on
 * the eyes — the glow lives on the glass + accents, not here. All motion is read inside [drawBehind],
 * so it runs on the draw phase only (no recomposition). The name is kept for call-site stability.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "cosmos")
    val drift by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(34000, easing = LinearEasing), RepeatMode.Reverse),
        label = "drift",
    )
    val flow by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart),
        label = "flow",
    )

    // A fixed particle field (remembered once) — positions, sizes, speeds, twinkle phase.
    val particles = remember {
        val rng = Random(7)
        List(54) {
            Particle(
                x = rng.nextFloat(),
                y = rng.nextFloat(),
                radius = 0.6f + rng.nextFloat() * 1.8f,
                speed = 0.3f + rng.nextFloat() * 0.9f,
                twinkle = rng.nextFloat(),
                warm = rng.nextFloat() > 0.62f,
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height
                // Base cosmic gradient.
                drawRect(Brush.verticalGradient(listOf(CosmicTop, CosmicBottom)))
                // Drifting nebula clouds (desaturated, low alpha).
                fun nebula(color: Color, cx: Float, cy: Float, radius: Float) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(color.copy(alpha = 0.55f), Color.Transparent),
                            center = Offset(cx, cy),
                            radius = radius,
                        ),
                    )
                }
                nebula(NebulaViolet, w * (0.22f + 0.10f * drift), h * (0.18f + 0.05f * drift), w * 0.95f)
                nebula(NebulaTeal, w * (0.85f - 0.10f * drift), h * (0.30f + 0.06f * (1 - drift)), w * 0.85f)
                nebula(NebulaMagenta, w * (0.70f + 0.08f * drift), h * (0.82f - 0.05f * drift), w * 0.80f)

                // Particle flow — each rises slowly and twinkles; wraps around the top.
                particles.forEach { p ->
                    val y = ((p.y - flow * p.speed * 0.6f) % 1f + 1f) % 1f
                    val twinkle = 0.25f + 0.55f * (0.5f + 0.5f * sin((flow * 6.283f * p.speed) + p.twinkle * 6.283f))
                    val color = if (p.warm) Color(0xFFB98BFF) else Color(0xFF6FE9FF)
                    drawCircle(
                        color = color.copy(alpha = twinkle * 0.5f),
                        radius = p.radius,
                        center = Offset(p.x * w, y * h),
                    )
                }
            },
    ) {
        content()
    }
}

private data class Particle(
    val x: Float,
    val y: Float,
    val radius: Float,
    val speed: Float,
    val twinkle: Float,
    val warm: Boolean,
)
