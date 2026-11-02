package com.nonverbal.mastery.design.pose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/** Orthographic camera orbiting the figure. Screen y grows downward. */
class Projector(yawDeg: Float, val scale: Float, private val ox: Float, private val oy: Float) {
    private val c = cos(Math.toRadians(yawDeg.toDouble())).toFloat()
    private val s = sin(Math.toRadians(yawDeg.toDouble())).toFloat()
    val viewX = V3(c, 0f, s)
    fun sx(p: V3) = p.x * c + p.z * s
    fun depth(p: V3) = -p.x * s + p.z * c
    fun screen(p: V3) = Offset(ox + scale * sx(p), oy - scale * p.y)
}

/** The vertical extent and width a figure (or a pair of figures) is fitted into. */
data class FigureFrame(val top: Float, val width: Float) {
    fun union(o: FigureFrame) = FigureFrame(max(top, o.top), max(width, o.width))
}

data class FigureGroup(val path: Path, val depth: Float, val far: Boolean)
data class FaceMark(val center: Offset, val rx: Float, val ry: Float, val rotationDeg: Float, val strength: Float)

class FigureGeometry(
    val groups: List<FigureGroup>,
    val silhouette: Path,
    val face: FaceMark?,
    val propLines: List<Pair<Offset, Offset>>,
    val groundCenter: Offset,
    val groundRadiusX: Float,
    val groundRadiusY: Float,
    val scale: Float,
    private val skeleton: Skeleton,
    private val projector: Projector,
) {
    fun anchor(name: String): Offset? = skeleton.anchor(name)?.let(projector::screen)
}

object PoseRenderer {
    private const val BOTTOM = -3f

    /** Width and height a skeleton occupies, before scaling. */
    fun frameOf(sk: Skeleton): FigureFrame {
        val pr = Projector(sk.yaw, 1f, 0f, 0f)
        val xs = keyPoints(sk).map(pr::sx)
        val width = (xs.maxOrNull() ?: 30f) - (xs.minOrNull() ?: -30f) + 30f
        val top = sk.head.y + 12f
        return FigureFrame(top, width)
    }

    private fun keyPoints(sk: Skeleton): List<V3> = buildList {
        add(sk.head); add(sk.pelvis); add(sk.chest)
        for (s in listOf(sk.left, sk.right)) {
            add(s.shoulder); add(s.elbow); add(s.hand.wrist); add(s.hand.wrist + s.hand.dir * 14f)
            add(s.knee); add(s.ankle); add(s.foot.toe); add(s.foot.heel)
        }
        for (sec in sk.sections) {
            add(sec.center + sec.lateral * sec.halfWidth); add(sec.center - sec.lateral * sec.halfWidth)
            add(sec.center + sec.depth * sec.halfDepth); add(sec.center - sec.depth * sec.halfDepth)
        }
    }

