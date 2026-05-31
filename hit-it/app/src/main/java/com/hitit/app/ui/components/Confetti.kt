package com.hitit.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import com.hitit.app.ui.theme.NeonAmber
import com.hitit.app.ui.theme.NeonCyan
import com.hitit.app.ui.theme.NeonLime
import com.hitit.app.ui.theme.NeonMagenta
import com.hitit.app.ui.theme.NeonViolet
import kotlin.random.Random

/**
 * A lightweight one-shot confetti burst. Re-triggers whenever [trigger] changes to a new value.
 * Pure Compose Canvas — no extra dependency.
 */
@Composable
fun ConfettiOverlay(trigger: Long, modifier: Modifier = Modifier) {
    if (trigger <= 0L) return
    val progress by animateFloatAsState(
        targetValue = if (trigger > 0) 1f else 0f,
        animationSpec = tween(durationMillis = 1100, easing = LinearEasing),
        label = "confetti",
    )
    val colors = listOf(NeonCyan, NeonLime, NeonMagenta, NeonViolet, NeonAmber)
    val pieces = rememberPieces(trigger)

    Canvas(modifier = modifier.fillMaxSize()) {
        pieces.forEach { p ->
            val y = (progress * (size.height * p.fall) + p.startY * size.height)
            val x = size.width * p.x + p.drift * progress * size.width
            val alpha = (1f - progress).coerceIn(0f, 1f)
            drawRect(
                color = colors[p.colorIndex].copy(alpha = alpha),
                topLeft = Offset(x, y),
                size = Size(p.size, p.size * 2.2f),
            )
        }
    }
}

private data class Piece(
    val x: Float,
    val startY: Float,
    val fall: Float,
    val drift: Float,
    val size: Float,
    val colorIndex: Int,
)

@Composable
private fun rememberPieces(seed: Long): List<Piece> {
    return androidx.compose.runtime.remember(seed) {
        val rng = Random(seed)
        List(80) {
            Piece(
                x = rng.nextFloat(),
                startY = -rng.nextFloat() * 0.2f,
                fall = 0.6f + rng.nextFloat() * 0.6f,
                drift = (rng.nextFloat() - 0.5f) * 0.4f,
                size = 6f + rng.nextFloat() * 8f,
                colorIndex = rng.nextInt(5),
            )
        }
    }
}
