package com.nonverbal.mastery.design.components

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nonverbal.mastery.data.models.Pose
import com.nonverbal.mastery.design.pose.Cast
import com.nonverbal.mastery.design.pose.FigureFrame
import com.nonverbal.mastery.design.pose.FigureGeometry
import com.nonverbal.mastery.design.pose.PoseRenderer
import com.nonverbal.mastery.design.pose.PoseSolver
import com.nonverbal.mastery.design.pose.Scene
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.Inter
import com.nonverbal.mastery.design.tokens.NvColors
import com.nonverbal.mastery.design.tokens.annotationColor

/** Every pose in the pack, by id. Provided once the content pack has loaded. */
val LocalPoseLibrary = staticCompositionLocalOf<Map<String, Pose>> { emptyMap() }

enum class FigureStyle { Filled, Outline }
enum class MarkerMode { None, Dots, Numbered }

@Immutable
data class FigurePalette(
    val fill: Color,
    val far: Color,
    val line: Color,
    val face: Color,
    val prop: Color,
    val panel: Color,
    val ground: Color,
    val markerRing: Color,
) {
    companion object {
        fun from(c: NvColors) = FigurePalette(
            fill = c.figureFill,
            far = c.figureFar,
            line = c.figureLine,
            face = if (c.isDark) lerp(c.figureFill, c.ink, 0.13f) else lerp(c.figureFill, c.surface, 0.42f),
            prop = c.inkFaint.copy(alpha = 0.55f),
            panel = lerp(c.surface, c.divider, 0.55f),
            ground = c.divider,
            markerRing = c.surface,
        )
    }
}

/** Description read by TalkBack: the pose in prose, then its annotations in order. */
fun Pose.accessibleDescription(): String = buildString {
    append(desc.trim())
    if (annotations.isNotEmpty()) {
        append(" Points to notice: ")
        annotations.forEachIndexed { i, a -> append("${i + 1}. ${a.text.trim().trimEnd('.')}. ") }
    }
}.trim()

@Composable
fun rememberScene(pose: Pose, view: String? = null): Scene =
    remember(pose, view) { PoseSolver.solveScene(pose, view) }

/**
 * A drawn figure: a person from the cast, clothed and shaded, with far-side limbs a shade deeper
 * and optional annotation markers.
 */
@Composable
fun PoseFigure(
    pose: Pose,
    modifier: Modifier = Modifier,
    style: FigureStyle = FigureStyle.Filled,
    dimmed: Boolean = false,
    markers: MarkerMode = MarkerMode.Dots,
    view: String? = null,
    frame: FigureFrame? = null,
    scene: Scene? = null,
    decorative: Boolean = false,
    tint: Color? = null,
    /** Whose figure this is. Pairs pass the same seed so both panels show the same man. */
    castSeed: String? = null,
) {
    val colors = Nv.colors
    val palette = remember(colors, tint) {
        FigurePalette.from(colors).let { if (tint != null) it.copy(line = tint) else it }
    }
    val solved = scene ?: rememberScene(pose, view)
    val looks = remember(pose, castSeed, solved.figures.size) { Cast.forScene(castSeed ?: pose.id, solved) }
    val measurer = rememberTextMeasurer()
    val numberStyle = remember { TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.sp) }
    val markerColors = pose.annotations.map { annotationColor(it.color, colors) }
    val gazeColor = colors.live
    val description = remember(pose) { pose.accessibleDescription() }

    val semantics = if (decorative) Modifier else Modifier.semantics {
        contentDescription = description
        role = Role.Image
    }

    Box(
        modifier
            .then(semantics)
            .drawWithCache {
                val geo = PoseRenderer.build(solved, size, frame ?: PoseRenderer.frameOf(solved), gaze = pose.showGaze, looks = looks)
                val anchors = pose.annotations.map { geo.anchor(it.at) }
                onDrawBehind {
                    drawFigure(geo, palette, style, dimmed)
                    geo.gaze?.let { (a, b) -> drawGaze(a, b, gazeColor) }
                    if (markers != MarkerMode.None) {
                        anchors.forEachIndexed { i, a ->
                            if (a != null) drawMarker(a, markerColors[i], palette.markerRing, i + 1, markers, measurer, numberStyle)
                        }
                    }
                }
            },
    )
}