    fun build(sk: Skeleton, size: Size, frame: FigureFrame = frameOf(sk), padding: Float = 0.06f): FigureGeometry {
        val worldH = frame.top - BOTTOM
        val usableH = size.height * (1f - padding * 2f)
        val usableW = size.width * (1f - padding * 2f)
        val scale = min(usableH / worldH, usableW / frame.width)

        val probe = Projector(sk.yaw, 1f, 0f, 0f)
        val xs = keyPoints(sk).map(probe::sx)
        val midX = ((xs.maxOrNull() ?: 0f) + (xs.minOrNull() ?: 0f)) / 2f
        val ox = size.width / 2f - midX * scale
        val oy = size.height / 2f + (frame.top + BOTTOM) / 2f * scale
        val pr = Projector(sk.yaw, scale, ox, oy)

        val parts = mutableListOf<Part>()
        val torsoDepth = pr.depth(sk.waist) + 0.5f
        parts += Part("torso", "torso", torsoPath(sk, pr), torsoDepth, far = false)

        // Neck and head share an id so they merge into one silhouette.
        parts += Part("neck", "head", capsule(pr, sk.neck - sk.chestAxes.yAxis * 1f, 5.5f, sk.head - sk.headAxes.yAxis * 4f, 4.9f),
            pr.depth(sk.neck) + 0.6f, false)
        parts += Part("head", "head", headPath(sk, pr), pr.depth(sk.head) + 1f, false)

        for ((name, side) in listOf("L" to sk.left, "R" to sk.right)) {
            val farSide = pr.depth(side.shoulder) < torsoDepth - 7f
            val upper = union(
                circle(pr, side.shoulder, 5.2f),
                capsule(pr, side.shoulder, 4.8f, side.elbow, 3.9f),
            )
            parts += Part("upperArm$name", "arm$name", upper,
                (pr.depth(side.shoulder) + pr.depth(side.elbow)) / 2f + 0.8f, farSide)
            val lower = union(capsule(pr, side.elbow, 3.95f, side.hand.wrist, 2.95f), handPath(pr, side.hand))
            val tip = side.hand.wrist + side.hand.dir * 8f
            parts += Part("forearm$name", "arm$name", lower,
                max(pr.depth(side.elbow), max(pr.depth(side.hand.wrist), pr.depth(tip))) + 0.8f, farSide)

            val farLeg = pr.depth(side.hip) < torsoDepth - 5f
            val thigh = capsule(pr, side.hip, 8.7f, side.knee, 5.7f)
            parts += Part("thigh$name", "leg$name", thigh, (pr.depth(side.hip) + pr.depth(side.knee)) / 2f - 3f, farLeg)
            val shin = union(
                capsule(pr, side.knee, 5.6f, side.ankle, 3.6f),
                capsule(pr, side.foot.heel, 3.6f, side.foot.toe, 2.6f),
                capsule(pr, side.foot.ballInner, 2.9f, side.foot.ballOuter, 2.9f),
            )
            parts += Part("shin$name", "leg$name", shin, (pr.depth(side.knee) + pr.depth(side.ankle)) / 2f - 3f, farLeg)
        }

        // Painter's algorithm, then merge neighbours from the same limb so joints read as one form.
        val sorted = parts.sortedBy { it.depth }
        val merged = mutableListOf<Part>()
        for (part in sorted) {
            val last = merged.lastOrNull()
            if (last != null && last.limb == part.limb) {
                merged[merged.lastIndex] = last.copy(path = union(last.path, part.path), depth = max(last.depth, part.depth))
            } else merged += part
        }
        val groups = merged.map { FigureGroup(it.path, it.depth, it.far) }
        val silhouette = groups.fold(Path()) { acc, g -> union(acc, g.path) }

        return FigureGeometry(
            groups = groups,
            silhouette = silhouette,
            face = faceMark(sk, pr),
            propLines = propLines(sk, pr),
            groundCenter = pr.screen(sk.left.foot.ballInner.lerp(sk.right.foot.ballInner, 0.5f).let { V3(it.x, 0f, it.z - 3f) }),
            groundRadiusX = (abs(pr.sx(sk.left.foot.toe) - pr.sx(sk.right.foot.toe)) / 2f + 18f) * scale,
            groundRadiusY = 3.2f * scale,
            scale = scale,
            skeleton = sk,
            projector = pr,
        )
    }

    private data class Part(val id: String, val limb: String, val path: Path, val depth: Float, val far: Boolean)

    // ---- Shapes ---------------------------------------------------------------------------

    private fun torsoPath(sk: Skeleton, pr: Projector): Path {
        val minSide = mutableListOf<Offset>()
        val maxSide = mutableListOf<Offset>()
        for (sec in sk.sections) {
            // Extreme points of the projected ellipse along screen x.
            val a = sec.halfWidth * (sec.lateral dot pr.viewX)
            val b = sec.halfDepth * (sec.depth dot pr.viewX)
            val t = atan2(b, a)
            val ext = sec.lateral * (sec.halfWidth * cos(t)) + sec.depth * (sec.halfDepth * sin(t))
            val p1 = pr.screen(sec.center + ext)
            val p2 = pr.screen(sec.center - ext)
            if (p1.x >= p2.x) { maxSide += p1; minSide += p2 } else { maxSide += p2; minSide += p1 }
        }
        val top = pr.screen(sk.chest + sk.chestAxes.apply(v(0, 5.5, -1)))
        val crotch = pr.screen(sk.pelvis + sk.lowerAxes.apply(v(0, -11.5, 0.5)))
        val pts = buildList {
            addAll(minSide)
            add(top)
            addAll(maxSide.reversed())
            add(crotch)
        }
        return smoothClosed(pts)
    }

    private fun headPath(sk: Skeleton, pr: Projector): Path {
        val h = sk.headAxes
        return union(
            ellipsoid(pr, sk.head + h.apply(v(0, 1, -0.5)), h, 7.9f, 10.9f, 10f),
            ellipsoid(pr, sk.head + h.apply(v(0, -4.6, 3.4)), h, 5.6f, 6.4f, 6.4f),
            circle(pr, sk.head + h.apply(v(0, -0.6, 9.4)), 1.5f),
        )
    }

    private fun faceMark(sk: Skeleton, pr: Projector): FaceMark? {
        val forward = sk.headAxes.zAxis
        val towardViewer = pr.depth(sk.head + forward) - pr.depth(sk.head)
        if (towardViewer < 0.25f) return null
        val c = pr.screen(sk.head + sk.headAxes.apply(v(0, -1.2, 7.4)))
        val up = sk.headAxes.yAxis
        val upScreen = pr.screen(sk.head + up) - pr.screen(sk.head)
        val rot = Math.toDegrees(atan2(upScreen.x.toDouble(), -upScreen.y.toDouble())).toFloat()
        return FaceMark(c, 4.3f * pr.scale * towardViewer, 6.4f * pr.scale, rot, ((towardViewer - 0.25f) / 0.75f).coerceIn(0f, 1f))
    }

