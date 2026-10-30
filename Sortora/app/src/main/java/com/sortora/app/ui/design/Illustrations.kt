package com.sortora.app.ui.design

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.compose.animation.core.withInfiniteAnimationFrameNanos
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.sin

/**
 * Seconds since first composition, advanced every frame. Read it only in draw / layer lambdas so
 * the ambient motion redraws without recomposing. Stops under "remove animations" and in tests
 * (infinite animations are paused there), leaving a still frame.
 */
@Composable
fun rememberLoopClock(): State<Float> {
    val time = remember { mutableFloatStateOf(0f) }
    val reduceMotion = Sortora.reduceMotion
    LaunchedEffect(reduceMotion) {
        if (reduceMotion) return@LaunchedEffect
        val start = withInfiniteAnimationFrameNanos { it }
        while (true) {
            withInfiniteAnimationFrameNanos { now -> time.floatValue = (now - start) / 1_000_000_000f }
        }
    }
    return time
}

enum class Art { Photos, History, Search, Faces, Paused, Shield, Sort, Review }

/** Small, slow, looping scenes for empty states and onboarding. */
@Composable
fun Illustration(art: Art, modifier: Modifier = Modifier, size: Dp = 168.dp) {
    val c = Sortora.colors
    val clock = rememberLoopClock()
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            drawCircle(Brush.radialGradient(listOf(c.accentSoft.copy(alpha = if (c.dark) 0.9f else 1f), Color.Transparent), center, this.size.minDimension * 0.5f))
        }
        when (art) {
            Art.Photos -> PhotosArt(size, clock)
            Art.History -> HistoryArt(size, clock)
            Art.Search -> CanvasArt(clock) { t -> drawSearch(t, c) }
            Art.Faces -> CanvasArt(clock) { t -> drawFaces(t, c) }
            Art.Paused -> CanvasArt(clock) { t -> drawPaused(t, c) }
            Art.Shield -> CanvasArt(clock) { t -> drawShield(t, c) }
            Art.Sort -> SortArt(size, clock)
            Art.Review -> CanvasArt(clock) { t -> drawReview(t, c) }
        }
    }
}

@Composable
private fun BoxScope.CanvasArt(clock: State<Float>, draw: DrawScope.(Float) -> Unit) {
    Canvas(Modifier.matchParentSize()) { draw(clock.value) }
}

// --- Photos: three photo cards, fanned, drifting on slightly different rhythms ---------------

@Composable
private fun BoxScope.PhotosArt(size: Dp, clock: State<Float>) {
    val c = Sortora.colors
    val cardW = size * 0.42f
    val cardH = size * 0.52f
    listOf(-1, 1, 0).forEach { i ->
        Box(
            Modifier
                .zIndex(if (i == 0) 1f else 0f)
                .offset(x = size * 0.17f * i)
                .graphicsLayer {
                    val t = clock.value
                    rotationZ = 11f * i + sin(t * 1.1f + i) * 1.8f
                    translationY = (sin(t * 0.9f + i * 1.7f) * 4f).dp.toPx() + if (i == 0) 0f else 6.dp.toPx()
                }
                .size(cardW, cardH)
                .softShadow(Radius.Sm, Depth.Mid, c)
                .clip(Radius.Sm)
                .background(c.surface)
                .hairline(Radius.Sm, c.hairline),
        ) {
            Canvas(Modifier.matchParentSize()) { drawPhoto(c, seed = i) }
        }
    }
}

private fun DrawScope.drawPhoto(c: SortoraColors, seed: Int) {
    val pad = size.width * 0.08f
    val inner = Size(size.width - pad * 2, size.height * 0.72f - pad)
    val top = Offset(pad, pad)
    drawRoundRect(
        Brush.verticalGradient(listOf(lerp(c.accentSoft, c.surface, 0.2f), lerp(c.sunken, c.accentSoft, 0.4f)), top.y, top.y + inner.height),
        top, inner, CornerRadius(size.width * 0.06f),
    )
    drawCircle(c.accent.copy(alpha = 0.55f), inner.width * 0.11f, Offset(top.x + inner.width * (0.72f - seed * 0.08f), top.y + inner.height * 0.3f))
    val hills = Path().apply {
        moveTo(top.x, top.y + inner.height * 0.82f)
        quadraticTo(top.x + inner.width * 0.3f, top.y + inner.height * (0.5f + seed * 0.05f), top.x + inner.width * 0.55f, top.y + inner.height * 0.75f)
        quadraticTo(top.x + inner.width * 0.78f, top.y + inner.height * 0.58f, top.x + inner.width, top.y + inner.height * 0.7f)
        lineTo(top.x + inner.width, top.y + inner.height)
        lineTo(top.x, top.y + inner.height)
        close()
    }
    drawPath(hills, lerp(c.sunken, c.accent, 0.28f))
    val lineY = top.y + inner.height + pad * 1.2f
    drawRoundRect(c.sunken, Offset(pad, lineY), Size(size.width * 0.5f, size.height * 0.045f), CornerRadius(50f))
}

