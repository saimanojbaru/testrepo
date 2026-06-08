package com.hitit.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Overlays a moving light band over the content — a "filling/updating" shimmer for progress fills.
 * The infinite animation is hoisted into the composition (via [composed]); the DrawScope only reads
 * the animated value, which is the correct pattern (you can't start an animation inside drawWith*).
 */
fun Modifier.shimmer(
    active: Boolean = true,
    color: Color = Color.White.copy(alpha = 0.35f),
    durationMillis: Int = 1500,
): Modifier = if (!active) this else composed {
    val transition = rememberInfiniteTransition(label = "shimmer")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(durationMillis), RepeatMode.Restart),
        label = "shimmerPhase",
    )
    drawWithContent {
        drawContent()
        val band = size.width * 0.4f
        val start = -band + phase * (size.width + band)
        drawRect(
            brush = Brush.horizontalGradient(
                colors = listOf(Color.Transparent, color, Color.Transparent),
                startX = start,
                endX = start + band,
            ),
        )
    }
}