    private fun handPath(pr: Projector, hand: Hand): Path {
        val w = hand.wrist
        val d = hand.dir
        val t = hand.thumb
        fun at(along: Float, side: Float = 0f) = w + d * along + t * side
        return when (hand.shape) {
            HandShape.HIDDEN, HandShape.TUCKED -> circle(pr, w, 2.9f)
            HandShape.FIST -> union(circle(pr, at(4.6f), 4.1f), capsule(pr, w, 3.1f, at(3f), 3.4f))
            HandShape.LOOSE_FIST, HandShape.GRIP -> union(capsule(pr, w, 3.1f, at(5.5f), 3.6f), circle(pr, at(6.2f, 0.5f), 3.3f))
            HandShape.POINT -> union(circle(pr, at(4.4f), 3.9f), capsule(pr, at(6f), 1.5f, at(14f), 1.2f))
            HandShape.CLASP -> union(capsule(pr, w, 3.1f, at(7f), 3.5f), circle(pr, at(7.5f), 3.2f))
            HandShape.STEEPLE -> union(capsule(pr, w, 3.0f, at(8.5f), 2.7f), capsule(pr, at(8f), 2.2f, at(15.5f), 1.5f))
            HandShape.OPEN, HandShape.PALM_UP -> union(
                capsule(pr, w, 3.1f, at(9f), 3.1f),
                capsule(pr, at(8.5f), 2.5f, at(17f), 1.9f),
                capsule(pr, at(2.5f, 1.5f), 1.5f, at(7.5f, 4.6f), 1.2f),
            )
            HandShape.FLAT, HandShape.RUB -> union(
                capsule(pr, w, 3.1f, at(9f), 3.0f),
                capsule(pr, at(8.5f), 2.5f, at(16.5f), 1.9f),
                capsule(pr, at(2.5f, 1.2f), 1.5f, at(7f, 2.6f), 1.2f),
            )
            HandShape.CUP -> union(capsule(pr, w, 3.1f, at(8f), 3.2f), circle(pr, at(10f), 2.7f))
            HandShape.PINCH -> union(capsule(pr, w, 3.0f, at(8f), 2.8f), capsule(pr, at(8f), 2.0f, at(12f), 1.4f))
            HandShape.RELAXED -> union(
                capsule(pr, w, 3.05f, at(8.5f), 2.8f),
                capsule(pr, at(8f), 2.4f, at(14.5f, -0.6f), 1.9f),
                capsule(pr, at(2.5f, 1.4f), 1.5f, at(6.5f, 3.2f), 1.2f),
            )
        }
    }

    private fun propLines(sk: Skeleton, pr: Projector): List<Pair<Offset, Offset>> {
        val lines = mutableListOf<Pair<V3, V3>>()
        when (sk.prop) {
            "chair" -> {
                val y = 44f; val x0 = -21f; val x1 = 21f; val zb = -22f; val zf = 18f; val back = 92f
                lines += v(x0, y, zb) to v(x1, y, zb); lines += v(x0, y, zf) to v(x1, y, zf)
                lines += v(x0, y, zb) to v(x0, y, zf); lines += v(x1, y, zb) to v(x1, y, zf)
                for (x in listOf(x0, x1)) for (z in listOf(zb, zf)) lines += v(x, 0, z) to v(x, y, z)
                for (x in listOf(x0, x1)) lines += v(x, y, zb) to v(x, back, zb - 4)
                lines += v(x0, back, zb - 4) to v(x1, back, zb - 4)
                lines += v(x0, 74, zb - 3) to v(x1, 74, zb - 3)
            }
            "stool" -> {
                val y = 66f
                lines += v(-17, y, -17) to v(17, y, -17); lines += v(-17, y, 17) to v(17, y, 17)
                lines += v(-17, y, -17) to v(-17, y, 17); lines += v(17, y, -17) to v(17, y, 17)
                for (x in listOf(-15f, 15f)) for (z in listOf(-15f, 15f)) lines += v(x, 0, z) to v(x, y, z)
            }
            "table" -> {
                val y = 74f
                lines += v(-45, y, 14) to v(45, y, 14); lines += v(-45, y, 80) to v(45, y, 80)
                lines += v(-45, y, 14) to v(-45, y, 80); lines += v(45, y, 14) to v(45, y, 80)
                for (x in listOf(-40f, 40f)) lines += v(x, 0, 20) to v(x, y, 20)
            }
        }
        return lines.map { (a, b) -> pr.screen(a) to pr.screen(b) }
    }