fun DrawScope.drawFigure(geo: FigureGeometry, p: FigurePalette, style: FigureStyle, dimmed: Boolean) {
    val hair = 0.9.dp.toPx()
    val draw: DrawScope.() -> Unit = {
        // Ground and props first, quietly, inside the same layer so a dimmed figure still hides them.
        for (g in geo.grounds) {
            drawOval(p.ground, topLeft = Offset(g.center.x - g.rx, g.center.y - g.ry), size = Size(g.rx * 2, g.ry * 2))
        }
        for ((a, b) in geo.propLines) drawLine(p.prop, a, b, strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round)
        when (style) {
            FigureStyle.Filled -> {
                val shade = geo.shadeOffset
                val edge = (0.75.dp.toPx()).coerceAtMost(geo.scale * 0.55f).coerceAtLeast(0.5f)
                for (g in geo.groups) {
                    if (g.panel) {
                        drawPath(g.outline, p.panel)
                        drawPath(g.outline, p.prop, style = Stroke(1.2.dp.toPx(), join = StrokeJoin.Round))
                        continue
                    }
                    for (l in g.layers) {
                        drawPath(l.path, l.color)
                        val shaded: DrawScope.() -> Unit = {
                            // Soft light from the upper left: the far edge of every shape falls into shadow.
                            clipPath(l.path) {
                                drawPath(l.path, lerp(l.color, l.line, 0.32f))
                                translate(-shade, -shade * 0.8f) { drawPath(l.path, l.color) }
                            }
                            drawPath(l.path, l.line, style = Stroke(edge, join = StrokeJoin.Round))
                        }
                        val quiet = l.quiet
                        if (quiet == null) shaded() else clipPath(quiet, ClipOp.Difference) { shaded() }
                    }
                    for (m in g.marks) {
                        if (m.width <= 0f) drawPath(m.path, m.color)
                        else drawPath(m.path, m.color, style = Stroke(m.width, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
                // A quiet outer edge keeps dark clothes readable on a dark page and pale ones on a light page.
                drawPath(geo.silhouette, p.line.copy(alpha = 0.45f), style = Stroke(edge * 1.2f, join = StrokeJoin.Round))
            }
            FigureStyle.Outline -> drawPath(
                geo.silhouette, p.line,
                style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
    }
    if (dimmed) {
        // Group opacity: overlapping limbs must not darken where they cross.
        drawIntoCanvas { canvas ->
            canvas.saveLayer(Rect(Offset.Zero, size), Paint().apply { alpha = 0.65f })
            draw()
            canvas.restore()
        }
    } else draw()
}

/** The line of gaze: dashed from the eyes, ending in a small open circle. */
fun DrawScope.drawGaze(a: Offset, b: Offset, color: Color) {
    val w = 1.3.dp.toPx()
    drawLine(color.copy(alpha = 0.75f), a, b, strokeWidth = w, cap = StrokeCap.Round,
        pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(5.dp.toPx(), 4.dp.toPx())))
    drawCircle(color.copy(alpha = 0.75f), radius = 3.dp.toPx(), center = b, style = Stroke(w))
}

fun DrawScope.drawMarker(
    at: Offset,
    color: Color,
    ring: Color,
    number: Int,
    mode: MarkerMode,
    measurer: TextMeasurer,
    numberStyle: TextStyle,
) {
    when (mode) {
        MarkerMode.None -> Unit
        MarkerMode.Dots -> {
            drawCircle(ring, radius = 5.dp.toPx(), center = at)
            drawCircle(color, radius = 3.4.dp.toPx(), center = at)
        }
        MarkerMode.Numbered -> {
            val r = 9.dp.toPx()
            drawCircle(ring, radius = r + 1.5.dp.toPx(), center = at)
            drawCircle(color, radius = r, center = at)
            val layout = measurer.measure(number.toString(), numberStyle.copy(color = ring))
            drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
        }
    }
}
