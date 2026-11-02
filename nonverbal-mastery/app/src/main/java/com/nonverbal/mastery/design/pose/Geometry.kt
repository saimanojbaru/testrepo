package com.nonverbal.mastery.design.pose

import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * World space for the figure, in centimetres.
 * Origin on the floor between the feet. +x is the figure's own LEFT, +y is up,
 * +z is the direction the figure faces. Left and right everywhere in the pose system
 * are the figure's own, as in an anatomy plate.
 */
data class V3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: V3) = V3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: V3) = V3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = V3(x * s, y * s, z * s)
    operator fun unaryMinus() = V3(-x, -y, -z)
    infix fun dot(o: V3) = x * o.x + y * o.y + z * o.z
    infix fun cross(o: V3) = V3(y * o.z - z * o.y, z * o.x - x * o.z, x * o.y - y * o.x)
    fun length() = sqrt(this dot this)
    fun normalized(): V3 {
        val l = length()
        return if (l < 1e-5f) ZERO else this * (1f / l)
    }
    fun mirrorX() = V3(-x, y, z)
    fun lerp(o: V3, t: Float) = V3(x + (o.x - x) * t, y + (o.y - y) * t, z + (o.z - z) * t)

    companion object {
        val ZERO = V3(0f, 0f, 0f)
        val X = V3(1f, 0f, 0f)
        val Y = V3(0f, 1f, 0f)
        val Z = V3(0f, 0f, 1f)
    }
}

fun v(x: Number, y: Number, z: Number) = V3(x.toFloat(), y.toFloat(), z.toFloat())

/** A 3x3 rotation, row-major. Columns are the frame's x (left), y (up) and z (forward) axes. */
class M3(private val m: FloatArray) {
    fun apply(p: V3) = V3(
        m[0] * p.x + m[1] * p.y + m[2] * p.z,
        m[3] * p.x + m[4] * p.y + m[5] * p.z,
        m[6] * p.x + m[7] * p.y + m[8] * p.z,
    )

    operator fun times(o: M3): M3 {
        val r = FloatArray(9)
        for (i in 0..2) for (j in 0..2) {
            var s = 0f
            for (k in 0..2) s += m[i * 3 + k] * o.m[k * 3 + j]
            r[i * 3 + j] = s
        }
        return M3(r)
    }

    val xAxis get() = V3(m[0], m[3], m[6])
    val yAxis get() = V3(m[1], m[4], m[7])
    val zAxis get() = V3(m[2], m[5], m[8])

    /** Element-wise blend, re-orthonormalised. Good enough for morphing nearby poses. */
    fun lerp(o: M3, t: Float): M3 {
        val r = FloatArray(9) { m[it] + (o.m[it] - m[it]) * t }
        val y = V3(r[1], r[4], r[7]).normalized()
        val z0 = V3(r[2], r[5], r[8])
        val x = (y cross z0).normalized()
        val z = (x cross y).normalized()
        return M3(floatArrayOf(x.x, y.x, z.x, x.y, y.y, z.y, x.z, y.z, z.z))
    }

    companion object {
        val IDENTITY = M3(floatArrayOf(1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f))

        private fun rad(deg: Float) = Math.toRadians(deg.toDouble()).toFloat()

        /** Pitch. Positive tips +y toward +z: a forward lean, or chin down for the head. */
        fun rotX(deg: Float): M3 {
            val c = cos(rad(deg)); val s = sin(rad(deg))
            return M3(floatArrayOf(1f, 0f, 0f, 0f, c, -s, 0f, s, c))
        }

        /** Yaw. Positive turns +z toward +x: the figure turns to its own left. */
        fun rotY(deg: Float): M3 {
            val c = cos(rad(deg)); val s = sin(rad(deg))
            return M3(floatArrayOf(c, 0f, s, 0f, 1f, 0f, -s, 0f, c))
        }

        /** Roll. Positive lifts +x: the figure's left side rises. */
        fun rotZ(deg: Float): M3 {
            val c = cos(rad(deg)); val s = sin(rad(deg))
            return M3(floatArrayOf(c, -s, 0f, s, c, 0f, 0f, 0f, 1f))
        }
    }
}

/**
 * Analytic two-bone IK. Returns (middle joint, end joint). The end is clamped to the
 * reachable shell; the middle joint bends toward [hint].
 */
fun solveTwoBone(root: V3, target: V3, l1: Float, l2: Float, hint: V3): Pair<V3, V3> {
    val toTarget = target - root
    val d = toTarget.length()
    val dir = if (d < 1e-4f) V3(0f, -1f, 0f) else toTarget * (1f / d)
    val reach = d.coerceIn(kotlin.math.abs(l1 - l2) + 0.5f, (l1 + l2) * 0.9995f)
    val end = root + dir * reach
    val a = (l1 * l1 - l2 * l2 + reach * reach) / (2f * reach)
    val h = sqrt((l1 * l1 - a * a).coerceAtLeast(0f))
    var perp = hint - dir * (hint dot dir)
    if (perp.length() < 1e-3f) {
        perp = (dir cross V3.X).let { if (it.length() < 1e-3f) dir cross V3.Z else it }
    }
    val mid = root + dir * a + perp.normalized() * h
    return mid to end
}
