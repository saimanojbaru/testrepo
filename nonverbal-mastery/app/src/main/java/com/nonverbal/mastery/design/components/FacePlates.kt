package com.nonverbal.mastery.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.NvColors

/**
 * A schematic face, described by FACS action units (0 = absent, 1 = strong).
 *
 * The body figures in this book are faceless on purpose. Faces appear only on these plates, where
 * the face itself is the subject, drawn as a neutral, ungendered sketch so that the plate teaches
 * muscle movements rather than a type of person.
 *
 * Left and right are the face's own: [au14Right] dimples the face's right cheek, which the
 * viewer sees on the left.
 */
data class FaceSpec(
    val au1: Float = 0f,   // inner brow raiser
    val au2: Float = 0f,   // outer brow raiser
    val au4: Float = 0f,   // brow lowerer
    val au5: Float = 0f,   // upper lid raiser
    val au6: Float = 0f,   // cheek raiser
    val au7: Float = 0f,   // lid tightener
    val au9: Float = 0f,   // nose wrinkler
    val au10: Float = 0f,  // upper lip raiser
    val au12: Float = 0f,  // lip corner puller
    val au14Right: Float = 0f, // dimpler, one side only
    val au15: Float = 0f,  // lip corner depressor
    val au17: Float = 0f,  // chin raiser
    val au20: Float = 0f,  // lip stretcher
    val au23: Float = 0f,  // lip tightener
    val au24: Float = 0f,  // lip pressor
    val au25: Float = 0f,  // lips part
    val au26: Float = 0f,  // jaw drop
    val gazeX: Float = 0f, // -1 (face's right, viewer's left) .. 1
    val gazeY: Float = 0f, // -1 up .. 1 down
)

/** One face on a plate, with a plain caption and the action units it shows. */
data class FaceCell(val spec: FaceSpec, val caption: String, val code: String)

@Composable
fun FacePlate(cells: List<FaceCell>, columns: Int, modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val c = Nv.colors
    val captionStyle = Nv.type.caption
    val codeStyle = Nv.type.uiSmall.copy(fontSize = Nv.type.label.fontSize)
    val rows = (cells.size + columns - 1) / columns
    Canvas(modifier) {
        val cellW = size.width / columns
        val cellH = size.height / rows
        val captionH = 40.dp.toPx()
        cells.forEachIndexed { i, cell ->
            val col = i % columns; val row = i / columns
            val left = col * cellW; val top = row * cellH
            val faceH = cellH - captionH
            val u = minOf(cellW * 0.36f, faceH * 0.37f)
            val center = Offset(left + cellW / 2f, top + faceH / 2f + 2.dp.toPx())
            drawFace(cell.spec, center, u, c)
            val cap = measurer.measure(cell.caption, captionStyle.copy(color = c.ink))
            drawText(cap, topLeft = Offset(left + (cellW - cap.size.width) / 2f, top + faceH + 2.dp.toPx()))
            val code = measurer.measure(cell.code, codeStyle.copy(color = c.inkTertiary))
            drawText(code, topLeft = Offset(left + (cellW - code.size.width) / 2f, top + faceH + 4.dp.toPx() + cap.size.height))
        }
    }
}

