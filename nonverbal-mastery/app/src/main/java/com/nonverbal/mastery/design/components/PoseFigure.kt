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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
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
import com.nonverbal.mastery.design.pose.FigureFrame
import com.nonverbal.mastery.design.pose.FigureGeometry
import com.nonverbal.mastery.design.pose.PoseRenderer
import com.nonverbal.mastery.design.pose.PoseSolver
import com.nonverbal.mastery.design.pose.Skeleton
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
fun rememberSkeleton(pose: Pose, view: String? = null): Skeleton =
    remember(pose, view) { PoseSolver.solve(pose.params, view) }

/**
 * A drawn figure. Filled silhouettes with per-limb hairlines (so arms crossing the body stay
 * legible), far-side limbs a shade deeper, and optional annotation markers.
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
    skeleton: Skeleton? = null,
    decorative: Boolean = false,
    tint: Color? = null,
) {
    val colors = Nv.colors
    val palette = remember(colors, tint) {
        FigurePalette.from(colors).let { if (tint != null) it.copy(line = tint) else it }
    }
    val solved = skeleton ?: rememberSkeleton(pose, view)
    val measurer = rememberTextMeasurer()
    val numberStyle = remember { TextStyle(fontFamily = Inter, fontWeight = FontWeight.SemiBold, fontSize = 10.sp) }
    val markerColors = pose.annotations.map { annotationColor(it.color, colors) }
    val description = remember(pose) { pose.accessibleDescription() }

    val semantics = if (decorative) Modifier else Modifier.semantics {
        contentDescription = description
        role = Role.Image
    }

    Box(
        modifier
            .then(semantics)
            .drawWithCache {
                val geo = PoseRenderer.build(solved, size, frame ?: PoseRenderer.frameOf(solved))
                val anchors = pose.annotations.map { geo.anchor(it.at) }
                onDrawBehind {
                    drawFigure(geo, palette, style, dimmed)
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
    // Ground and props first, quietly.
    drawOval(
        p.ground,
        topLeft = Offset(geo.groundCenter.x - geo.groundRadiusX, geo.groundCenter.y - geo.groundRadiusY),
        size = Size(geo.groundRadiusX * 2, geo.groundRadiusY * 2),
    )
    for ((a, b) in geo.propLines) drawLine(p.prop, a, b, strokeWidth = 1.2.dp.toPx(), cap = StrokeCap.Round)

    val draw: DrawScope.() -> Unit = {
        when (style) {
            FigureStyle.Filled -> for (g in geo.groups) {
                drawPath(g.path, if (g.far) p.far else p.fill)
                drawPath(g.path, if (g.far) p.line.copy(alpha = 0.55f) else p.line, style = Stroke(hair, join = StrokeJoin.Round))
            }
            FigureStyle.Outline -> drawPath(
                geo.silhouette, p.line,
                style = Stroke(1.4.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
        }
        if (style == FigureStyle.Filled) geo.face?.let { f ->
            rotate(f.rotationDeg, f.center) {
                drawOval(
                    p.face.copy(alpha = 0.85f * f.strength),
                    topLeft = Offset(f.center.x - f.rx, f.center.y - f.ry),
                    size = Size(f.rx * 2, f.ry * 2),
                )
            }
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