// --- History: log rows drifting, and a clock whose hand sweeps --------------------------------

@Composable
private fun BoxScope.HistoryArt(size: Dp, clock: State<Float>) {
    val c = Sortora.colors
    val rowW = size * 0.66f
    val rowH = size * 0.15f
    listOf(-1, 0, 1).forEach { i ->
        Box(
            Modifier
                .offset(y = size * 0.19f * i + size * 0.04f)
                .graphicsLayer {
                    val t = clock.value
                    translationX = (sin(t * 0.8f + i * 1.2f) * 5f).dp.toPx()
                    alpha = 1f - 0.22f * (i + 1)
                }
                .size(rowW, rowH)
                .softShadow(Radius.Pill, Depth.Low, c)
                .clip(Radius.Pill)
                .background(c.surface)
                .hairline(Radius.Pill, c.hairline),
        ) {
            Canvas(Modifier.matchParentSize()) {
                val h = this.size.height
                drawCircle(if (i == -1) c.accent else c.sunken, h * 0.22f, Offset(h * 0.55f, h / 2))
                drawRoundRect(c.sunken, Offset(h * 1.05f, h * 0.3f), Size(this.size.width * 0.48f, h * 0.16f), CornerRadius(50f))
                drawRoundRect(c.sunken.copy(alpha = 0.7f), Offset(h * 1.05f, h * 0.56f), Size(this.size.width * 0.3f, h * 0.14f), CornerRadius(50f))
            }
        }
    }
    Canvas(Modifier.matchParentSize()) {
        val t = clock.value
        val center = Offset(this.size.width * 0.8f, this.size.height * 0.2f)
        val r = this.size.width * 0.1f
        drawCircle(c.surface, r, center)
        drawCircle(c.hairline, r, center, style = Stroke(1.dp.toPx()))
        rotate(t * 40f, center) { drawLine(c.accent, center, center + Offset(0f, -r * 0.62f), 2.dp.toPx(), StrokeCap.Round) }
        rotate(t * 4f, center) { drawLine(c.inkSecondary, center, center + Offset(r * 0.42f, 0f), 2.dp.toPx(), StrokeCap.Round) }
        drawCircle(c.ink, 1.6.dp.toPx(), center)
    }
}

// --- Search: a lens gliding over a field of dots ----------------------------------------------

private fun DrawScope.drawSearch(t: Float, c: SortoraColors) {
    val w = size.width
    val step = w * 0.11f
    val lens = Offset(w * 0.5f + sin(t * 0.7f) * w * 0.16f, size.height * 0.48f + sin(t * 1.4f) * w * 0.08f)
    val r = w * 0.17f
    var y = w * 0.24f
    while (y < size.height * 0.8f) {
        var x = w * 0.2f
        while (x < w * 0.82f) {
            val d = (Offset(x, y) - lens).getDistance()
            val inside = d < r * 0.95f
            drawCircle(if (inside) c.accent.copy(alpha = 0.7f) else c.inkTertiary.copy(alpha = 0.25f), if (inside) 3.2.dp.toPx() else 2.dp.toPx(), Offset(x, y))
            x += step
        }
        y += step
    }
    drawCircle(c.surface.copy(alpha = 0.35f), r, lens)
    drawCircle(c.ink, r, lens, style = Stroke(3.dp.toPx()))
    val handleStart = lens + Offset(r * 0.72f, r * 0.72f)
    drawLine(c.ink, handleStart, handleStart + Offset(r * 0.55f, r * 0.55f), 5.dp.toPx(), StrokeCap.Round)
}

// --- Faces: three soft avatars breathing out of phase ----------------------------------------

