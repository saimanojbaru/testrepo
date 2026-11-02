package com.nonverbal.mastery.design.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nonverbal.mastery.data.models.Pose
import com.nonverbal.mastery.data.models.PoseParams
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.Motion
import com.nonverbal.mastery.design.tokens.Space

/** The app mark: an open book whose pages are a pair of shoulders, a head above. */
fun DrawScope.drawAppMark(color: Color) {
    val s = size.minDimension / 108f
    val ox = (size.width - 108f * s) / 2f
    val oy = (size.height - 108f * s) / 2f
    fun p(x: Float, y: Float) = Offset(ox + x * s, oy + y * s)
    drawCircle(color, radius = 8f * s, center = p(54f, 32f))
    val left = Path().apply {
        val a = p(53.1f, 50.5f); moveTo(a.x, a.y)
        val c1 = p(46f, 45.6f); val c2 = p(38.4f, 44.6f); val e = p(31f, 46.6f); cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)
        val b = p(31f, 69.4f); lineTo(b.x, b.y)
        val d1 = p(38.4f, 67.6f); val d2 = p(46f, 68.6f); val f = p(53.1f, 73.4f); cubicTo(d1.x, d1.y, d2.x, d2.y, f.x, f.y)
        close()
    }
    val right = Path().apply {
        val a = p(54.9f, 50.5f); moveTo(a.x, a.y)
        val c1 = p(62f, 45.6f); val c2 = p(69.6f, 44.6f); val e = p(77f, 46.6f); cubicTo(c1.x, c1.y, c2.x, c2.y, e.x, e.y)
        val b = p(77f, 69.4f); lineTo(b.x, b.y)
        val d1 = p(69.6f, 67.6f); val d2 = p(62f, 68.6f); val f = p(54.9f, 73.4f); cubicTo(d1.x, d1.y, d2.x, d2.y, f.x, f.y)
        close()
    }
    drawPath(left, color); drawPath(right, color)
}

@Composable
fun AppMark(modifier: Modifier = Modifier, size: Dp = 64.dp, color: Color = Nv.colors.brass) {
    Canvas(modifier.size(size).semantics { contentDescription = "Nonverbal Mastery" }) { drawAppMark(color) }
}

/** Loading: a single brass pulse on the app mark. Never a spinner. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    val reduced = Nv.reducedMotion
    val pulse = if (reduced) 1f else {
        val t = rememberInfiniteTransition(label = "pulse")
        val v by t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(900, easing = Motion.Enter), RepeatMode.Reverse), label = "a")
        v
    }
    Box(modifier.fillMaxSize().semantics { contentDescription = "Loading" }, contentAlignment = Alignment.Center) {
        AppMark(Modifier.scale(0.94f + 0.06f * pulse), size = 72.dp, color = Nv.colors.brass.copy(alpha = pulse))
    }
}

/** A seated figure drawn as a single quiet line: the empty state's illustration. */
private val SeatedQuiet = Pose(
    id = "empty_seated",
    desc = "A line drawing of a figure sitting quietly on a chair, hands resting on the thighs.",
    params = PoseParams(
        armPoseL = "thighs_rest", armPoseR = "lap_clasp", handShapeL = "relaxed", handShapeR = "relaxed",
        legStanceL = "sit", legStanceR = "sit", spineLean = -3f, headPitch = 8f, headTilt = 3f, view = "three-quarter",
    ),
)

@Composable
fun EmptyState(
    sentence: String,
    modifier: Modifier = Modifier,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Space.x8, vertical = Space.x10),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PoseFigure(
            SeatedQuiet,
            Modifier.size(width = 140.dp, height = 170.dp),
            style = FigureStyle.Outline,
            markers = MarkerMode.None,
            decorative = true,
            tint = Nv.colors.inkFaint,
        )
        Spacer(Modifier.height(Space.x6))
        Text(sentence, style = Nv.type.marginalia, color = Nv.colors.inkSecondary, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(Space.x6))
            NvButton(action, onAction, kind = ButtonKind.Secondary)
        }
    }
}

