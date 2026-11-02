package com.nonverbal.mastery.design.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.NvColors
import com.nonverbal.mastery.design.tokens.Space
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

enum class DiagramKind(val id: String, val title: String, val description: String) {
    BaselineTimeline(
        "baseline-timeline", "The baseline, over time",
        "A timeline of one person's behaviour. A shaded band marks their resting range. In the first minutes the line runs high above the band while the situation is new, then settles into it. Later, shortly after a marked moment, the line leaves the band for a short spike and returns. The spike, its timing, and its duration are what an observer records; the wandering inside the band is noise.",
    ),
    GazeCone(
        "gaze-cone", "Where the eyes can go",
        "A top-down view of two people facing each other across a short distance. From one person's eyes a cone opens toward the other: a narrow central zone of direct gaze at the face, a wider zone around it that takes in the torso and hands, and the outer peripheral field where movement is noticed but detail is not.",
    ),
    ProxemicsRings(
        "proxemics-rings", "Distance zones",
        "A figure seen from above at the centre of four rings. Intimate distance reaches to about 45 centimetres; personal distance to about 1.2 metres; social distance to about 3.6 metres; public distance beyond. These are Edward Hall's figures for mid-century middle-class Americans and vary with culture, relationship, setting and crowding.",
    ),
    CongruenceMatrix(
        "congruence-matrix", "Face, body, voice",
        "A grid of eight columns showing every combination of three channels — face, body and voice — each either agreeing with what is said or not. One column has all three agreeing; three columns have a single channel out of step; three have two out of step; one has none agreeing. The single-channel columns are the most common and the least informative; agreement or disagreement across several channels at once carries more weight.",
    ),
    GroupAttention(
        "group-attention", "Attention around a table",
        "A top-down view of six people around a meeting table. One person is speaking, but most heads and arrows of attention turn not toward the speaker but toward a quiet person at the far end — the one whose reaction the room is waiting for.",
    ),
    BreathingWave(
        "breathing-wave", "The four phases of a breath",
        "A wave tracing one slow breath in four phases: inhale rising, a pause at the top, exhale falling, and a pause at the bottom. A dot travels along the wave at the pace of the breath. A slow, even cycle like this is a regulation practice, not a reading of anyone else.",
    );

    companion object {
        fun of(id: String?): DiagramKind? = entries.firstOrNull { it.id == id }
    }
}

@Composable
fun Diagram(kind: DiagramKind, modifier: Modifier = Modifier, showTitle: Boolean = true) {
    Column(modifier.fillMaxWidth()) {
        if (showTitle) {
            SectionLabel("Plate · ${kind.title}", color = Nv.colors.inkTertiary)
            Spacer(Modifier.height(Space.x3))
        }
        val m = Modifier.fillMaxWidth().semantics {
            contentDescription = kind.description
            role = Role.Image
        }
        when (kind) {
            DiagramKind.BaselineTimeline -> BaselineTimelineDiagram(m.height(190.dp))
            DiagramKind.GazeCone -> GazeConeDiagram(m.height(210.dp))
            DiagramKind.ProxemicsRings -> ProxemicsDiagram(m.height(300.dp))
            DiagramKind.CongruenceMatrix -> CongruenceDiagram(m.height(190.dp))
            DiagramKind.GroupAttention -> GroupAttentionDiagram(m.height(240.dp))
            DiagramKind.BreathingWave -> BreathingWaveDiagram(m.height(180.dp))
        }
    }
}

private class Ink(val c: NvColors, val tint: Color, val measurer: TextMeasurer, val label: TextStyle, val italic: TextStyle)

@Composable
private fun rememberInk(): Ink {
    val measurer = rememberTextMeasurer()
    val c = Nv.colors
    val tint = Nv.tint
    val label = Nv.type.uiSmall.copy(fontSize = Nv.type.label.fontSize)
    val italic = Nv.type.caption
    return remember(c, tint, label, italic) { Ink(c, tint, measurer, label, italic) }
}

private fun DrawScope.label(ink: Ink, text: String, at: Offset, color: Color = ink.c.inkSecondary, center: Boolean = false, italic: Boolean = false) {
    val layout = ink.measurer.measure(text, (if (italic) ink.italic else ink.label).copy(color = color))
    val x = if (center) at.x - layout.size.width / 2f else at.x
    drawText(layout, topLeft = Offset(x, at.y - layout.size.height / 2f))
}

