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
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.hitit.app.ui.theme.AuroraBase
import com.hitit.app.ui.theme.AuroraBlobCyan
import com.hitit.app.ui.theme.AuroraBlobPeach
import com.hitit.app.ui.theme.AuroraBlobPink
import com.hitit.app.ui.theme.AuroraBlobViolet

/**
 * The living canvas: a near-white base with several big, soft aurora blobs that slowly drift. Each
 * blob is a radial gradient (colour → transparent), so they're soft-edged with no real blur (which
 * would need API 31). The drift phases are read inside [drawBehind], so motion runs on the draw
 * phase only — cheap, no recomposition.
 */
@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val transition = rememberInfiniteTransition(label = "aurora")
    val p1 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(22000, easing = LinearEasing), RepeatMode.Reverse),
        label = "p1",
    )
    val p2 by transition.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(31000, easing = LinearEasing), RepeatMode.Reverse),
        label = "p2",
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                drawRect(AuroraBase)
                val w = size.width
                val h = size.height
                fun blob(color: Color, cx: Float, cy: Float, radius: Float, alpha: Float) {
                    drawRect(
                        brush = Brush.radialGradient(
                            colors = listOf(color.copy(alpha = alpha), Color.Transparent),
                            center = Offset(cx, cy),
                            radius = radius,
                        ),
                    )
                }
                // Four drifting blobs — top-left cyan, top-right violet, lower pink, bottom peach.
                blob(AuroraBlobCyan, w * (0.18f + 0.10f * p1), h * (0.12f + 0.06f * p2), w * 0.85f, 0.45f)
                blob(AuroraBlobViolet, w * (0.88f - 0.12f * p2), h * (0.22f + 0.10f * p1), w * 0.95f, 0.42f)
                blob(AuroraBlobPink, w * (0.72f + 0.10f * p1), h * (0.82f - 0.06f * p2), w * 0.80f, 0.40f)
                blob(AuroraBlobPeach, w * (0.14f + 0.06f * p2), h * (0.88f - 0.05f * p1), w * 0.70f, 0.38f)
            },
    ) {
        content()
    }
}