/** Error: a short, warm sentence. No codes, no exclamation marks. */
@Composable
fun ErrorState(
    sentence: String = "The pages didn't open this time. Your progress is safe; close the app and open it again.",
    modifier: Modifier = Modifier,
    onRetry: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxSize().padding(Space.x8),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        AppMark(size = 48.dp, color = Nv.colors.inkFaint)
        Spacer(Modifier.height(Space.x6))
        Text(sentence, style = Nv.type.marginalia, color = Nv.colors.inkSecondary, textAlign = TextAlign.Center)
        if (onRetry != null) {
            Spacer(Modifier.height(Space.x6))
            NvButton("Try again", onRetry, kind = ButtonKind.Secondary)
        }
    }
}

/** A brass ring around an icon or numeral, sweeping to [progress]. */
@Composable
fun ProgressRing(
    progress: Float,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    stroke: Dp = 2.dp,
    color: Color = Nv.colors.brass,
    content: @Composable () -> Unit = {},
) {
    val track = Nv.colors.divider
    val reduced = Nv.reducedMotion
    val anim = remember { Animatable(if (reduced) progress else 0f) }
    LaunchedEffect(progress, reduced) {
        if (reduced) anim.snapTo(progress) else anim.animateTo(progress, tween(Motion.DURATION_LONG * 2, easing = Motion.Enter))
    }
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val w = stroke.toPx()
            val inset = w / 2
            drawArc(track, 0f, 360f, false, Offset(inset, inset), Size(this.size.width - w, this.size.height - w), style = Stroke(w))
            if (anim.value > 0f) {
                drawArc(color, -90f, 360f * anim.value.coerceIn(0f, 1f), false, Offset(inset, inset),
                    Size(this.size.width - w, this.size.height - w), style = Stroke(w, cap = StrokeCap.Round))
            }
        }
        content()
    }
}

/**
 * The mastery reward: an 800ms brass ring trace, ending in a drawn check.
 * With reduced motion, the finished badge simply appears.
 */
@Composable
fun MasteryBadge(modifier: Modifier = Modifier, size: Dp = 56.dp, animate: Boolean = true) {
    val colors = Nv.colors
    val reduced = Nv.reducedMotion
    val play = animate && !reduced
    val ring = remember { Animatable(if (play) 0f else 1f) }
    val check = remember { Animatable(if (play) 0f else 1f) }
    LaunchedEffect(play) {
        if (play) {
            ring.animateTo(1f, tween((Motion.MASTERY_TRACE * 0.7f).toInt(), easing = Motion.Enter))
            check.animateTo(1f, tween((Motion.MASTERY_TRACE * 0.3f).toInt(), easing = LinearEasing))
        }
    }
    Canvas(modifier.size(size).semantics { contentDescription = "Mastered" }) {
        val w = 2.dp.toPx()
        val inset = w / 2
        drawArc(colors.brass, -90f, 360f * ring.value, false, Offset(inset, inset),
            Size(this.size.width - w, this.size.height - w), style = Stroke(w, cap = StrokeCap.Round))
        val r = this.size.minDimension / 2
        val p1 = Offset(r * 0.62f, r * 1.02f)
        val p2 = Offset(r * 0.9f, r * 1.3f)
        val p3 = Offset(r * 1.42f, r * 0.72f)
        val t = check.value
        if (t > 0f) {
            val a = (t * 2f).coerceAtMost(1f)
            drawLine(colors.brass, p1, p1 + (p2 - p1) * a, w * 1.1f, StrokeCap.Round)
            if (t > 0.5f) drawLine(colors.brass, p2, p2 + (p3 - p2) * ((t - 0.5f) * 2f), w * 1.1f, StrokeCap.Round)
        }
    }
}

/** A small brass-ringed check for lists. Static. */
@Composable
fun MasteredMark(modifier: Modifier = Modifier, size: Dp = 22.dp) = MasteryBadge(modifier, size, animate = false)

@Composable
fun Gap(h: Dp) = Spacer(Modifier.height(h))

@Composable
fun HGap(w: Dp) = Spacer(Modifier.width(w))