private fun DrawScope.drawFaces(t: Float, c: SortoraColors) {
    val w = size.width
    listOf(-1, 1, 0).forEach { i ->
        val center = Offset(w * 0.5f + i * w * 0.2f, size.height * (0.52f + if (i == 0) -0.02f else 0.04f))
        val r = w * (if (i == 0) 0.17f else 0.135f) * (1f + 0.035f * sin(t * 1.3f + i * 2f))
        drawCircle(c.shadow.copy(alpha = if (c.dark) 0.3f else 0.06f), r * 1.04f, center + Offset(0f, r * 0.12f))
        drawCircle(if (i == 0) c.surface else lerp(c.surface, c.sunken, 0.6f), r, center)
        drawCircle(c.hairline, r, center, style = Stroke(1.dp.toPx()))
        val tone = if (i == 0) c.accent.copy(alpha = 0.75f) else c.inkTertiary.copy(alpha = 0.5f)
        drawCircle(tone, r * 0.3f, center + Offset(0f, -r * 0.18f))
        drawArc(tone, 200f, 140f, true, center + Offset(-r * 0.52f, r * 0.2f), Size(r * 1.04f, r * 1.0f))
    }
}

// --- Paused ------------------------------------------------------------------------------------

private fun DrawScope.drawPaused(t: Float, c: SortoraColors) {
    val r = size.width * 0.24f
    val pulse = (sin(t * 1.6f) + 1f) / 2f
    drawCircle(c.accent.copy(alpha = 0.12f * (1f - pulse)), r * (1.15f + 0.25f * pulse), center)
    drawCircle(c.surface, r, center)
    drawCircle(c.hairline, r, center, style = Stroke(1.dp.toPx()))
    val barW = r * 0.18f
    val barH = r * 0.72f
    listOf(-1, 1).forEach { s ->
        drawRoundRect(c.ink, Offset(center.x + s * r * 0.2f - barW / 2, center.y - barH / 2), Size(barW, barH), CornerRadius(barW / 2))
    }
}

// --- Shield: data points orbit inside a ring and never leave it --------------------------------

private fun DrawScope.drawShield(t: Float, c: SortoraColors) {
    val w = size.width
    val ring = w * 0.36f
    drawCircle(c.inkTertiary.copy(alpha = 0.35f), ring, center, style = Stroke(1.2.dp.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 9f), t * 12f)))
    repeat(6) { i ->
        val a = t * (0.5f + i * 0.07f) + i * 1.05f
        val rr = ring * (0.72f + 0.16f * sin(t + i))
        drawCircle(if (i % 3 == 0) c.accent else c.inkSecondary.copy(alpha = 0.5f), (if (i % 3 == 0) 3.5f else 2.5f).dp.toPx(), center + Offset(cos(a) * rr, sin(a) * rr))
    }
    val sw = w * 0.32f
    val sh = w * 0.38f
    val top = center.y - sh * 0.52f
    val shield = Path().apply {
        moveTo(center.x, top)
        cubicTo(center.x + sw * 0.32f, top + sh * 0.1f, center.x + sw * 0.5f, top + sh * 0.1f, center.x + sw * 0.5f, top + sh * 0.12f)
        cubicTo(center.x + sw * 0.52f, top + sh * 0.6f, center.x + sw * 0.3f, top + sh * 0.85f, center.x, top + sh)
        cubicTo(center.x - sw * 0.3f, top + sh * 0.85f, center.x - sw * 0.52f, top + sh * 0.6f, center.x - sw * 0.5f, top + sh * 0.12f)
        cubicTo(center.x - sw * 0.5f, top + sh * 0.1f, center.x - sw * 0.32f, top + sh * 0.1f, center.x, top)
        close()
    }
    drawPath(shield, c.surface)
    drawPath(shield, c.hairline, style = Stroke(1.dp.toPx()))
    drawPath(shield, Brush.verticalGradient(listOf(c.accent.copy(alpha = 0.16f), Color.Transparent), top, top + sh))
    val check = Path().apply {
        moveTo(center.x - sw * 0.18f, center.y)
        lineTo(center.x - sw * 0.04f, center.y + sh * 0.13f)
        lineTo(center.x + sw * 0.2f, center.y - sh * 0.12f)
    }
    drawPath(check, c.accent, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))
}

// --- Sort: little cards arc from the top into two folders, which open as they land ------------

