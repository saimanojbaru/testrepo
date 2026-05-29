package com.hitit.app.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import com.hitit.app.ui.theme.heatColor
import com.hitit.domain.grid.GridAggregator.GridCell

/**
 * The signature heatmap ("The Grid"): ISO-week columns of 7 day-cells, drawn on a single Canvas
 * for smooth scrolling. Future/out-of-year cells are dimmed.
 */
@Composable
fun Heatmap(
    columns: List<List<GridCell>>,
    modifier: Modifier = Modifier,
    cell: Dp = 13.dp,
    gap: Dp = 3.dp,
    onCellClick: ((GridCell) -> Unit)? = null,
) {
    if (columns.isEmpty()) return
    val density = LocalDensity.current
    val cellPx = with(density) { cell.toPx() }
    val gapPx = with(density) { gap.toPx() }
    val step = cellPx + gapPx
    val radius = with(density) { 3.dp.toPx() }

    val cols = columns.size
    val widthDp = cell * cols + gap * (cols - 1)
    val heightDp = cell * 7 + gap * 6
    val emptyColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)

    Canvas(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .size(width = widthDp, height = heightDp)
            .let { base ->
                if (onCellClick == null) base else base.pointerInput(columns) {
                    detectTapGestures { offset ->
                        val ci = (offset.x / step).toInt()
                        val ri = (offset.y / step).toInt()
                        columns.getOrNull(ci)?.getOrNull(ri)?.let(onCellClick)
                    }
                }
            },
    ) {
        columns.forEachIndexed { ci, column ->
            column.forEachIndexed { ri, gridCell ->
                val x = ci * step
                val y = ri * step
                val color: Color = if (!gridCell.inYear || gridCell.isFuture) {
                    emptyColor
                } else {
                    heatColor(gridCell.intensity)
                }
                drawRoundRect(
                    color = color,
                    topLeft = Offset(x, y),
                    size = Size(cellPx, cellPx),
                    cornerRadius = CornerRadius(radius, radius),
                )
            }
        }
    }
}

/** "Less → More" intensity legend. */
@Composable
fun HeatmapLegend(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("Less", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        (0..4).forEach { intensity ->
            androidx.compose.foundation.layout.Box(
                modifier = Modifier
                    .padding(horizontal = 1.dp)
                    .size(12.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(heatColor(intensity)),
            )
        }
        Text("More", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
