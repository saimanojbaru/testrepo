package com.sortora.app.ui.sorting

import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Physics for the sorting animation, kept free of Compose so it can be unit-tested.
 *
 * Each thumbnail is a small mass on a spring anchored at its destination. At launch it gets an
 * upward, slightly sideways kick, so it rises, arcs over and is reeled in, overshooting a touch
 * before it drops into the pile. Heavier items (random per file) launch lower and settle later,
 * so a batch never moves in lockstep. Tilt follows horizontal velocity, like a card through air.
 *
 * Units are pixels and seconds; [unit] is the screen density, so the tuning holds on any screen.
 */
class SortSimulation(
    private val unit: Float,
    private val maxInFlight: Int = 7,
    /** Seconds between launches when the queue is short; it tightens as the queue grows. */
    private val launchInterval: Float = 0.12f,
    /** Beyond this many waiting, the oldest skip the flight and land instantly. */
    private val maxQueue: Int = 28,
) {
    class Flight internal constructor(
        val id: Long,
        val targetKey: String,
        val payload: Any?,
        start: Vec,
        internal val weight: Float,
        internal val spin: Float,
        internal val startScale: Float,
    ) {
        var position: Vec = start
            internal set
        var velocity: Vec = Vec.Zero
            internal set
        var rotation: Float = 0f
            internal set
        var scale: Float = startScale
            internal set
        var alpha: Float = 1f
            internal set
        /** Recent positions, newest last, for the faint trail. */
        val trail: ArrayDeque<Vec> = ArrayDeque()
        var landed: Boolean = false
            internal set
        internal var age = 0f
        internal var trailClock = 0f
        internal var startDistance = 1f
        internal var target: Vec = start
        internal var fade = 0f

        /** 0 at launch, 1 at the destination. */
        val progress: Float get() = (1f - (target - position).length / startDistance).coerceIn(0f, 1f)
        internal val finished: Boolean get() = landed && fade >= 1f
    }

    private class Request(val id: Long, val targetKey: String, val start: Vec, val startScale: Float, val payload: Any?)

    data class Landing(val id: Long, val targetKey: String, val at: Vec, val flew: Boolean)

    private val queue = ArrayDeque<Request>()
    private val _flights = ArrayList<Flight>()
    val flights: List<Flight> get() = _flights
    private var sinceLaunch = Float.MAX_VALUE

    /** True while anything is queued or moving. */
    val busy: Boolean get() = queue.isNotEmpty() || _flights.isNotEmpty()
    val queued: Int get() = queue.size

    fun request(id: Long, targetKey: String, start: Vec, startScale: Float = 1f, payload: Any? = null) {
        if (_flights.any { it.id == id } || queue.any { it.id == id }) return
        queue.addLast(Request(id, targetKey, start, startScale, payload))
    }

    /**
     * Advances by [dt] seconds. [targetOf] gives each destination's current centre, or null while
     * it isn't laid out yet (those flights wait). Returns what landed during this step.
     */
    fun advance(dt: Float, targetOf: (String) -> Vec?): List<Landing> {
        val landings = ArrayList<Landing>()
        val step = dt.coerceIn(0f, MAX_FRAME)

        // Backpressure: never let the animation fall far behind the real work.
        while (queue.size > maxQueue) {
            val r = queue.removeFirst()
            landings += Landing(r.id, r.targetKey, targetOf(r.targetKey) ?: r.start, flew = false)
        }

        sinceLaunch += step
        val interval = launchInterval / (1f + queue.size / 6f)
        if (sinceLaunch >= interval && _flights.count { !it.landed } < maxInFlight) {
            val index = queue.indexOfFirst { targetOf(it.targetKey) != null }
            if (index >= 0) {
                val r = queue.removeAt(index)
                launch(r, targetOf(r.targetKey)!!)
                sinceLaunch = 0f
            }
        }

        var remaining = step
        while (remaining > 0f) {
            val h = min(SUBSTEP, remaining)
            for (f in _flights) {
                if (f.landed) continue
                targetOf(f.targetKey)?.let { f.target = it }
                integrate(f, h)
                if (shouldLand(f)) {
                    f.landed = true
                    f.position = f.target
                    landings += Landing(f.id, f.targetKey, f.target, flew = true)
                }
            }
            remaining -= h
        }
        for (f in _flights) {
            if (f.landed) {
                f.fade = (f.fade + step / FADE_TIME).coerceAtMost(1f)
                f.alpha = 1f - f.fade
                f.scale = f.scale * (1f - 0.5f * f.fade).coerceAtLeast(0.2f)
                if (f.trail.isNotEmpty()) f.trail.removeFirst()
            }
        }
        _flights.removeAll { it.finished }
        return landings
    }

    /** Lands everything immediately (e.g. when the screen leaves or motion is reduced). */
    fun flush(targetOf: (String) -> Vec?): List<Landing> {
        val out = ArrayList<Landing>()
        queue.forEach { out += Landing(it.id, it.targetKey, targetOf(it.targetKey) ?: it.start, flew = false) }
        queue.clear()
        _flights.filter { !it.landed }.forEach { out += Landing(it.id, it.targetKey, it.target, flew = false) }
        _flights.clear()
        return out
    }

    private fun launch(r: Request, target: Vec) {
        val random = Random(r.id * 7919 + 17)
        val weight = 0.85f + random.nextFloat() * 0.4f
        val spin = if (random.nextBoolean()) 1f else -1f
        val f = Flight(r.id, r.targetKey, r.payload, r.start, weight, spin * (0.5f + random.nextFloat() * 0.5f), r.startScale)
        f.target = target
        val delta = target - r.start
        f.startDistance = max(delta.length, 1f)
        // The kick: upward (screen y grows downward), a little sideways, a little toward the target.
        // Scaled with distance so long throws still rise before the spring takes over.
        val up = (LAUNCH_UP * unit + LAUNCH_UP_PER_DISTANCE * f.startDistance) / weight
        val side = (delta.x / unit) * LAUNCH_TOWARD + f.spin * LAUNCH_SIDE
        f.velocity = Vec(side * unit, -up)
        f.rotation = f.spin * 4f
        f.trail.addLast(r.start)
        _flights += f
    }

    private fun integrate(f: Flight, h: Float) {
        f.age += h
        // The spring stiffens over the first moments, so the kick shapes the start of the arc
        // and the pull shapes the end.
        val ramp = (f.age / RAMP_TIME).coerceIn(0f, 1f)
        val k = STIFFNESS * (0.25f + 0.75f * ramp * ramp)
        val c = 2f * DAMPING_RATIO * sqrt(k * f.weight)
        val toTarget = f.target - f.position
        val accel = (toTarget * k - f.velocity * c) / f.weight
        f.velocity = f.velocity + accel * h // semi-implicit Euler: velocity first
        f.position = f.position + f.velocity * h

        val tilt = (f.velocity.x / unit * TILT_PER_VELOCITY).coerceIn(-MAX_TILT, MAX_TILT) + f.spin * 5f * (1f - f.progress)
        f.rotation += (tilt - f.rotation) * (1f - exp(-h * 10f))

        val lift = if (f.age < LIFT_TIME) sinPulse(f.age / LIFT_TIME) * 0.09f else 0f
        f.scale = f.startScale + (1f - f.startScale) * min(1f, f.age / 0.2f) + lift - END_SHRINK * f.progress.pow(1.6f)

        f.trailClock += h
        if (f.trailClock >= TRAIL_EVERY) {
            f.trailClock = 0f
            f.trail.addLast(f.position)
            while (f.trail.size > TRAIL_LENGTH) f.trail.removeFirst()
        }
    }

    private fun shouldLand(f: Flight): Boolean {
        val distance = (f.target - f.position).length
        val speed = f.velocity.length
        return (distance < LAND_DISTANCE * unit && speed < LAND_SPEED * unit && f.age > 0.25f) || f.age > MAX_FLIGHT
    }

    private fun sinPulse(x: Float): Float = kotlin.math.sin(x * Math.PI).toFloat().coerceAtLeast(0f)

    companion object {
        const val SUBSTEP = 1f / 240f
        const val MAX_FRAME = 1f / 20f
        const val STIFFNESS = 50f
        const val DAMPING_RATIO = 0.74f
        const val RAMP_TIME = 0.32f
        /** dp per second. */
        const val LAUNCH_UP = 120f
        /** Extra upward speed per pixel of distance, per second. */
        const val LAUNCH_UP_PER_DISTANCE = 2f
        const val LAUNCH_TOWARD = 0.55f
        const val LAUNCH_SIDE = 46f
        const val TILT_PER_VELOCITY = 0.05f
        const val MAX_TILT = 18f
        const val LIFT_TIME = 0.24f
        const val END_SHRINK = 0.48f
        const val LAND_DISTANCE = 7f
        const val LAND_SPEED = 160f
        const val MAX_FLIGHT = 2.4f
        const val FADE_TIME = 0.16f
        const val TRAIL_EVERY = 1f / 60f
        const val TRAIL_LENGTH = 8
    }
}

