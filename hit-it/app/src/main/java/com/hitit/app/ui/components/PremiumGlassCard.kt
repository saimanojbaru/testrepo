package com.hitit.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.random.Random

/** Premium glass surface — a hair lifted off the canvas so cards read as carbon, not voids. */
private val GlassSurface = Color(0xFF14141C)

/**
 * Subtle micro-grain so surfaces don't look sterile. The points are remembered once (fixed seed),
 * so per-frame drawing is cheap and stable (no recomposition jitter). Drawn at very low alpha.
 */
fun Modifier.subtleNoise(alpha: Float = 0.022f): Modifier = composed {
    val points = remember {
        val rng = Random(42)
        List(220) { Triple(rng.nextFloat(), rng.nextFloat(), rng.nextBoolean()) }
    }
    drawWithContent {
        drawContent()
        points.forEach { (fx, fy, white) ->
            drawCircle(
                color = (if (white) Color.White else Color.Black).copy(alpha = alpha),
                radius = 1.1f,
                center = Offset(fx * size.width, fy * size.height),
            )
        }
    }
}

/** Asymmetric "rim light" border: bright at the top edge, fading toward the bottom. */
fun glassRimBrush(tint: Color = Color.White): Brush = Brush.verticalGradient(
    colors = listOf(tint.copy(alpha = 0.16f), tint.copy(alpha = 0.02f)),
)

/**
 * The canonical premium surface: translucent carbon backing + micro-grain + an asymmetric metallic
 * rim that looks like it's catching light from above. When [accent] is set (e.g. a completed rep),
 * the whole card lights up in that accent — a cohesive ambient look instead of disjoint status dots.
 */
@Composable
fun PremiumGlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    contentPadding: Dp = 16.dp,
    accent: Color? = null,
    rimTint: Color = Color.White,
    content: @Composable BoxScope.() -> Unit,
) {
    val shape = RoundedCornerShape(cornerRadius)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(GlassSurface)
            .then(if (accent != null) Modifier.background(accent.copy(alpha = 0.10f)) else Modifier)
            .subtleNoise()
            .border(1.dp, if (accent != null) glassRimBrush(accent) else glassRimBrush(rimTint), shape)
            .padding(contentPadding),
        content = content,
    )
}
