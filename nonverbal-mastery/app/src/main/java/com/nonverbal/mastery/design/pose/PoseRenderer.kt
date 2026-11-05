package com.nonverbal.mastery.design.pose

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
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
    /** Unit vector from the scene toward the camera. A surface faces the viewer when its normal points this way. */
    val toViewer = V3(-s, 0f, c)
    fun sx(p: V3) = p.x * c + p.z * s
    fun depth(p: V3) = -p.x * s + p.z * c
    fun screen(p: V3) = Offset(ox + scale * sx(p), oy - scale * p.y)
}

/** The vertical extent and width a figure (or a pair of figures) is fitted into. */
data class FigureFrame(val top: Float, val width: Float) {
    fun union(o: FigureFrame) = FigureFrame(max(top, o.top), max(width, o.width))
}

/**
 * One filled shape in one material (skin, shirt, trousers, shoes, hair), with its own edge colour.
 * Inside [quiet] it is filled flat, with no edge or shading, so a limb joins the body without a seam.
 */
data class Layer(val path: Path, val color: Color, val line: Color, val quiet: Path? = null)

/** A detail drawn over a figure's shapes: an eye, a brow, a mouth, a seam, a sole. Width 0 means filled. */
data class Mark(val path: Path, val color: Color, val width: Float)

data class FigureGroup(
    val layers: List<Layer>,
    val marks: List<Mark>,
    val outline: Path,
    val depth: Float,
    val far: Boolean,
    val panel: Boolean = false,
)
data class Ground(val center: Offset, val rx: Float, val ry: Float)

class FigureGeometry(
    val groups: List<FigureGroup>,
    val silhouette: Path,
    val propLines: List<Pair<Offset, Offset>>,
    val grounds: List<Ground>,
    /** Line of gaze for the main figure, when the pose asks for it: eyes to a point ahead. */
    val gaze: Pair<Offset, Offset>?,
    val scale: Float,
    /** How far, in pixels, the soft shadow side of each shape reaches. */
    val shadeOffset: Float,
    private val scene: Scene,
    private val projector: Projector,
) {
    fun anchor(name: String): Offset? = scene.anchor(name)?.let(projector::screen)
}

object PoseRenderer {
    private const val BOTTOM = -3f

    fun frameOf(sk: Skeleton): FigureFrame = frameOf(Scene(listOf(sk), sk.yaw))