/** Neutral, then a flash of expression, then neutral again, along a time axis. */
@Composable
fun MicroExpressionPlate(modifier: Modifier) {
    val measurer = rememberTextMeasurer()
    val c = Nv.colors
    val tint = Nv.tint
    val label = Nv.type.uiSmall.copy(fontSize = Nv.type.label.fontSize)
    val italic = Nv.type.caption
    val flash = remember { FaceSpec(au4 = 1f, au5 = 0.35f, au7 = 1f, au23 = 1f, au24 = 0.6f) }
    Canvas(modifier) {
        val w = size.width
        val axisY = size.height - 52.dp.toPx()
        val u = minOf(w * 0.13f, (axisY - 18.dp.toPx()) * 0.36f)
        val xs = listOf(w * 0.17f, w * 0.5f, w * 0.83f)
        val faceY = axisY - u * 1.55f
        val half = w * 0.045f
        drawRoundRect(
            c.brass.copy(alpha = 0.10f), Offset(xs[1] - u * 1.35f, faceY - u * 1.45f),
            androidx.compose.ui.geometry.Size(u * 2.7f, axisY - (faceY - u * 1.45f)),
            androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
        )
        drawFace(FaceSpec(), Offset(xs[0], faceY), u, c)
        drawFace(flash, Offset(xs[1], faceY), u, c)
        drawFace(FaceSpec(), Offset(xs[2], faceY), u, c)
        // The axis and the brief window.
        drawLine(c.divider, Offset(8.dp.toPx(), axisY), Offset(w - 8.dp.toPx(), axisY), 1.dp.toPx())
        drawRect(c.brass.copy(alpha = 0.28f), Offset(xs[1] - half, axisY - 5.dp.toPx()), androidx.compose.ui.geometry.Size(half * 2, 10.dp.toPx()))
        drawLine(c.brass, Offset(xs[1] - half, axisY - 9.dp.toPx()), Offset(xs[1] - half, axisY + 9.dp.toPx()), 1.5.dp.toPx())
        drawLine(c.brass, Offset(xs[1] + half, axisY - 9.dp.toPx()), Offset(xs[1] + half, axisY + 9.dp.toPx()), 1.5.dp.toPx())
        fun text(s: String, at: Offset, style: TextStyle, color: Color) {
            val l = measurer.measure(s, style.copy(color = color))
            drawText(l, topLeft = Offset(at.x - l.size.width / 2f, at.y))
        }
        text("under half a second", Offset(xs[1], axisY + 12.dp.toPx()), label, c.brassText)
        text("resting", Offset(xs[0], axisY + 12.dp.toPx()), label, c.inkTertiary)
        text("resting", Offset(xs[2], axisY + 12.dp.toPx()), label, c.inkTertiary)
        text("a flash, between two resting faces", Offset(w / 2f, axisY + 30.dp.toPx()), italic, tint)
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

/** Draws one face centred at [o] whose half-width is [u]. */
fun DrawScope.drawFace(f: FaceSpec, o: Offset, u: Float, c: NvColors) {
    val line = c.ink.copy(alpha = 0.78f)
    val soft = c.ink.copy(alpha = 0.32f)
    val hair = 1.dp.toPx()
    val stroke = maxOf(1.2.dp.toPx(), u * 0.035f)

    // Head: an egg, wider above the cheekbones, with small ears.
    val head = Path().apply {
        moveTo(o.x, o.y - 1.25f * u)
        cubicTo(o.x + 0.62f * u, o.y - 1.25f * u, o.x + 1.0f * u, o.y - 0.85f * u, o.x + 1.0f * u, o.y - 0.2f * u)
        cubicTo(o.x + 1.0f * u, o.y + 0.45f * u, o.x + 0.72f * u, o.y + 1.02f * u, o.x, o.y + 1.25f * u)
        cubicTo(o.x - 0.72f * u, o.y + 1.02f * u, o.x - 1.0f * u, o.y + 0.45f * u, o.x - 1.0f * u, o.y - 0.2f * u)
        cubicTo(o.x - 1.0f * u, o.y - 0.85f * u, o.x - 0.62f * u, o.y - 1.25f * u, o.x, o.y - 1.25f * u)
        close()
    }
    for (s in listOf(-1f, 1f)) {
        val ear = Path().apply {
            moveTo(o.x + s * 0.97f * u, o.y - 0.24f * u)
            cubicTo(o.x + s * 1.16f * u, o.y - 0.3f * u, o.x + s * 1.16f * u, o.y + 0.16f * u, o.x + s * 0.95f * u, o.y + 0.12f * u)
        }
        drawPath(ear, c.figureFill)
        drawPath(ear, c.figureLine, style = Stroke(hair))
    }
    drawPath(head, c.figureFill)
    drawPath(head, c.figureLine, style = Stroke(hair))

    // Forehead lines (AU1/AU2) and the vertical creases between the brows (AU4).
    val raise = maxOf(f.au1, f.au2)
    if (raise > 0.35f) {
        val span = if (f.au2 > 0.35f) 0.42f else 0.2f
        for (k in 0 until 3) {
            val y = o.y - (0.66f + 0.1f * k) * u
            val p = Path().apply {
                moveTo(o.x - span * u, y + 0.015f * u)
                quadraticTo(o.x, y - 0.03f * u * (if (f.au1 > f.au2) -1f else 1f), o.x + span * u, y + 0.015f * u)
            }
            drawPath(p, soft.copy(alpha = 0.22f * raise + 0.1f), style = Stroke(hair, cap = StrokeCap.Round))
        }
    }
    if (f.au4 > 0.35f) {
        for (s in listOf(-1f, 1f)) {
            drawLine(soft.copy(alpha = 0.3f * f.au4 + 0.1f), Offset(o.x + s * 0.045f * u, o.y - 0.44f * u), Offset(o.x + s * 0.035f * u, o.y - 0.3f * u), hair, StrokeCap.Round)
        }
    }

    // Brows: the inner end follows AU1 (up) and AU4 (down and in); the outer end follows AU2.
    for (s in listOf(-1f, 1f)) {
        val baseY = o.y - 0.4f * u
        val innerX = o.x + s * (0.13f - 0.04f * f.au4) * u
        val innerY = baseY - 0.13f * u * f.au1 + 0.08f * u * f.au4
        val outerX = o.x + s * 0.64f * u
        val outerY = baseY + 0.04f * u - 0.13f * u * f.au2 + 0.02f * u * f.au4
        val midX = o.x + s * 0.4f * u
        val midY = baseY - 0.07f * u - 0.06f * u * f.au2 - 0.03f * u * f.au1 + 0.03f * u * f.au4
        val brow = Path().apply {
            moveTo(innerX, innerY)
            quadraticTo(midX, midY - 0.03f * u, outerX, outerY)
        }
        drawPath(brow, line, style = Stroke(stroke * 1.9f, cap = StrokeCap.Round))
    }

    // Eyes: lids open with AU5, narrow with AU7 and AU6; the iris follows the gaze.
    for (s in listOf(-1f, 1f)) {
        val ec = Offset(o.x + s * 0.38f * u, o.y - 0.16f * u)
        val ew = 0.19f * u
        val up = 0.085f * u * (1f + 0.95f * f.au5 - 0.45f * f.au7 - 0.2f * f.au6 - 0.15f * f.au4 - 0.45f * f.gazeY.coerceAtLeast(0f))
        val down = 0.07f * u * (1f + 0.3f * f.au5 - 0.7f * f.au7 - 0.75f * f.au6)
        val inner = Offset(ec.x - s * ew, ec.y + 0.01f * u)
        val outer = Offset(ec.x + s * ew, ec.y - 0.005f * u)
        val eye = Path().apply {
            moveTo(inner.x, inner.y)
            quadraticTo(ec.x, ec.y - up * 2f, outer.x, outer.y)
            quadraticTo(ec.x, ec.y + down * 2f, inner.x, inner.y)
            close()
        }
        drawPath(eye, c.background)
        clipPath(eye) {
            val iris = Offset(ec.x + f.gazeX * 0.1f * u, ec.y + f.gazeY * 0.06f * u + 0.005f * u)
            drawCircle(c.ink.copy(alpha = 0.62f), 0.072f * u, iris)
            drawCircle(c.ink.copy(alpha = 0.9f), 0.03f * u, iris)
        }
        // Upper lid line, heavier; lower lid lighter.
        val upper = Path().apply { moveTo(inner.x, inner.y); quadraticTo(ec.x, ec.y - up * 2f, outer.x, outer.y) }
        val lower = Path().apply { moveTo(inner.x, inner.y); quadraticTo(ec.x, ec.y + down * 2f, outer.x, outer.y) }
        drawPath(upper, line, style = Stroke(stroke * 1.2f, cap = StrokeCap.Round))
        drawPath(lower, soft.copy(alpha = 0.55f), style = Stroke(hair, cap = StrokeCap.Round))
        // AU6: cheeks lift into a bulge below the eye and creases fan from the outer corner.
        if (f.au6 > 0.25f) {
            val a = 0.3f + 0.45f * f.au6
            val bulge = Path().apply {
                moveTo(ec.x - s * 0.14f * u, ec.y + 0.14f * u)
                quadraticTo(ec.x + s * 0.05f * u, ec.y + 0.2f * u, ec.x + s * 0.22f * u, ec.y + 0.1f * u)
            }
            drawPath(bulge, soft.copy(alpha = a * 0.6f), style = Stroke(hair, cap = StrokeCap.Round))
            for (k in -1..1) {
                val start = Offset(outer.x + s * 0.03f * u, outer.y + k * 0.035f * u)
                val end = Offset(outer.x + s * 0.12f * u, outer.y + k * 0.065f * u)
                drawLine(soft.copy(alpha = a * 0.7f), start, end, hair, StrokeCap.Round)
            }
        }
    }

    // Nose: a bridge line and nostril wings; AU9 wrinkles the bridge and lifts the wings.
    val wingY = o.y + 0.3f * u - 0.04f * u * f.au9 - 0.02f * u * f.au10
    val nose = Path().apply {
        moveTo(o.x + 0.02f * u, o.y - 0.08f * u)
        quadraticTo(o.x + 0.07f * u, o.y + 0.18f * u, o.x + 0.03f * u, wingY + 0.03f * u)
    }
    drawPath(nose, soft.copy(alpha = 0.5f), style = Stroke(hair, cap = StrokeCap.Round))
    for (s in listOf(-1f, 1f)) {
        val wing = Path().apply {
            moveTo(o.x + s * 0.04f * u, wingY + 0.05f * u)
            quadraticTo(o.x + s * 0.15f * u, wingY + 0.06f * u, o.x + s * 0.13f * u, wingY - 0.02f * u)
        }
        drawPath(wing, line.copy(alpha = 0.6f), style = Stroke(hair * 1.2f, cap = StrokeCap.Round))
    }
    if (f.au9 > 0.3f) {
        // Wrinkles fan out from the sides of the bridge, not across it.
        for (side in listOf(-1f, 1f)) for (k in 0 until 2) {
            val y = o.y - 0.03f * u + k * 0.05f * u
            val p = Path().apply {
                moveTo(o.x + side * 0.05f * u, y)
                quadraticTo(o.x + side * 0.1f * u, y - 0.025f * u, o.x + side * 0.15f * u, y + 0.005f * u)
            }
            drawPath(p, soft.copy(alpha = 0.2f + 0.3f * f.au9), style = Stroke(hair, cap = StrokeCap.Round))
        }
    }

    // Mouth geometry.
    val my = o.y + 0.63f * u
    val open = 0.1f * u * f.au25 + 0.3f * u * f.au26
    val mw = 0.27f * u * (1f + 0.22f * f.au12 + 0.3f * f.au20 - 0.12f * f.au23 - 0.08f * f.au24 - 0.3f * f.au26)
    fun cornerY(s: Float): Float {
        val dimple = if (s < 0f) f.au14Right else 0f
        return my - 0.11f * u * f.au12 + 0.09f * u * f.au15 + 0.015f * u * f.au20 - 0.06f * u * dimple
    }
    fun cornerX(s: Float): Float {
        val dimple = if (s < 0f) f.au14Right else 0f
        return o.x + s * (mw + 0.035f * u * dimple)
    }
    val lCorner = Offset(cornerX(-1f), cornerY(-1f))
    val rCorner = Offset(cornerX(1f), cornerY(1f))

    // Nasolabial folds deepen with AU12, AU10 and AU9.
    val fold = maxOf(f.au12, f.au10, f.au9 * 0.8f)
    if (fold > 0.25f) {
        for (s in listOf(-1f, 1f)) {
            val corner = if (s < 0f) lCorner else rCorner
            val p = Path().apply {
                moveTo(o.x + s * 0.17f * u, wingY - 0.02f * u)
                quadraticTo(o.x + s * 0.3f * u, wingY + 0.05f * u, o.x + s * (0.33f + 0.04f * f.au12) * u, wingY + 0.17f * u)
            }
            drawPath(p, soft.copy(alpha = 0.08f + 0.22f * fold), style = Stroke(hair, cap = StrokeCap.Round))
        }
    }

    // Lips: the line where they meet bows with the corners; they part with AU25 and AU26.
    val lipSag = 0.04f * u * f.au12 - 0.02f * u * f.au15
    val upperMid = Offset(o.x, my - open / 2f - 0.02f * u * f.au10 + lipSag * 0.5f)
    val lowerMid = Offset(o.x, my + open / 2f + lipSag)
    val thin = 1f - 0.6f * maxOf(f.au23, f.au24)
    if (open > 0.02f * u) {
        val gap = Path().apply {
            moveTo(lCorner.x, lCorner.y)
            quadraticTo(upperMid.x, upperMid.y, rCorner.x, rCorner.y)
            quadraticTo(lowerMid.x, lowerMid.y + open * 0.4f, lCorner.x, lCorner.y)
            close()
        }
        drawPath(gap, c.ink.copy(alpha = 0.55f))
        drawPath(gap, line, style = Stroke(stroke, join = StrokeJoin.Round))
    } else {
        val meet = Path().apply {
            moveTo(lCorner.x, lCorner.y)
            quadraticTo(o.x, my + lipSag * 1.6f, rCorner.x, rCorner.y)
        }
        drawPath(meet, line, style = Stroke(stroke * 1.15f, cap = StrokeCap.Round))
    }
    // Upper lip outline with a shallow bow, and the lower lip's curve.
    val upperLip = Path().apply {
        moveTo(lCorner.x + 0.02f * u, lCorner.y - 0.01f * u)
        quadraticTo(o.x - 0.1f * u, upperMid.y - 0.075f * u * thin, o.x, upperMid.y - 0.055f * u * thin)
        quadraticTo(o.x + 0.1f * u, upperMid.y - 0.075f * u * thin, rCorner.x - 0.02f * u, rCorner.y - 0.01f * u)
    }
    drawPath(upperLip, soft.copy(alpha = 0.35f * (1f - 0.75f * f.au12)), style = Stroke(hair, cap = StrokeCap.Round))
    val lowerLip = Path().apply {
        moveTo(lCorner.x + 0.05f * u, lCorner.y + 0.02f * u)
        quadraticTo(o.x, lowerMid.y + (0.1f * thin + 0.02f) * u + open * 0.4f, rCorner.x - 0.05f * u, rCorner.y + 0.02f * u)
    }
    drawPath(lowerLip, soft.copy(alpha = 0.4f), style = Stroke(hair, cap = StrokeCap.Round))
    // AU14: a small dimple tucked in at one corner.
    if (f.au14Right > 0.25f) {
        val d = Path().apply {
            moveTo(lCorner.x - 0.02f * u, lCorner.y - 0.05f * u)
            quadraticTo(lCorner.x - 0.06f * u, lCorner.y, lCorner.x - 0.02f * u, lCorner.y + 0.05f * u)
        }
        drawPath(d, line.copy(alpha = 0.6f), style = Stroke(hair * 1.2f, cap = StrokeCap.Round))
    }
    // AU17: the chin boss pushes up and wrinkles.
    if (f.au17 > 0.3f) {
        val y = o.y + 0.95f * u
        val p = Path().apply { moveTo(o.x - 0.14f * u, y); quadraticTo(o.x, y - 0.07f * u * f.au17, o.x + 0.14f * u, y) }
        drawPath(p, soft.copy(alpha = 0.4f), style = Stroke(hair, cap = StrokeCap.Round, pathEffect = PathEffect.dashPathEffect(floatArrayOf(3f, 3f))))
    }
}

/** The faces on each named plate. */
object FaceCells {
    val smiles = listOf(
        FaceCell(FaceSpec(au12 = 0.85f), "Lips only", "AU 12"),
        FaceCell(FaceSpec(au6 = 0.9f, au12 = 0.85f), "Lips and eyes", "AU 6+12"),
    )
    val brows = listOf(
        FaceCell(FaceSpec(au1 = 0.9f, au2 = 0.9f), "Raised", "AU 1+2"),
        FaceCell(FaceSpec(au4 = 0.95f), "Lowered, drawn in", "AU 4"),
        FaceCell(FaceSpec(au1 = 0.95f, au4 = 0.55f), "Inner corners up", "AU 1+4"),
    )
    val lids = listOf(
        FaceCell(FaceSpec(au5 = 0.95f), "Upper lids raised", "AU 5"),
        FaceCell(FaceSpec(), "At rest", "neutral"),
        FaceCell(FaceSpec(au7 = 0.95f), "Lids tightened", "AU 7"),
    )
    val mouths = listOf(
        FaceCell(FaceSpec(au24 = 0.95f, au23 = 0.4f), "Lips pressed", "AU 24"),
        FaceCell(FaceSpec(au15 = 0.95f, au17 = 0.6f), "Corners down", "AU 15+17"),
        FaceCell(FaceSpec(au14Right = 0.95f), "One corner tucked", "AU 14, one side"),
    )
    val prototypes = listOf(
        FaceCell(FaceSpec(au6 = 0.8f, au12 = 0.9f, au25 = 0.35f), "Happiness", "AU 6+12"),
        FaceCell(FaceSpec(au1 = 0.9f, au2 = 0.9f, au5 = 0.8f, au25 = 0.5f, au26 = 0.7f), "Surprise", "AU 1+2+5+26"),
        FaceCell(FaceSpec(au1 = 0.85f, au2 = 0.5f, au4 = 0.6f, au5 = 0.95f, au20 = 0.8f, au25 = 0.6f), "Fear", "AU 1+2+4+5+20"),
        FaceCell(FaceSpec(au4 = 0.95f, au5 = 0.5f, au7 = 0.8f, au23 = 0.9f), "Anger", "AU 4+5+7+23"),
        FaceCell(FaceSpec(au9 = 0.95f, au10 = 0.7f, au15 = 0.3f, au17 = 0.3f), "Disgust", "AU 9+10"),
        FaceCell(FaceSpec(au1 = 0.9f, au4 = 0.6f, au15 = 0.85f, au17 = 0.4f), "Sadness", "AU 1+4+15"),
    )
    val gaze = listOf(
        FaceCell(FaceSpec(), "Direct", "eyes on you"),
        FaceCell(FaceSpec(gazeX = 0.95f), "Averted", "to one side"),
        FaceCell(FaceSpec(gazeX = -0.35f, gazeY = 1f), "Lowered", "down and away"),
    )
}