@Composable
fun BaselineTimelineDiagram(modifier: Modifier) {
    val ink = rememberInk()
    Canvas(modifier) {
        val c = ink.c
        val left = 8.dp.toPx(); val right = size.width - 8.dp.toPx()
        val top = 20.dp.toPx(); val bottom = size.height - 26.dp.toPx()
        val bandTop = top + (bottom - top) * 0.45f
        val bandBottom = top + (bottom - top) * 0.72f
        drawRect(ink.tint.copy(alpha = 0.08f), Offset(left, bandTop), Size(right - left, bandBottom - bandTop))
        val dash = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))
        drawLine(ink.tint.copy(alpha = 0.5f), Offset(left, bandTop), Offset(right, bandTop), 1.dp.toPx(), pathEffect = dash)
        drawLine(ink.tint.copy(alpha = 0.5f), Offset(left, bandBottom), Offset(right, bandBottom), 1.dp.toPx(), pathEffect = dash)
        drawLine(c.divider, Offset(left, bottom), Offset(right, bottom), 1.dp.toPx())

        // The behaviour line: high while the room is new, settling, a spike after the marked moment.
        val stim = 0.66f
        val path = Path()
        val n = 160
        for (i in 0..n) {
            val t = i / n.toFloat()
            val settle = (1f - t / 0.28f).coerceAtLeast(0f)
            val wobble = sin(t * 38f) * 0.05f + sin(t * 91f) * 0.03f
            val spikeT = t - (stim + 0.025f)
            val spike = if (spikeT > 0f) (spikeT * 40f) * kotlin.math.exp(-spikeT * 26f) * 0.62f else 0f
            val v = 0.585f + wobble - settle * 0.42f - spike
            val x = left + (right - left) * t
            val y = top + (bottom - top) * v
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, c.ink.copy(alpha = 0.8f), style = Stroke(1.6.dp.toPx(), cap = StrokeCap.Round))
        val sx = left + (right - left) * stim
        drawLine(c.brass, Offset(sx, top), Offset(sx, bottom), 1.dp.toPx())
        drawCircle(c.brass, 3.5.dp.toPx(), Offset(sx, top))
        label(ink, "the question", Offset(sx + 6.dp.toPx(), top), c.brassText)
        label(ink, "resting range", Offset(right - 92.dp.toPx(), bandBottom + 10.dp.toPx()), ink.tint)
        label(ink, "first minutes", Offset(left + 4.dp.toPx(), top + 4.dp.toPx()), c.inkTertiary)
        label(ink, "time →", Offset(right - 40.dp.toPx(), bottom + 12.dp.toPx()), c.inkTertiary)
        val spikeX = left + (right - left) * (stim + 0.06f)
        label(ink, "change · timing · duration", Offset(spikeX + 8.dp.toPx(), top + (bottom - top) * 0.22f), c.ink, italic = true)
    }
}

private fun DrawScope.headTopDown(at: Offset, facing: Float, r: Float, fill: Color, line: Color) {
    // Shoulders as an ellipse across the facing direction, head as a circle with a nose tick.
    val perp = facing + (PI / 2).toFloat()
    val sw = r * 2.1f
    val p1 = Offset(at.x + cos(perp) * sw, at.y + sin(perp) * sw)
    val p2 = Offset(at.x - cos(perp) * sw, at.y - sin(perp) * sw)
    drawLine(fill, p1, p2, r * 1.3f, StrokeCap.Round)
    drawCircle(fill, r, at)
    drawCircle(line, r, at, style = Stroke(1.dp.toPx()))
    drawLine(line, at + Offset(cos(facing) * r, sin(facing) * r), at + Offset(cos(facing) * r * 1.5f, sin(facing) * r * 1.5f), 1.5.dp.toPx(), StrokeCap.Round)
}

@Composable
fun GazeConeDiagram(modifier: Modifier) {
    val ink = rememberInk()
    Canvas(modifier) {
        val c = ink.c
        val a = Offset(size.width * 0.18f, size.height * 0.55f)
        val b = Offset(size.width * 0.8f, size.height * 0.55f)
        val r = 11.dp.toPx()
        val reach = (b.x - a.x) * 1.08f
        fun wedge(halfDeg: Float, color: Color) {
            val p = Path()
            p.moveTo(a.x, a.y)
            val steps = 24
            for (i in 0..steps) {
                val ang = Math.toRadians((-halfDeg + 2 * halfDeg * i / steps).toDouble()).toFloat()
                p.lineTo(a.x + cos(ang) * reach, a.y + sin(ang) * reach)
            }
            p.close()
            drawPath(p, color)
        }
        wedge(55f, ink.tint.copy(alpha = 0.06f))
        wedge(24f, ink.tint.copy(alpha = 0.09f))
        wedge(6f, ink.tint.copy(alpha = 0.18f))
        headTopDown(a, 0f, r, c.figureFill, c.figureLine)
        headTopDown(b, PI.toFloat(), r, c.figureFill, c.figureLine)
        label(ink, "direct gaze", Offset((a.x + b.x) / 2, a.y - 10.dp.toPx()), ink.tint, center = true)
        label(ink, "face, torso, hands", Offset((a.x + b.x) / 2 + 20.dp.toPx(), a.y - 46.dp.toPx()), c.inkSecondary, center = true)
        label(ink, "periphery: movement, not detail", Offset(a.x + 18.dp.toPx(), 14.dp.toPx()), c.inkTertiary)
    }
}