    /** Width and height a scene occupies, before scaling. */
    fun frameOf(scene: Scene): FigureFrame {
        val pr = Projector(scene.yaw, 1f, 0f, 0f)
        val xs = scene.figures.flatMap { keyPoints(it) }.map(pr::sx)
        val width = (xs.maxOrNull() ?: 30f) - (xs.minOrNull() ?: -30f) + 30f
        val top = scene.figures.maxOf { it.head.y + 12f * it.placement.scale }
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

    fun build(sk: Skeleton, size: Size, frame: FigureFrame = frameOf(sk), padding: Float = 0.06f, look: Look = Cast.men.first()): FigureGeometry =
        build(Scene(listOf(sk), sk.yaw), size, frame, padding, looks = listOf(look))

    fun build(
        scene: Scene,
        size: Size,
        frame: FigureFrame = frameOf(scene),
        padding: Float = 0.06f,
        gaze: Boolean = false,
        looks: List<Look> = Cast.forScene("", scene),
    ): FigureGeometry {
        val worldH = frame.top - BOTTOM
        val usableH = size.height * (1f - padding * 2f)
        val usableW = size.width * (1f - padding * 2f)
        val scale = min(usableH / worldH, usableW / frame.width)

        val probe = Projector(scene.yaw, 1f, 0f, 0f)
        val xs = scene.figures.flatMap { keyPoints(it) }.map(probe::sx)
        val midX = ((xs.maxOrNull() ?: 0f) + (xs.minOrNull() ?: 0f)) / 2f
        val ox = size.width / 2f - midX * scale
        val oy = size.height / 2f + (frame.top + BOTTOM) / 2f * scale
        val pr = Projector(scene.yaw, scale, ox, oy)

        val parts = mutableListOf<Part>()
        scene.figures.forEachIndexed { f, sk ->
            val look = looks.getOrNull(f) ?: Cast.men[f % Cast.men.size]
            parts += Dresser(f, sk, pr, look).parts() + propPanels(f, sk, pr)
        }

        // Painter's algorithm across every figure, then merge neighbours from the same limb.
        val sorted = parts.sortedBy { it.depth }
        val merged = mutableListOf<Part>()
        for (part in sorted) {
            val last = merged.lastOrNull()
            if (last != null && last.limb == part.limb) {
                merged[merged.lastIndex] = last.copy(
                    shapes = last.shapes + part.shapes, marks = last.marks + part.marks, depth = max(last.depth, part.depth),
                )
            } else merged += part
        }
        val groups = merged.map { it.finish() }
        val silhouette = groups.filterNot { it.panel }.fold(Path()) { acc, g -> union(acc, g.outline) }

        // A carried child or a person lying on a bed casts no floor shadow of their own.
        val grounds = scene.figures.filterNot { it.placement.lifted }.map { sk ->
            val k = sk.placement.scale
            val mid = sk.left.foot.ballInner.lerp(sk.right.foot.ballInner, 0.5f)
            Ground(
                pr.screen(V3(mid.x, 0f, mid.z - 3f * k)),
                (abs(pr.sx(sk.left.foot.toe) - pr.sx(sk.right.foot.toe)) / 2f + 18f * k) * scale,
                3.2f * k * scale,
            )
        }

        val gazeLine = if (!gaze) null else {
            val main = scene.main
            val eyes = main.anchor("eyes") ?: main.head
            val a = pr.screen(eyes)
            val b = pr.screen(eyes + main.headAxes.zAxis * 75f)
            if ((b - a).getDistance() < 10f * scale) null else a to b
        }

        return FigureGeometry(
            groups = groups,
            silhouette = silhouette,
            propLines = scene.figures.flatMap { propLines(it, pr) },
            grounds = grounds,
            gaze = gazeLine,
            scale = scale,
            shadeOffset = 0.75f * scale,
            scene = scene,
            projector = pr,
        )
    }

    /** Materials, in the order they are painted within one limb or head. */
    private enum class Mat { SKIN, BOTTOM, TOP, SHOE, SKIN_OVER, EAR, HAIR, BEARD, PROP }

    private data class Shape(val mat: Mat, val path: Path, val color: Color, val quiet: Path? = null)

    private data class Part(
        val limb: String,
        val shapes: List<Shape>,
        val depth: Float,
        val far: Boolean,
        val marks: List<Mark> = emptyList(),
        val panel: Boolean = false,
    ) {
        fun finish(): FigureGroup {
            // One union per material and colour, painted in material order, so a limb has no seams of its own.
            val layers = shapes.groupBy { it.mat to it.color }.entries
                .sortedBy { it.key.first.ordinal }
                .map { (key, list) ->
                    val c = if (far) shadeOf(key.second, 0.1f) else key.second
                    val quiets = list.mapNotNull { it.quiet }
                    Layer(union(*list.map { it.path }.toTypedArray()), c, shadeOf(c, 0.42f),
                        if (quiets.isEmpty()) null else union(*quiets.toTypedArray()))
                }
            val outline = if (layers.isEmpty()) Path() else union(*layers.map { it.path }.toTypedArray())
            return FigureGroup(layers, marks, outline, depth, far, panel)
        }
    }

    /**
     * Dresses one solved skeleton as a person: skin, clothes, shoes, hair, a face. Every shape is
     * still placed from the skeleton's joints, so poses, views and companions work as before.
     */
    private class Dresser(val f: Int, val sk: Skeleton, val pr: Projector, val look: Look) {
        val k = sk.placement.scale
        val baby = look.age == Age.BABY
        val child = look.age == Age.CHILD
        val woman = look.woman && !baby && !child
        /** Children's heads are larger for their bodies. */
        val hk = k * when { baby -> 1.6f; child -> 1.24f; woman -> 0.98f; else -> 1.05f }
        val b = look.build
        /** Limb thickness follows build, gently; women's arms and legs are slighter. */
        val lw = 1f + (b - 1f) * 0.8f
        val armW = lw * (if (woman) 0.86f else 1f)
        val legW = lw * (if (woman) 0.93f else 1f)
        val skirt = look.bottom == Bottom.SKIRT
        val skin = look.skin
        val parts = mutableListOf<Part>()
        /** The top's outline, kept so the sleeves can join it without a seam. */
        var shirtPath: Path = Path()
        var trousersPath: Path = Path()

        fun facing(n: V3) = n.normalized() dot pr.toViewer

        fun parts(): List<Part> {
            torso()
            head()
            for ((name, side) in listOf("L" to sk.left, "R" to sk.right)) {
                arm(name, side)
                leg(name, side)
            }
            return parts
        }

        // ---- Torso ------------------------------------------------------------------------

        private fun torso() {
            val male = !baby && !child && !woman
            // A man's trunk: shoulders and chest a little broader, hips a little narrower. A woman's: a narrower
            // waist and shoulders, fuller hips and bust. Build widens the waist and deepens the belly most.
            val widthShape = when {
                male -> floatArrayOf(0.95f, 0.94f, 0.95f, 1.0f, 1.05f, 1.08f)
                woman -> floatArrayOf(1.07f, 1.02f, 0.86f, 0.9f, 0.95f, 0.91f)
                else -> FloatArray(6) { 1f }
            }
            val depthShape = if (woman) floatArrayOf(1.04f, 1f, 0.94f, 1.04f, 1.12f, 1f) else FloatArray(6) { 1f }
            val heavy = b - 1f
            val secs = sk.sections.mapIndexed { i, sec ->
                val belly = if (i in 1..3) 1f + heavy * 2.6f else 1f + heavy * 1.2f
                val waist = if (i in 1..3) 1f + heavy * 2.0f else 1f + heavy * 0.9f
                sec.copy(halfWidth = sec.halfWidth * widthShape[i] * waist,
                    halfDepth = sec.halfDepth * belly * depthShape[i] * (if (baby) 1.12f else 1f))
            }
            val ext = secs.map { extremes(it) }
            val depth = pr.depth(sk.waist) + 0.5f * k

            // Trousers: hips down to the crotch, closed across the waist.
            val crotch = pr.screen(sk.pelvis + sk.lowerAxes.apply(v(0, -11.5, 0.5)) * k)
            val trousers = smoothClosed(buildList {
                for (i in 0..2) add(ext[i].first)
                for (i in 2 downTo 0) add(ext[i].second)
                add(crotch)
            })

            // Top: from just below the waist to the shoulders, hanging a little loose at the hem.
            val hem = sk.sections[1].center - sk.lowerAxes.yAxis * (1.5f * k)
            val hemExt = extremes(secs[1].copy(center = hem, halfWidth = secs[1].halfWidth * 1.05f, halfDepth = secs[1].halfDepth * 1.04f))
            val top = pr.screen(sk.chest + sk.chestAxes.apply(v(0, 5.5, -1)) * k)
            // The tops of the shoulders, so the outline runs over them instead of dipping between the arms.
            val shoulderTops = listOf(1f to sk.left.shoulder, -1f to sk.right.shoulder)
                .map { (side, sh) -> pr.screen(sh + sk.chestAxes.apply(v(if (woman) -1.4f * side else 0f, if (woman) 2.8f else 3.4f, -0.5f)) * k) }
                .sortedBy { it.x }
            // The slope of the trapezius, from the sides of the neck down to the shoulders.
            val neckSides = listOf(1f, -1f)
                .map { pr.screen(sk.chest + sk.chestAxes.apply(v((if (woman) 5.4f else 6.6f) * it, 7.6, -1.6)) * k) }
                .sortedBy { it.x }
            val shirt = smoothClosed(buildList {
                add(hemExt.first)
                for (i in 2..5) add(ext[i].first)
                add(shoulderTops[0])
                add(neckSides[0])
                add(top)
                add(neckSides[1])
                add(shoulderTops[1])
                for (i in 5 downTo 2) add(ext[i].second)
                add(hemExt.second)
                add(pr.screen(hem - sk.lowerAxes.yAxis * (1.2f * k)))
            })

            shirtPath = shirt
            trousersPath = trousers
            // Where the hips overlap the thighs, the trousers are one piece of cloth: no seam, no shadow.
            val r = k * legW
            val thighs = union(
                capsule(pr, sk.left.hip, 9.4f * r, sk.left.knee, 6.2f * r),
                capsule(pr, sk.right.hip, 9.4f * r, sk.right.knee, 6.2f * r),
            )
            val shapes = mutableListOf(
                Shape(Mat.BOTTOM, trousers, look.bottomColor, quiet = thighs),
                Shape(Mat.TOP, shirt, look.topColor),
            )
            val marks = mutableListOf<Mark>()
            val chestFront = facing(sk.chestAxes.zAxis)
            val c = sk.chestAxes
            fun chestPt(x: Number, y: Number, z: Number) = sk.chest + c.apply(v(x, y, z)) * k

            if (look.top == Top.SHIRT && chestFront > 0.3f) {
                // An open collar shows a little skin, then a button placket runs down the front.
                shapes += Shape(Mat.SKIN_OVER, polygon(listOf(chestPt(-3.6, 2.8, 8.6), chestPt(3.6, 2.8, 8.6), chestPt(0, -6.5, 10.2)).map(pr::screen)), skin)
                val front = (2..5).map { i -> secs[i].center + secs[i].depth * (secs[i].halfDepth + 0.3f * k) }
                val pts = listOf(hem + sk.lowerAxes.zAxis * (secs[1].halfDepth * 1.04f)) + front.dropLast(1) + listOf(chestPt(0, -6.2, 10.1))
                marks += Mark(polyline(pts.map(pr::screen)), shadeOf(look.topColor, 0.28f), 0.35f * k * pr.scale)
                for (y in listOf(-12f, -20f, -28f)) {
                    marks += Mark(dot(pr.screen(chestPt(0.4, y, 10.6 + if (y < -20f) 0.6f else 0f)), 0.42f * k * pr.scale), shadeOf(look.topColor, 0.35f), 0f)
                }
            }
            if (look.top == Top.BLOUSE && chestFront > 0.3f) {
                shapes += Shape(Mat.SKIN_OVER, smoothClosed(listOf(chestPt(-3.4, 3.0, 8.4), chestPt(3.4, 3.0, 8.4), chestPt(0.6, -3.8, 9.8), chestPt(-0.6, -3.8, 9.8)).map(pr::screen)), skin)
            }
            if (look.top == Top.POLO && chestFront > 0.3f) {
                marks += Mark(polyline(listOf(chestPt(0, 2.4, 9.6), chestPt(0, -9, 10.8)).map(pr::screen)), shadeOf(look.topColor, 0.3f), 0.35f * k * pr.scale)
                marks += Mark(dot(pr.screen(chestPt(0.4, -4, 10.6)), 0.42f * k * pr.scale), shadeOf(look.topColor, 0.35f), 0f)
            }
            if (look.top == Top.JUMPER) {
                // A ribbed hem band.
                val band = extremes(secs[1].copy(center = hem + sk.lowerAxes.yAxis * (3.2f * k), halfWidth = secs[1].halfWidth * 1.05f, halfDepth = secs[1].halfDepth * 1.04f))
                marks += Mark(polyline(listOf(band.first, band.second)), shadeOf(look.topColor, 0.22f), 0.3f * k * pr.scale)
            }
            parts += Part("$f:torso", shapes, depth, far = false, marks = marks)
        }

        /** Extreme points of a projected cross-section along screen x: (left, right). */
        private fun extremes(sec: Section): Pair<Offset, Offset> {
            val a = sec.halfWidth * (sec.lateral dot pr.viewX)
            val bb = sec.halfDepth * (sec.depth dot pr.viewX)
            val t = atan2(bb, a)
            val ext = sec.lateral * (sec.halfWidth * cos(t)) + sec.depth * (sec.halfDepth * sin(t))
            val p1 = pr.screen(sec.center + ext)
            val p2 = pr.screen(sec.center - ext)
            return if (p1.x <= p2.x) p1 to p2 else p2 to p1
        }

        // ---- Head ---------------------------------------------------------------------------

        private fun head() {
            val h = sk.headAxes
            fun at(x: Number, y: Number, z: Number) = sk.head + h.apply(v(x, y, z)) * hk
            fun chestAt(x: Number, y: Number, z: Number) = sk.chest + sk.chestAxes.apply(v(x, y, z)) * k
            val nf = (if (baby) 0.85f else 1f + (b - 1f) * 0.9f) * (if (woman) 0.84f else 1f)

            // Neck, then the collar of whatever is worn, both in the neck's place in the depth order.
            val neck = mutableListOf(
                Shape(Mat.SKIN, capsule(pr, sk.neck - sk.chestAxes.yAxis * (1.5f * k), 5.6f * k * nf, sk.head - h.yAxis * (4.5f * hk), 5.0f * k * nf), skin),
            )
            if (!baby && look.hairStyle != HairStyle.HIJAB) {
                val collar = when (look.top) {
                    Top.SHIRT, Top.POLO -> ellipsoid(pr, chestAt(0, 3.7, -1.0), sk.chestAxes, 6.5f * k * nf, 1.7f * k, 5.9f * k * nf)
                    else -> ellipsoid(pr, chestAt(0, 3.1, -0.9), sk.chestAxes, 6.2f * k * nf, 1.2f * k, 5.7f * k * nf)
                }
                neck += Shape(Mat.TOP, collar, look.topColor)
            }
            parts += Part("$f:head", neck, pr.depth(sk.neck) + 0.6f * k, false)

            val toward = facing(h.zAxis)
            val jawW = if (child || baby) 5.9f else if (woman) 5.8f else 6.5f + (b - 1f) * 2f
            val skull = union(
                ellipsoid(pr, at(0, 1.4, -0.8), h, 7.7f * hk, 10.2f * hk, 9.8f * hk),
                ellipsoid(pr, at(0, -3.6, 2.4), h, jawW * hk, (if (baby) 5.6f else if (woman) 6.5f else 6.9f) * hk, 6.8f * hk),
                circle(pr, at(0, -8.4, 5.4), (if (baby) 1.6f else if (woman) 2.0f else 2.4f) * hk),
                capsule(pr, at(0, 1.2, 8.2), 1.0f * hk, at(0, -2.3, 9.8), (if (baby) 1.0f else if (woman) 1.15f else 1.35f) * hk),
            )
            val shapes = mutableListOf(Shape(Mat.SKIN_OVER, skull, skin))

            // Ears, on whichever side faces the viewer, unless the hair or a scarf covers them.
            val earsCovered = look.hairStyle in setOf(HairStyle.LONG, HairStyle.BOB, HairStyle.CURLY_LONG, HairStyle.HIJAB) ||
                sk.accessory == "headphones"
            for (s in listOf(1f, -1f)) {
                if (!earsCovered && facing(h.xAxis * s) > -0.15f) {
                    shapes += Shape(Mat.EAR, ellipsoid(pr, at(7.5f * s, 0.2, 0.2), h, 1.0f * hk, 3.0f * hk, 1.9f * hk), skin)
                }
            }

            hair(h, toward, ::at)?.let { shapes += Shape(Mat.HAIR, it.first, it.second) }
            val headMarks = mutableListOf<Mark>()
            if (sk.accessory == "headphones") {
                // Over-ear headphones: a band over the crown and a cup on each ear that faces the viewer.
                for (s in listOf(1f, -1f)) {
                    if (facing(h.xAxis * s) > -0.2f) {
                        shapes += Shape(Mat.PROP, ellipsoid(pr, at(8.3f * s, 0.3, 0.3), h, 1.8f * hk, 3.8f * hk, 3.3f * hk), Color(0xFFC3C8CF))
                    }
                }
                val band = (0..10).map { i ->
                    val th = PI * i / 10
                    at(8.4f * cos(th).toFloat(), 0.8f + 11.6f * sin(th).toFloat(), -0.4)
                }
                headMarks += Mark(polyline(band.map(pr::screen)), Color(0xFFA9AEB6), 1.3f * hk * pr.scale)
            }
            hairBehind(h, ::at)?.let { (path, color, centre) ->
                // Long hair, a bun or a scarf's drape hangs behind the head: in front of it only when seen from behind.
                parts += Part("$f:hairback", listOf(Shape(Mat.HAIR, path, color)), pr.depth(centre) - 1.5f * k, false)
            }
            beard(h, toward, ::at)?.let { shapes += Shape(Mat.BEARD, it.first, it.second) }

            parts += Part("$f:head", shapes, pr.depth(sk.head) + 1f * k, false, marks = face(h, ::at) + headMarks)
        }

        private fun hair(h: M3, toward: Float, at: (Number, Number, Number) -> V3): Pair<Path, Color>? {
            data class Cut(val cy: Float, val ry: Float, val t: Float, val bumps: Float)
            val spec = when (look.hairStyle) {
                HairStyle.CROP -> Cut(3.2f, 9.2f, 0.55f, 0f)
                HairStyle.SIDE -> Cut(2.8f, 9.8f, 1.15f, 0f)
                HairStyle.CURLY -> Cut(3.0f, 10.0f, 1.9f, 0.05f)
                HairStyle.BUZZ -> Cut(3.4f, 8.8f, 0.25f, 0f)
                HairStyle.RECEDING -> Cut(3.2f, 9.2f, 0.45f, 0f)
                HairStyle.CHILD -> Cut(2.6f, 9.8f, 1.05f, 0f)
                HairStyle.BABY -> Cut(3.8f, 8.3f, 0.2f, 0f)
                HairStyle.LONG -> Cut(2.6f, 10.0f, 1.4f, 0f)
                HairStyle.BOB -> Cut(2.6f, 10.0f, 1.6f, 0f)
                HairStyle.BUN, HairStyle.PONYTAIL -> Cut(3.0f, 9.4f, 0.7f, 0f)
                HairStyle.CURLY_LONG -> Cut(3.0f, 10.2f, 2.8f, 0.07f)
                HairStyle.PIXIE -> Cut(3.0f, 9.6f, 1.0f, 0f)
                HairStyle.HIJAB -> return hijab(h, toward, at)
                HairStyle.TURBAN -> return turban(h, at)
            }
            val longStyle = look.hairStyle in setOf(HairStyle.LONG, HairStyle.BOB, HairStyle.CURLY_LONG)
            val base = ellipsoidPath(at(0, spec.cy, -1.3), h, (7.7f + spec.t) * hk, (spec.ry + spec.t * 0.6f) * hk, (10.0f + spec.t) * hk, spec.bumps)
            var hair = base
            // Short hair stops above the ears at the sides; at the back it runs down to the nape.
            if (!longStyle && toward > -0.55f) {
                hair = difference(hair, ellipsoid(pr, at(0, -4.4, 1.6), h, 9.8f * hk, (if (look.hairStyle == HairStyle.CHILD) 5.4f else 5.8f) * hk, 8.0f * hk))
            }
            // Where the face is turned toward us, cut the face out of the hair; from behind, hair covers the head.
            if (toward > -0.35f) {
                val low = look.hairStyle == HairStyle.CHILD
                val cut = if (longStyle) ellipsoid(pr, at(0, -2.4, 4.4), h, 6.8f * hk, 9.4f * hk, 8.4f * hk)
                    else ellipsoid(pr, at(0, if (low) -3.3f else -2.6f, 4.0), h, 7.3f * hk, (if (low) 9.2f else 9.6f) * hk, 8.6f * hk)
                hair = difference(hair, cut)
            }
            if (look.hairStyle == HairStyle.RECEDING) {
                hair = difference(hair, ellipsoid(pr, at(0, 9.6, 2.6), h, 6.6f * hk, 4.4f * hk, 7.8f * hk))
            }
            val color = when (look.hairStyle) {
                HairStyle.BUZZ -> mix(skin, look.hair, 0.72f)
                HairStyle.BABY -> mix(skin, look.hair, 0.5f)
                else -> look.hair
            }
            return hair to color
        }

        /** A headscarf: covers the hair, ears and neck, and frames the face. */
        private fun hijab(h: M3, toward: Float, at: (Number, Number, Number) -> V3): Pair<Path, Color> {
            var scarf = union(
                ellipsoid(pr, at(0, 1.0, -1.0), h, 9.0f * hk, 11.8f * hk, 11.0f * hk),
                ellipsoid(pr, at(0, -10.5, 0.5), h, 10.5f * hk, 6.5f * hk, 9.5f * hk),
            )
            if (toward > -0.35f) scarf = difference(scarf, ellipsoid(pr, at(0, -1.6, 5.4), h, 5.9f * hk, 7.6f * hk, 6.4f * hk))
            return scarf to look.scarfColor
        }

        /** A Sikh turban (dastar): wrapped high over the crown and down over the tops of the ears. */
        private fun turban(h: M3, at: (Number, Number, Number) -> V3): Pair<Path, Color> {
            val wrap = union(
                ellipsoid(pr, at(0, 5.0, -1.2), h, 9.0f * hk, 8.6f * hk, 10.6f * hk),
                ellipsoid(pr, at(0, 8.6, -0.2), h, 7.6f * hk, 5.6f * hk, 9.0f * hk),
            )
            val cut = difference(wrap, ellipsoid(pr, at(0, -4.2, 2.6), h, 10.5f * hk, 7.6f * hk, 12f * hk))
            return cut to look.scarfColor
        }

        /** What hangs behind the head, with a point to depth-sort it by. */
        private fun hairBehind(h: M3, at: (Number, Number, Number) -> V3): Triple<Path, Color, V3>? = when (look.hairStyle) {
            HairStyle.LONG -> Triple(union(
                ellipsoid(pr, at(0, -3, -3.2), h, 8.9f * hk, 12f * hk, 7.8f * hk),
                capsule(pr, at(0, -8, -4.2), 8.2f * hk, at(0, -20, -3.6), 7.2f * hk),
            ), look.hair, at(0, -4, -4))
            HairStyle.BOB -> Triple(ellipsoid(pr, at(0, -2.5, -2.4), h, 9.4f * hk, 10.6f * hk, 9.0f * hk), look.hair, at(0, -3, -3))
            HairStyle.CURLY_LONG -> Triple(ellipsoidPath(at(0, -3, -2.6), h, 11.5f * hk, 12.5f * hk, 10f * hk, 0.06f), look.hair, at(0, -3, -3))
            HairStyle.BUN -> Triple(ellipsoid(pr, at(0, 4.2, -9.8), h, 3.8f * hk, 3.6f * hk, 3.8f * hk), look.hair, at(0, 4, -9))
            HairStyle.PONYTAIL -> Triple(union(
                ellipsoid(pr, at(0, 3.4, -9.6), h, 2.6f * hk, 2.6f * hk, 2.6f * hk),
                capsule(pr, at(0, 2.5, -10.4), 2.6f * hk, at(0, -10, -10.8), 1.7f * hk),
            ), look.hair, at(0, 0, -9))
            HairStyle.HIJAB -> Triple(capsule(pr, at(0, -6, -6), 8.0f * hk, at(0, -17, -6.5), 9.5f * hk), look.scarfColor, at(0, -8, -5))
            else -> null
        }

        private fun beard(h: M3, toward: Float, at: (Number, Number, Number) -> V3): Pair<Path, Color>? {
            if (look.beard == Beard.NONE || toward < -0.45f) return null
            val trim = if (look.beard == Beard.FULL) 1f else 0.9f
            var jaw = union(
                ellipsoid(pr, at(0, -5.6, 5.0), h, 6.9f * trim * hk, 5.6f * trim * hk, 6.0f * hk),
                circle(pr, at(0, -8.7, 5.8), 2.8f * trim * hk),
            )
            jaw = difference(jaw, ellipsoid(pr, at(0, -0.4, 5.2), h, 7.4f * hk, 5.2f * hk, 7.6f * hk))
            val stache = capsule(pr, at(-2.4, -3.75, 8.7), 0.85f * hk, at(2.4, -3.75, 8.7), 0.85f * hk)
            val shape = union(jaw, stache)
            val color = when (look.beard) {
                Beard.STUBBLE -> mix(skin, look.hair, 0.3f)
                Beard.TRIM -> mix(skin, look.hair, 0.82f)
                else -> look.hair
            }
            return shape to color
        }

        /** Eyes, brows, nose and mouth: each drawn only where that part of the face is turned toward us. */
        private fun face(h: M3, at: (Number, Number, Number) -> V3): List<Mark> {
            val marks = mutableListOf<Mark>()
            val px = hk * pr.scale
            val dark = Color(0xFF2A211C)
            val brow = shadeOf(if (look.hairStyle == HairStyle.BABY) skin else look.hair, 0.15f)
            val crease = shadeOf(skin, 0.32f)
            val lips = if (woman) mix(shadeOf(skin, 0.22f), Color(0xFFA84E5A), 0.45f) else mix(shadeOf(skin, 0.3f), Color(0xFF8E4A44), 0.3f)
            fun n(x: Number, y: Number, z: Number) = h.apply(v(x, y, z))

            for (s in listOf(1f, -1f)) {
                if (facing(n(0.42f * s, 0.05, 0.9)) > 0.12f) {
                    val eye = at(3.0f * s, 1.9, 7.7)
                    marks += Mark(dot(pr.screen(eye), (if (baby) 0.75f else if (woman) 0.68f else 0.62f) * px), dark, 0f)
                    if (!baby) marks += Mark(polyline(listOf(at(1.3f * s, 4.2, 8.3), at(3.2f * s, if (woman) 4.8f else 4.4f, 8.0), at(4.8f * s, 4.0, 7.0)).map(pr::screen)), brow, (if (woman) 0.5f else 0.7f) * px)
                    if (look.glasses) {
                        val ring = (0 until 18).map { i ->
                            val th = 2.0 * PI * i / 18
                            at(3.0f * s + 2.1f * cos(th).toFloat(), 1.8f + 1.7f * sin(th).toFloat(), 8.6)
                        }
                        marks += Mark(polygon(ring.map(pr::screen)), Color(0xFF2E2A28), 0.32f * px)
                        marks += Mark(polyline(listOf(at(5.1f * s, 2.1, 7.9), at(7.4f * s, 2.3, 1.0)).map(pr::screen)), Color(0xFF2E2A28), 0.32f * px)
                    }
                }
            }
            if (look.glasses && facing(n(0, 0, 1)) > 0.25f) {
                marks += Mark(polyline(listOf(at(-0.9, 2.2, 9.0), at(0.9, 2.2, 9.0)).map(pr::screen)), Color(0xFF2E2A28), 0.32f * px)
            }
            if (facing(n(0, -0.25, 1)) > 0.3f) {
                marks += Mark(polyline(listOf(at(-1.25, -2.0, 8.9), at(0, -2.55, 9.8), at(1.25, -2.0, 8.9)).map(pr::screen)), crease, 0.4f * px)
            }
            // Mouth in two halves, so a profile still shows the near corner.
            for (s in listOf(1f, -1f)) {
                if (facing(n(0.6f * s, 0, 0.8)) > 0.2f) {
                    val w = if (baby) 1.2f else if (woman) 1.9f else 2.1f
                    marks += Mark(polyline(listOf(at(w * s, -5.0, 8.3), at(w * 0.5f * s, -5.2, 8.8), at(0, -5.25, 8.95)).map(pr::screen)), lips, (if (woman) 0.65f else 0.5f) * px)
                }
            }
            return marks
        }

        // ---- Arms -----------------------------------------------------------------------------

        private fun arm(name: String, side: Side) {
            val torsoDepth = pr.depth(sk.waist) + 0.5f * k
            val farSide = pr.depth(side.shoulder) < torsoDepth - 7f * k
            val r = k * armW
            val sleeves = look.sleeves

            val upper = mutableListOf<Shape>()
            if (sleeves != Look.Sleeves.LONG) {
                upper += Shape(Mat.SKIN, capsule(pr, side.shoulder, 4.9f * r, side.elbow, 4.2f * r), skin)
            }
            val sleeveEnd = if (sleeves == Look.Sleeves.SHORT) side.shoulder.lerp(side.elbow, 0.5f) else side.elbow
            // The sleeve joins the shirt at the shoulder without a seam.
            val shoulderJoin = intersect(shirtPath, circle(pr, side.shoulder, 8f * r))
            upper += Shape(Mat.TOP, union(
                circle(pr, side.shoulder, 4.9f * r),
                capsule(pr, side.shoulder, 5.0f * r, sleeveEnd, if (sleeves == Look.Sleeves.SHORT) 5.0f * r else 4.6f * r),
            ), look.topColor, quiet = shoulderJoin)
            parts += Part("$f:arm$name", upper, (pr.depth(side.shoulder) + pr.depth(side.elbow)) / 2f + 0.8f * k, farSide)

            val lower = mutableListOf<Shape>()
            val wrist = side.hand.wrist
            val elbowJoin = circle(pr, side.elbow, 5.2f * r)
            when (sleeves) {
                Look.Sleeves.LONG -> lower += Shape(Mat.TOP, capsule(pr, side.elbow, 4.6f * r, wrist - side.hand.dir * (0.8f * k), 3.8f * r), look.topColor, elbowJoin)
                Look.Sleeves.ROLLED -> {
                    lower += Shape(Mat.SKIN, capsule(pr, side.elbow, 4.3f * r, wrist, 3.1f * r), skin, elbowJoin)
                    lower += Shape(Mat.TOP, capsule(pr, side.elbow, 4.8f * r, side.elbow.lerp(wrist, 0.28f), 4.7f * r), look.topColor, elbowJoin)
                }
                Look.Sleeves.SHORT -> lower += Shape(Mat.SKIN, capsule(pr, side.elbow, 4.3f * r, wrist, 3.1f * r), skin, elbowJoin)
            }
            val handMat = if (sleeves == Look.Sleeves.LONG) Mat.SKIN_OVER else Mat.SKIN
            lower += Shape(handMat, handPath(pr, side.hand, k * (if (baby) 1.15f else 1f)), skin)
            val tip = wrist + side.hand.dir * (8f * k)
            parts += Part("$f:arm$name", lower, max(pr.depth(side.elbow), max(pr.depth(wrist), pr.depth(tip))) + 0.8f * k, farSide)
        }

        // ---- Legs -----------------------------------------------------------------------------

        private fun leg(name: String, side: Side) {
            val torsoDepth = pr.depth(sk.waist) + 0.5f * k
            val farLeg = pr.depth(side.hip) < torsoDepth - 5f * k
            val r = k * legW
            val thigh = capsule(pr, side.hip, 9.4f * r, side.knee, (if (skirt) 7.2f else 6.2f) * r)
            // Seen end-on (sitting, facing us), a thigh runs into the lap without a seam.
            parts += Part("$f:leg$name", listOf(Shape(Mat.BOTTOM, thigh, look.bottomColor, quiet = trousersPath)),
                (pr.depth(side.hip) + pr.depth(side.knee)) / 2f - 3f * k, farLeg)

            val ft = side.foot
            val shin = if (skirt) capsule(pr, side.knee, 5.0f * r, side.ankle, 3.5f * k) else capsule(pr, side.knee, 6.0f * r, side.ankle, 4.7f * k)
            val sf = if (woman) 0.86f else 1f
            val shoe = union(
                capsule(pr, ft.heel, 4.0f * k * sf, ft.toe, 3.2f * k * sf),
                capsule(pr, ft.ballInner, 3.3f * k * sf, ft.ballOuter, 3.3f * k * sf),
                capsule(pr, side.ankle - V3.Y * (1.5f * k), 4.0f * k * sf, ft.heel, 3.9f * k * sf),
            )
            val marks = mutableListOf<Mark>()
            if (look.top != Top.ONESIE) {
                val down = V3.Y * (-2.6f * k)
                val sole = listOf(ft.heel + down, ft.ballInner.lerp(ft.ballOuter, 0.5f) + V3.Y * (-2.2f * k), ft.toe + V3.Y * (-1.6f * k))
                marks += Mark(polyline(sole.map(pr::screen)), look.soleColor ?: shadeOf(look.shoeColor, 0.45f), 1.1f * k * pr.scale)
            }
            val kneeJoin = circle(pr, side.knee, 7f * r)
            val lower = if (skirt) listOf(
                // Bare or tighted legs below a skirt, with the hem falling over the knee.
                Shape(Mat.SKIN, shin, look.legColor ?: skin),
                Shape(Mat.BOTTOM, capsule(pr, side.knee, 7.4f * r, side.knee.lerp(side.hip, 0.16f), 7.6f * r), look.bottomColor),
                Shape(Mat.SHOE, shoe, look.shoeColor),
            ) else listOf(Shape(Mat.BOTTOM, shin, look.bottomColor, kneeJoin), Shape(Mat.SHOE, shoe, look.shoeColor))
            parts += Part("$f:leg$name", lower,
                (pr.depth(side.knee) + pr.depth(side.ankle)) / 2f - 3f * k, farLeg, marks = marks)
        }

        // ---- Helpers --------------------------------------------------------------------------

        private fun ellipsoidPath(c: V3, axes: M3, rx: Float, ry: Float, rz: Float, bumps: Float): Path {
            if (bumps <= 0f) return ellipsoid(pr, c, axes, rx, ry, rz)
            val center = pr.screen(c)
            val pts = ellipsoidPoints(pr, c, axes, rx, ry, rz).mapIndexed { i, p ->
                val th = 2.0 * PI * i / 48
                val f = 1f + bumps * abs(sin(th * 9).toFloat())
                Offset(center.x + (p.x - center.x) * f, center.y + (p.y - center.y) * f)
            }
            return smoothClosed(pts)
        }
    }

    private fun handPath(pr: Projector, hand: Hand, k: Float): Path {
        val w = hand.wrist
        val d = hand.dir * k
        val t = hand.thumb * k
        fun at(along: Float, side: Float = 0f) = w + d * along + t * side
        fun circle(pr: Projector, c: V3, r: Float) = this.circle(pr, c, r * k)
        fun capsule(pr: Projector, a: V3, ra: Float, b: V3, rb: Float) = this.capsule(pr, a, ra * k, b, rb * k)
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
        fun box(x0: Float, x1: Float, y: Float, z0: Float, z1: Float) {
            lines += v(x0, y, z0) to v(x1, y, z0); lines += v(x0, y, z1) to v(x1, y, z1)
            lines += v(x0, y, z0) to v(x0, y, z1); lines += v(x1, y, z0) to v(x1, y, z1)
        }
        fun chair() {
            val y = 44f; val x0 = -21f; val x1 = 21f; val zb = -22f; val zf = 18f; val back = 92f
            box(x0, x1, y, zb, zf)
            for (x in listOf(x0, x1)) for (z in listOf(zb, zf)) lines += v(x, 0, z) to v(x, y, z)
            for (x in listOf(x0, x1)) lines += v(x, y, zb) to v(x, back, zb - 4)
            lines += v(x0, back, zb - 4) to v(x1, back, zb - 4)
            lines += v(x0, 74, zb - 3) to v(x1, 74, zb - 3)
        }
        fun table() {
            val y = 74f
            box(-45f, 45f, y, 14f, 80f)
            for (x in listOf(-40f, 40f)) lines += v(x, 0, 20) to v(x, y, 20)
        }
        when (sk.prop) {
            "chair" -> chair()
            "stool" -> {
                val y = 66f
                box(-17f, 17f, y, -17f, 17f)
                for (x in listOf(-15f, 15f)) for (z in listOf(-15f, 15f)) lines += v(x, 0, z) to v(x, y, z)
            }
            "table" -> table()
            "desk" -> { chair(); table() }
            // The head of a meeting table: the figure's chair, and a long table running away in front of it.
            "long_table" -> {
                chair()
                val y = 74f
                box(-45f, 45f, y, 14f, 176f)
                for (x in listOf(-40f, 40f)) for (z in listOf(20f, 170f)) lines += v(x, 0, z) to v(x, y, z)
            }
            "laptop" -> {
                chair(); table()
                box(-16f, 16f, 75f, 24f, 46f)
                lines += v(-16, 75, 46) to v(-16, 97, 50); lines += v(16, 75, 46) to v(16, 97, 50)
                lines += v(-16, 97, 50) to v(16, 97, 50)
            }
            "lectern" -> lines += v(-26, 0, 38) to v(26, 0, 38)
            // A bed for a figure lying on its back (pitch -90): the body runs from the feet at the origin
            // toward the figure's own rear, so the bed does too. Mattress top 52 cm, with a pillow line.
            "bed" -> {
                val y = 52f
                box(-46f, 46f, y, -186f, 14f)
                box(-46f, 46f, y - 14f, -186f, 14f)
                for (x in listOf(-46f, 46f)) for (z in listOf(-186f, 14f)) lines += v(x, 0, z) to v(x, y - 14f, z)
                lines += v(-30, y + 7, -150) to v(30, y + 7, -150)
                lines += v(-46, y + 30, -186) to v(46, y + 30, -186)
                for (x in listOf(-46f, 46f)) lines += v(x, y, -186) to v(x, y + 30, -186)
            }
            "bench" -> {
                val y = 44f
                box(-70f, 70f, y, -18f, 18f)
                for (x in listOf(-64f, 64f)) for (z in listOf(-14f, 14f)) lines += v(x, 0, z) to v(x, y, z)
            }
            // The frame of a manual wheelchair; the wheels are solid parts (see propPanels).
            "wheelchair" -> {
                val y = 48f
                box(-21f, 21f, y, -18f, 20f)
                for (x in listOf(-21f, 21f)) {
                    lines += v(x, y, -18) to v(x, 92, -24)            // back post
                    lines += v(x, 92, -24) to v(x, 94, -33)           // push handle
                    lines += v(x, y, 20) to v(x * 0.75f, 12, 32)      // front frame down to the footrest
                    lines += v(x * 0.85f, 7, 28) to v(x * 0.85f, 0, 28) // caster fork
                }
                lines += v(-21, 88, -23) to v(21, 88, -23)
                lines += v(-21, 70, -21) to v(21, 70, -21)
                lines += v(-16, 12, 32) to v(16, 12, 36)              // footplate
            }
        }
        val pl = sk.placement
        return lines.map { (a, b) -> pr.screen(pl.floor(a)) to pr.screen(pl.floor(b)) }
    }

    /**
     * Solid props: boards that hide the body behind them. They are depth-sorted with the limbs,
     * so a lectern covers the legs while hands resting on its top stay visible.
     */
    private fun propPanels(f: Int, sk: Skeleton, pr: Projector): List<Part> {
        val pl = sk.placement
        fun quad(vararg pts: V3, bias: Float, id: String): Part {
            val world = pts.map(pl::floor)
            val path = polygon(world.map(pr::screen))
            return Part("$f:$id", listOf(Shape(Mat.TOP, path, Color.Gray)), world.map(pr::depth).average().toFloat() + bias, far = false, panel = true)
        }
        fun wheel(x: Float, cy: Float, cz: Float, r: Float, id: String): Part {
            // A tyre (ring) in the figure's side plane, with spokes drawn over the hub.
            fun ring(rad: Float) = (0 until 36).map { i ->
                val th = 2.0 * PI * i / 36
                pl.floor(v(x, cy + rad * sin(th).toFloat(), cz + rad * cos(th).toFloat()))
            }
            val outer = polygon(ring(r).map(pr::screen))
            val inner = polygon(ring(r * 0.86f).map(pr::screen))
            val tyre = Path().apply { op(outer, inner, PathOperation.Difference) }
            val hub = pl.floor(v(x, cy, cz))
            val spokes = (0 until 6).map { i ->
                val th = PI * i / 6
                val a = pl.floor(v(x, cy + r * 0.86f * sin(th).toFloat(), cz + r * 0.86f * cos(th).toFloat()))
                val b = pl.floor(v(x, cy - r * 0.86f * sin(th).toFloat(), cz - r * 0.86f * cos(th).toFloat()))
                Mark(polyline(listOf(pr.screen(a), pr.screen(b))), Color(0xFF8B8E94), 0.35f * pl.scale * pr.scale)
            } + Mark(dot(pr.screen(hub), 2.2f * pl.scale * pr.scale), Color(0xFF55585E), 0f)
            return Part("$f:$id", listOf(Shape(Mat.PROP, tyre, Color(0xFF34363B))), pr.depth(hub), far = false, marks = spokes)
        }
        fun stick(top: V3, bottom: V3, r: Float, color: Color, id: String, tip: Color? = null): Part {
            val shapes = mutableListOf(Shape(Mat.PROP, capsule(pr, top, r, bottom, r * 0.85f), color))
            if (tip != null) shapes += Shape(Mat.SHOE, capsule(pr, top.lerp(bottom, 0.86f), r * 1.02f, bottom, r * 0.9f), tip)
            return Part("$f:$id", shapes, maxOf(pr.depth(top), pr.depth(bottom)) + 1f, far = false)
        }
        val hand = sk.right.hand
        val grip = hand.wrist + hand.dir * (4f * pl.scale)
        return when (sk.prop) {
            "wheelchair" -> listOf(
                wheel(27f, 30f, -4f, 30f, "wheelL"), wheel(-27f, 30f, -4f, 30f, "wheelR"),
                wheel(18f, 7f, 28f, 7f, "casterL"), wheel(-18f, 7f, 28f, 7f, "casterR"),
            )
            // A walking cane in the right hand, planted beside the foot, with a crook over the hand.
            "cane" -> {
                val foot = V3(grip.x, 0f, grip.z + 4f * pl.scale)
                val crook = grip + sk.chestAxes.zAxis * (5f * pl.scale) + V3.Y * (1.5f * pl.scale)
                listOf(stick(grip, foot, 1.25f * pl.scale, Color(0xFF5B4030), "cane"),
                    stick(grip, crook, 1.25f * pl.scale, Color(0xFF5B4030), "caneCrook"))
            }
            // A long white cane swept ahead along the floor, red near the tip.
            "long_cane" -> {
                val ahead = sk.chestAxes.zAxis.let { V3(it.x, 0f, it.z).normalized() }
                val tipPt = V3(grip.x, 1.5f * pl.scale, grip.z) + ahead * (78f * pl.scale)
                listOf(stick(grip, tipPt, 0.9f * pl.scale, Color(0xFFF2F1EC), "longCane", tip = Color(0xFFB8393A)))
            }
            "lectern" -> listOf(
                quad(v(-20, 2, 36), v(20, 2, 36), v(20, 103, 27), v(-20, 103, 27), bias = 0f, id = "lecternFront"),
                quad(v(-28, 104, 22), v(28, 104, 22), v(28, 112, 44), v(-28, 112, 44), bias = -15f, id = "lecternTop"),
            )
            else -> emptyList()
        }
    }

    // ---- Primitives ----------------------------------------------------------------------

    private fun circle(pr: Projector, c: V3, r: Float): Path {
        val o = pr.screen(c)
        val rr = r * pr.scale
        return Path().apply { addOval(androidx.compose.ui.geometry.Rect(o.x - rr, o.y - rr, o.x + rr, o.y + rr)) }
    }

    /** An ellipsoid with semi-axes along [axes], projected exactly onto the screen. */
    private fun ellipsoid(pr: Projector, c: V3, axes: M3, rx: Float, ry: Float, rz: Float): Path =
        polygon(ellipsoidPoints(pr, c, axes, rx, ry, rz))

    private fun ellipsoidPoints(pr: Projector, c: V3, axes: M3, rx: Float, ry: Float, rz: Float): List<Offset> {
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
        return pts
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

    private fun difference(a: Path, b: Path): Path = Path().apply { op(a, b, PathOperation.Difference) }

    private fun intersect(a: Path, b: Path): Path = Path().apply { op(a, b, PathOperation.Intersect) }

    /** An open, gently smoothed line through [pts]. */
    private fun polyline(pts: List<Offset>): Path = Path().apply {
        if (pts.isEmpty()) return@apply
        moveTo(pts[0].x, pts[0].y)
        if (pts.size == 2) { lineTo(pts[1].x, pts[1].y); return@apply }
        for (i in 1 until pts.size - 1) {
            val m = Offset((pts[i].x + pts[i + 1].x) / 2f, (pts[i].y + pts[i + 1].y) / 2f)
            if (i == pts.size - 2) quadraticTo(pts[i].x, pts[i].y, pts[i + 1].x, pts[i + 1].y)
            else quadraticTo(pts[i].x, pts[i].y, m.x, m.y)
        }
    }

    private fun dot(c: Offset, r: Float): Path = Path().apply {
        addOval(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r))
    }

    /** [c] darkened by [amount] (0 none, 1 black). */
    fun shadeOf(c: Color, amount: Float): Color = Color(c.red * (1f - amount), c.green * (1f - amount), c.blue * (1f - amount), c.alpha)

    private fun mix(a: Color, b: Color, t: Float): Color =
        Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

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
