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
import androidx.compose.runtime.remember
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
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import com.hitit.app.ui.theme.heatColor
import com.hitit.domain.grid.GridAggregator
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
    HeatmapCanvas(
        columns = columns,
        modifier = modifier.horizontalScroll(rememberScrollState()),
        cell = cell,
        gap = gap,
        onCellClick = onCellClick,
    )
}

/**
 * The Grid with an aligned month-label axis above it. Labels and cells share one scroll state, so
 * they stay aligned while scrolling horizontally.
 */
@Composable
fun HeatmapWithMonths(
    columns: List<List<GridCell>>,
    modifier: Modifier = Modifier,
    cell: Dp = 13.dp,
    gap: Dp = 3.dp,
    onCellClick: ((GridCell) -> Unit)? = null,
) {
    if (columns.isEmpty()) return
    val scrollState = rememberScrollState()
    val density = LocalDensity.current
    val stepPx = with(density) { (cell + gap).toPx() }
    val months = remember(columns) { GridAggregator.monthLabels(columns) }
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall

    Column(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .horizontalScroll(scrollState)
                .height(16.dp)
                .size(width = cell * columns.size + gap * (columns.size - 1), height = 16.dp),
        ) {
            drawMonthLabels(months, stepPx, textMeasurer, labelStyle, labelColor)
        }
        HeatmapCanvas(
            columns = columns,
            modifier = Modifier.horizontalScroll(scrollState),
            cell = cell,
            gap = gap,
            onCellClick = onCellClick,
        )
    }
}

private fun DrawScope.drawMonthLabels(
    months: List<GridAggregator.MonthLabel>,
    stepPx: Float,
    textMeasurer: androidx.compose.ui.text.TextMeasurer,
    style: TextStyle,
    color: Color,
) {
    months.forEach { label ->
        val result = textMeasurer.measure(label.label, style)
        drawText(
            textLayoutResult = result,
            color = color,
            topLeft = Offset(label.columnIndex * stepPx, 0f),
        )
    }
}

@Composable
private fun HeatmapCanvas(
    columns: List<List<GridCell>>,
    modifier: Modifier,
    cell: Dp,
    gap: Dp,
    onCellClick: ((GridCell) -> Unit)?,
) {
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