@Composable
private fun BoxScope.SortArt(size: Dp, clock: State<Float>) {
    val c = Sortora.colors
    val period = 1.6f
    val leftArrivals by remember { derivedStateOf { floor((clock.value + period * 0.2f) / (period * 2)).toInt() } }
    val rightArrivals by remember { derivedStateOf { floor((clock.value + period * 1.2f) / (period * 2)).toInt() } }
    val folderW = size * 0.36f
    FolderGlyph(Modifier.align(Alignment.BottomStart).offset(x = size * 0.06f, y = -size * 0.08f), width = folderW, pulseKey = leftArrivals)
    FolderGlyph(Modifier.align(Alignment.BottomEnd).offset(x = -size * 0.06f, y = -size * 0.08f), width = folderW, pulseKey = rightArrivals, active = true)
    val density = LocalDensity.current
    val sizePx = with(density) { size.toPx() }
    repeat(2) { lane ->
        Box(
            Modifier
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    val t = clock.value + lane * period
                    val p = ((t % (period * 2)) / (period * 2)).coerceIn(0f, 1f)
                    // Ease along a quadratic arc from the top centre to one folder.
                    val e = 1f - (1f - p) * (1f - p) * (1f - p)
                    val side = if (lane == 0) -1f else 1f
                    val start = Offset(0f, sizePx * 0.08f)
                    val control = Offset(side * sizePx * 0.05f, -sizePx * 0.05f)
                    val end = Offset(side * sizePx * 0.27f, sizePx * 0.6f)
                    val q = 1f - e
                    translationX = q * q * start.x + 2 * q * e * control.x + e * e * end.x
                    translationY = q * q * start.y + 2 * q * e * control.y + e * e * end.y
                    rotationZ = side * 16f * e
                    val s = 1f - 0.55f * e
                    scaleX = s
                    scaleY = s
                    alpha = if (p > 0.93f) (1f - p) / 0.07f else (p / 0.08f).coerceAtMost(1f)
                }
                .size(size * 0.26f, size * 0.32f)
                .softShadow(Radius.Xs, Depth.Mid, c)
                .clip(Radius.Xs)
                .background(c.surface)
                .hairline(Radius.Xs, c.hairline),
        ) { Canvas(Modifier.matchParentSize()) { drawPhoto(c, lane) } }
    }
}

// --- Review: a list whose rows tick themselves off in turn ------------------------------------

private fun DrawScope.drawReview(t: Float, c: SortoraColors) {
    val w = size.width
    val cardTopLeft = Offset(w * 0.18f, size.height * 0.2f)
    val card = Size(w * 0.64f, size.height * 0.6f)
    drawRoundRect(c.shadow.copy(alpha = if (c.dark) 0.3f else 0.06f), cardTopLeft + Offset(0f, 6.dp.toPx()), card, CornerRadius(w * 0.08f))
    drawRoundRect(c.surface, cardTopLeft, card, CornerRadius(w * 0.08f))
    drawRoundRect(c.hairline, cardTopLeft, card, CornerRadius(w * 0.08f), style = Stroke(1.dp.toPx()))
    val cycle = (t % 4.2f) / 4.2f
    repeat(3) { i ->
        val y = cardTopLeft.y + card.height * (0.24f + 0.26f * i)
        val x = cardTopLeft.x + card.width * 0.14f
        val on = ((cycle - 0.15f - i * 0.2f) / 0.12f).coerceIn(0f, 1f)
        val r = card.width * 0.07f
        drawCircle(lerp(c.sunken, c.accent, on), r, Offset(x, y))
        if (on > 0f) {
            val check = Path().apply {
                moveTo(x - r * 0.45f, y)
                lineTo(x - r * 0.1f, y + r * 0.35f)
                lineTo(x + r * 0.48f, y - r * 0.32f)
            }
            drawPath(check, c.onAccent.copy(alpha = on), style = Stroke(r * 0.28f, cap = StrokeCap.Round))
        }
        drawRoundRect(c.sunken, Offset(x + r * 1.8f, y - r * 0.55f), Size(card.width * (0.5f - i * 0.06f), r * 0.5f), CornerRadius(50f))
        drawRoundRect(c.sunken.copy(alpha = 0.6f), Offset(x + r * 1.8f, y + r * 0.2f), Size(card.width * 0.3f, r * 0.4f), CornerRadius(50f))
    }
}
