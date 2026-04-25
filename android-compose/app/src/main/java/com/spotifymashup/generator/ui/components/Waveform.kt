package com.spotifymashup.generator.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.spotifymashup.generator.ui.theme.BrandAccent
import com.spotifymashup.generator.ui.theme.BrandPrimary
import com.spotifymashup.generator.ui.theme.BrandSecondary
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.random.Random

private fun proceduralWave(n: Int, seed: Long): FloatArray {
    val rng = Random(seed)
    return FloatArray(n) { i ->
        val base = sin(i * 2.0 * PI / n).toFloat()
        val lump = sin(i * 6.0 * PI / n).toFloat() * 0.4f
        min(1f, max(0.05f, abs(base + lump) * 0.8f + rng.nextFloat() * 0.18f))
    }
}

@Composable
fun Waveform(
    modifier: Modifier = Modifier,
    samples: FloatArray? = null,
    seed: Long = 42L,
    height: Dp = 120.dp,
    playheadFraction: Float = 0f,
    hookStart: Float = -1f,
    hookEnd: Float = -1f,
    onSeek: ((Float) -> Unit)? = null,
) {
    val data = remember(samples, seed) { samples ?: proceduralWave(220, seed) }
    val brush = remember {
        Brush.linearGradient(listOf(BrandPrimary, BrandSecondary, BrandAccent))
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                if (onSeek != null) {
                    detectTapGestures { off ->
                        onSeek((off.x / size.width).coerceIn(0f, 1f))
                    }
                }
            }
            .pointerInput(Unit) {
                if (onSeek != null) {
                    detectHorizontalDragGestures { change, _ ->
                        onSeek((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                }
            },
    ) {
        val w = size.width
        val h = size.height
        if (hookEnd > hookStart && hookStart >= 0f) {
            drawRoundRect(
                color = BrandPrimary.copy(alpha = 0.18f),
                topLeft = Offset(hookStart * w, 4f),
                size = Size((hookEnd - hookStart) * w, h - 8f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(12f, 12f),
            )
        }
        drawLine(Color.White.copy(alpha = 0.15f), Offset(0f, h / 2f), Offset(w, h / 2f), 1f)
        val n = data.size
        val space = w / n
        val barW = max(space * 0.55f, 2f)
        val maxHalf = h / 2f - 6f
        for (i in 0 until n) {
            val a = data[i]
            val cx = i * space + space / 2f
            val half = a * maxHalf
            drawRoundRect(
                brush = brush,
                topLeft = Offset(cx - barW / 2f, h / 2f - half),
                size = Size(barW, half * 2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW, barW),
            )
        }
        val px = w * playheadFraction.coerceIn(0f, 1f)
        drawLine(Color.White, Offset(px, 4f), Offset(px, h - 4f), 4f)
        drawCircle(Color.White, 8f, Offset(px, h / 2f), style = Stroke(width = 4f))
    }
}

@Composable
fun MiniWaveform(
    modifier: Modifier = Modifier,
    seed: Long,
    height: Dp = 40.dp,
) {
    val data = remember(seed) { proceduralWave(80, seed) }
    val brush = remember { Brush.linearGradient(listOf(BrandPrimary, BrandSecondary)) }
    Canvas(modifier = modifier.fillMaxWidth().height(height)) {
        val w = size.width
        val h = size.height
        val n = data.size
        val space = w / n
        val barW = max(space * 0.55f, 2f)
        val maxHalf = h / 2f - 2f
        for (i in 0 until n) {
            val a = data[i]
            val cx = i * space + space / 2f
            val half = a * maxHalf
            drawRoundRect(
                brush = brush,
                topLeft = Offset(cx - barW / 2f, h / 2f - half),
                size = Size(barW, half * 2f),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(barW, barW),
            )
        }
    }
}