    // ---- Primitives ----------------------------------------------------------------------

    private fun circle(pr: Projector, c: V3, r: Float): Path {
        val o = pr.screen(c)
        val rr = r * pr.scale
        return Path().apply { addOval(androidx.compose.ui.geometry.Rect(o.x - rr, o.y - rr, o.x + rr, o.y + rr)) }
    }

    /** An ellipsoid with semi-axes along [axes], projected exactly onto the screen. */
    private fun ellipsoid(pr: Projector, c: V3, axes: M3, rx: Float, ry: Float, rz: Float): Path {
        val center = pr.screen(c)
        // Project the three semi-axes; the outline of an ellipsoid under orthographic projection
        // is the ellipse spanned by those projections. Sample it densely.
        val ax = pr.screen(c + axes.xAxis * rx) - center
        val ay = pr.screen(c + axes.yAxis * ry) - center
        val az = pr.screen(c + axes.zAxis * rz) - center
        val pts = ArrayList<Offset>(48)
        val n = 48
        for (i in 0 until n) {
            val th = 2.0 * PI * i / n
            val dx = cos(th).toFloat(); val dy = sin(th).toFloat()
            // Support function: the furthest extent of the ellipsoid in direction (dx, dy).
            val ex = ax.x * dx + ax.y * dy
            val ey = ay.x * dx + ay.y * dy
            val ez = az.x * dx + az.y * dy
            val h = sqrt(ex * ex + ey * ey + ez * ez)
            // Point of contact = sum of axes weighted by their share of the support.
            if (h < 1e-4f) { pts += center; continue }
            pts += Offset(
                center.x + (ax.x * ex + ay.x * ey + az.x * ez) / h,
                center.y + (ax.y * ex + ay.y * ey + az.y * ez) / h,
            )
        }
        return polygon(pts)
    }

    /** A tapered capsule: two circles joined by their outer tangents. */
    private fun capsule(pr: Projector, a3: V3, ra0: Float, b3: V3, rb0: Float): Path {
        val a = pr.screen(a3); val b = pr.screen(b3)
        val ra = ra0 * pr.scale; val rb = rb0 * pr.scale
        val dx = b.x - a.x; val dy = b.y - a.y
        val d = sqrt(dx * dx + dy * dy)
        if (d <= abs(ra - rb) + 0.01f) {
            return if (ra >= rb) Path().apply { addOval(androidx.compose.ui.geometry.Rect(a.x - ra, a.y - ra, a.x + ra, a.y + ra)) }
            else Path().apply { addOval(androidx.compose.ui.geometry.Rect(b.x - rb, b.y - rb, b.x + rb, b.y + rb)) }
        }
        val theta = atan2(dy, dx)
        val alpha = kotlin.math.acos(((ra - rb) / d).coerceIn(-1f, 1f))
        val pts = ArrayList<Offset>(40)
        val segB = 14; val segA = 20
        // Cap around B: from theta+alpha down to theta-alpha, passing theta.
        for (i in 0..segB) {
            val ang = theta + alpha - (2f * alpha) * i / segB
            pts += Offset(b.x + rb * cos(ang), b.y + rb * sin(ang))
        }
        // Cap around A: from theta-alpha down to theta+alpha-2pi, passing theta+pi.
        val sweepA = (2f * PI.toFloat() - 2f * alpha)
        for (i in 0..segA) {
            val ang = theta - alpha - sweepA * i / segA
            pts += Offset(a.x + ra * cos(ang), a.y + ra * sin(ang))
        }
        return polygon(pts)
    }

    private fun polygon(pts: List<Offset>): Path = Path().apply {
        if (pts.isEmpty()) return@apply
        moveTo(pts[0].x, pts[0].y)
        for (i in 1 until pts.size) lineTo(pts[i].x, pts[i].y)
        close()
    }

    /** Closed curve through the midpoints of [pts], with the points as control handles. */
    private fun smoothClosed(pts: List<Offset>): Path = Path().apply {
        val n = pts.size
        if (n < 3) return@apply
        fun mid(i: Int, j: Int) = Offset((pts[i].x + pts[j].x) / 2f, (pts[i].y + pts[j].y) / 2f)
        val start = mid(n - 1, 0)
        moveTo(start.x, start.y)
        for (i in 0 until n) {
            val m = mid(i, (i + 1) % n)
            quadraticTo(pts[i].x, pts[i].y, m.x, m.y)
        }
        close()
    }

    private fun union(vararg paths: Path): Path {
        var acc = paths.first()
        for (i in 1 until paths.size) {
            val r = Path()
            r.op(acc, paths[i], PathOperation.Union)
            acc = r
        }
        return acc
    }
}