@Composable
fun ProxemicsDiagram(modifier: Modifier) {
    val ink = rememberInk()
    Canvas(modifier) {
        val c = ink.c
        val center = Offset(size.width / 2f, size.height / 2f)
        val maxR = size.minDimension / 2f - 6.dp.toPx()
        val radii = listOf(0.2f, 0.42f, 0.7f, 1f).map { it * maxR }
        val names = listOf("intimate · to 45 cm", "personal · to 1.2 m", "social · to 3.6 m", "public · beyond")
        radii.reversed().forEachIndexed { i, r ->
            drawCircle(ink.tint.copy(alpha = 0.04f + 0.04f * i), r, center)
            drawCircle(ink.tint.copy(alpha = 0.35f), r, center, style = Stroke(1.dp.toPx()))
        }
        headTopDown(center, (-PI / 2).toFloat(), 9.dp.toPx(), c.figureFill, c.figureLine)
        radii.forEachIndexed { i, r ->
            val prev = if (i == 0) 0f else radii[i - 1]
            val y = center.y - (prev + r) / 2f - (if (i == 0) 6.dp.toPx() else 0f)
            label(ink, names[i], Offset(center.x, y), if (i == 0) c.ink else c.inkSecondary, center = true)
        }
    }
}

@Composable
fun CongruenceDiagram(modifier: Modifier) {
    val ink = rememberInk()
    Canvas(modifier) {
        val c = ink.c
        val rows = listOf("face", "body", "voice")
        val combos = listOf(
            listOf(true, true, true),
            listOf(false, true, true), listOf(true, false, true), listOf(true, true, false),
            listOf(false, false, true), listOf(false, true, false), listOf(true, false, false),
            listOf(false, false, false),
        )
        val labelW = 46.dp.toPx()
        val gridLeft = labelW
        val colW = (size.width - gridLeft) / 8f
        val rowH = 30.dp.toPx()
        val top = 14.dp.toPx()
        rows.forEachIndexed { r, name -> label(ink, name, Offset(0f, top + rowH * r + rowH / 2), c.inkSecondary) }
        val groups = listOf(0..0, 1..3, 4..6, 7..7)
        val groupColors = listOf(c.doGreen, ink.tint, c.brass, c.dontRed)
        groups.forEachIndexed { g, range ->
            val x0 = gridLeft + colW * range.first + 2.dp.toPx()
            val x1 = gridLeft + colW * (range.last + 1) - 2.dp.toPx()
            drawRoundRect(groupColors[g].copy(alpha = 0.07f), Offset(x0, top - 6.dp.toPx()), Size(x1 - x0, rowH * 3 + 12.dp.toPx()),
                androidx.compose.ui.geometry.CornerRadius(8.dp.toPx()))
        }
        combos.forEachIndexed { col, combo ->
            combo.forEachIndexed { r, agrees ->
                val center = Offset(gridLeft + colW * col + colW / 2, top + rowH * r + rowH / 2)
                if (agrees) drawCircle(c.ink.copy(alpha = 0.75f), 5.dp.toPx(), center)
                else {
                    drawCircle(c.dontRed, 5.dp.toPx(), center, style = Stroke(1.5.dp.toPx()))
                }
            }
        }
        val by = top + rowH * 3 + 20.dp.toPx()
        val captions = listOf("all agree", "one channel out", "two out", "none")
        groups.forEachIndexed { g, range ->
            val cx = gridLeft + colW * (range.first + range.last + 1) / 2f
            label(ink, captions[g], Offset(cx, by), if (g == 1) ink.tint else c.inkTertiary, center = true)
        }
        label(ink, "● agrees with the words   ○ contradicts", Offset(gridLeft, by + 22.dp.toPx()), c.inkTertiary)
    }
}

