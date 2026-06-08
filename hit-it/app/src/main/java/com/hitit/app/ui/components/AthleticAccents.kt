package com.hitit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hitit.app.ui.theme.HeroGradient
import com.hitit.app.ui.theme.NeonCyan
import com.hitit.app.ui.theme.SurfaceVariant
import com.hitit.app.ui.theme.heatColor

/** Big number / header text painted with the energy gradient so it jumps off the dark canvas. */
@Composable
fun GradientText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    brush: Brush = HeroGradient,
) {
    Text(text = text, modifier = modifier, style = style.copy(brush = brush))
}

/**
 * A segmented progress gauge — `completed` of `total` cells light up in the accent color, the rest
 * stay charcoal. Reads like physical units locking into place (vs a smooth Material bar).
 */
@Composable
fun SegmentedGauge(
    completed: Int,
    total: Int,
    modifier: Modifier = Modifier,
    accent: androidx.compose.ui.graphics.Color = NeonCyan,
    height: Dp = 8.dp,
) {
    val safeTotal = total.coerceAtLeast(1)
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        val allDone = completed >= safeTotal
        for (i in 0 until safeTotal) {
            val on = i < completed
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .clip(RoundedCornerShape(4.dp))
                    .background(if (on) accent else SurfaceVariant)
                    // Lit cells shimmer; a perfect gauge shimmers a touch brighter.
                    .shimmer(active = on, color = Color.White.copy(alpha = if (allDone) 0.45f else 0.3f)),
            )
        }
    }
}

/**
 * A compact 7-day (or N-day) consistency sparkline drawn on a single Canvas: one rounded square per
 * day, colored by activity intensity (reuses [heatColor]).
 */
@Composable
fun MomentumSparkline(
    intensities: List<Int>,
    modifier: Modifier = Modifier,
    block: Dp = 22.dp,
    gap: Dp = 6.dp,
) {
    if (intensities.isEmpty()) return
    val count = intensities.size
    val widthDp = block * count + gap * (count - 1)
    Canvas(modifier = modifier.size(width = widthDp, height = block)) {
        val blockPx = block.toPx()
        val gapPx = gap.toPx()
        val radius = 6.dp.toPx()
        intensities.forEachIndexed { i, intensity ->
            drawRoundRect(
                color = heatColor(intensity),
                topLeft = Offset(i * (blockPx + gapPx), 0f),
                size = Size(blockPx, blockPx),
                cornerRadius = CornerRadius(radius, radius),
            )
        }
    }
}