/** Tiny immutable 2D vector. */
data class Vec(val x: Float, val y: Float) {
    operator fun plus(o: Vec) = Vec(x + o.x, y + o.y)
    operator fun minus(o: Vec) = Vec(x - o.x, y - o.y)
    operator fun times(s: Float) = Vec(x * s, y * s)
    operator fun div(s: Float) = Vec(x / s, y / s)
    val length: Float get() = hypot(x, y)
    val isFinite: Boolean get() = x.isFinite() && y.isFinite()

    companion object {
        val Zero = Vec(0f, 0f)
    }
}

/** Keeps each waiting item in the same orbit slot until it leaves, so nothing jumps around. */
class SlotAssigner(private val slots: Int) {
    private val assigned = arrayOfNulls<Long>(slots)
    /** Where recently departed items were, so their flight can start from the right place. */
    private val departed = LinkedHashMap<Long, Int>()

    /** Returns slot → item id (null for an empty slot). Stable for the same input. */
    fun update(waiting: List<Long>): List<Long?> {
        val waitingSet = waiting.toHashSet()
        for (i in assigned.indices) {
            val id = assigned[i]
            if (id != null && id !in waitingSet) {
                departed[id] = i
                assigned[i] = null
            }
        }
        while (departed.size > 64) departed.remove(departed.keys.first())
        val placed = assigned.filterNotNull().toHashSet()
        val incoming = waiting.asSequence().filter { it !in placed }.iterator()
        // Fill empty slots spread around the orbit rather than bunched together.
        for (i in spreadOrder(slots)) {
            if (assigned[i] == null && incoming.hasNext()) assigned[i] = incoming.next()
        }
        return assigned.toList()
    }

    /** The slot [id] occupies, or occupied until it left; -1 if it never had one. */
    fun slotOf(id: Long): Int = assigned.indexOf(id).takeIf { it >= 0 } ?: departed[id] ?: -1

    companion object {
        /** 0, n/2, n/4, 3n/4, … so a half-full orbit is still balanced. */
        fun spreadOrder(n: Int): List<Int> {
            val order = ArrayList<Int>(n)
            var step = n
            while (order.size < n && step > 0) {
                var i = 0
                while (i < n) {
                    if (i !in order) order += i
                    i += max(step, 1)
                }
                step /= 2
            }
            for (i in 0 until n) if (i !in order) order += i
            return order
        }
    }
}