@Composable
fun GroupAttentionDiagram(modifier: Modifier) {
    val ink = rememberInk()
    Canvas(modifier) {
        val c = ink.c
        val w = size.width; val h = size.height
        val tableTopLeft = Offset(w * 0.22f, h * 0.36f)
        val tableSize = Size(w * 0.56f, h * 0.28f)
        drawRoundRect(c.surfaceSunken, tableTopLeft, tableSize, androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()))
        drawRoundRect(c.divider, tableTopLeft, tableSize, androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()), style = Stroke(1.dp.toPx()))
        val r = 9.dp.toPx()
        val decider = Offset(w * 0.88f, h * 0.5f)
        val speaker = Offset(w * 0.36f, h * 0.2f)
        val others = listOf(Offset(w * 0.64f, h * 0.2f), Offset(w * 0.36f, h * 0.8f), Offset(w * 0.64f, h * 0.8f), Offset(w * 0.12f, h * 0.5f))
        fun facing(from: Offset, to: Offset) = kotlin.math.atan2(to.y - from.y, to.x - from.x)
        // Attention arrows toward the decider
        for (o in others + speaker) {
            val ang = facing(o, decider)
            val start = o + Offset(cos(ang) * r * 2.2f, sin(ang) * r * 2.2f)
            val end = decider - Offset(cos(ang) * r * 2.4f, sin(ang) * r * 2.4f)
            drawLine(c.brass.copy(alpha = 0.55f), start, end, 1.2.dp.toPx(), StrokeCap.Round,
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(5f, 5f)))
            val ah = 6.dp.toPx()
            drawLine(c.brass, end, end - Offset(cos(ang - 0.45f) * ah, sin(ang - 0.45f) * ah), 1.4.dp.toPx(), StrokeCap.Round)
            drawLine(c.brass, end, end - Offset(cos(ang + 0.45f) * ah, sin(ang + 0.45f) * ah), 1.4.dp.toPx(), StrokeCap.Round)
        }
        for (o in others) headTopDown(o, facing(o, decider), r, c.figureFill, c.figureLine)
        headTopDown(speaker, facing(speaker, Offset(w * 0.5f, h * 0.5f)), r, c.figureFill, ink.tint)
        headTopDown(decider, PI.toFloat(), r, c.figureFill, c.brass)
        label(ink, "speaking", Offset(speaker.x, speaker.y - r * 2.6f), ink.tint, center = true)
        label(ink, "watched", Offset(decider.x, decider.y + r * 3.2f), c.brassText, center = true)
    }
}

@Composable
fun BreathingWaveDiagram(modifier: Modifier) {
    val ink = rememberInk()
    val reduced = Nv.reducedMotion
    val phase = if (reduced) -1f else {
        val t = rememberInfiniteTransition(label = "breath")
        val v by t.animateFloat(0f, 1f, infiniteRepeatable(tween(16000, easing = LinearEasing), RepeatMode.Restart), label = "p")
        v
    }
    Canvas(modifier) {
        val c = ink.c
        val left = 8.dp.toPx(); val right = size.width - 8.dp.toPx()
        val top = 28.dp.toPx(); val bottom = size.height - 30.dp.toPx()
        fun yAt(t: Float): Float {
            // Four equal phases: rise, hold high, fall, hold low.
            val q = (t * 4f)
            val v = when {
                q < 1f -> (1 - cos(q * PI.toFloat())) / 2f
                q < 2f -> 1f
                q < 3f -> (1 + cos((q - 2f) * PI.toFloat())) / 2f
                else -> 0f
            }
            return bottom - (bottom - top) * v
        }
        val path = Path()
        val n = 200
        for (i in 0..n) {
            val t = i / n.toFloat()
            val x = left + (right - left) * t
            if (i == 0) path.moveTo(x, yAt(t)) else path.lineTo(x, yAt(t))
        }
        for (k in 1..3) {
            val x = left + (right - left) * k / 4f
            drawLine(c.divider, Offset(x, top - 6.dp.toPx()), Offset(x, bottom + 6.dp.toPx()), 1.dp.toPx())
        }
        drawPath(path, ink.tint, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round))
        val names = listOf("in", "hold", "out", "hold")
        names.forEachIndexed { i, s ->
            label(ink, s, Offset(left + (right - left) * (i + 0.5f) / 4f, bottom + 18.dp.toPx()), c.inkTertiary, center = true)
        }
        if (phase >= 0f) {
            val x = left + (right - left) * phase
            drawCircle(c.brass, 6.dp.toPx(), Offset(x, yAt(phase)))
        } else {
            label(ink, "about four seconds each", Offset((left + right) / 2f, top - 16.dp.toPx()), c.inkTertiary, center = true, italic = true)
        }
    }
}
