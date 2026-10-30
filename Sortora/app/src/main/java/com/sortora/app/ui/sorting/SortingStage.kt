@file:OptIn(ExperimentalLayoutApi::class)

package com.sortora.app.ui.sorting

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.sortora.app.domain.model.MediaKind
import com.sortora.app.ui.design.AnimatedCount
import com.sortora.app.ui.design.Depth
import com.sortora.app.ui.design.FolderGlyph
import com.sortora.app.ui.design.Motion
import com.sortora.app.ui.design.Radius
import com.sortora.app.ui.design.Sortora
import com.sortora.app.ui.design.Space
import com.sortora.app.ui.design.hairline
import com.sortora.app.ui.design.rememberLoopClock
import com.sortora.app.ui.design.softShadow
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

data class StageItem(val id: Long, val uri: String, val kind: MediaKind)

data class StageTarget(val key: String, val label: String, val icon: ImageVector? = null)

private const val SLOTS = 10
private const val ORBIT_SPEED = 0.16f
private val THUMB = 56.dp
private val ORBIT_HEIGHT = 264.dp

/**
 * Sortora's signature moment.
 *
 * Files still waiting drift on a slow elliptical orbit around [center] (usually the progress
 * ring), the near side larger and brighter than the far side. As each one finishes it is thrown,
 * with weight and spin, into the pile for its category or folder: it lifts, arcs, tilts with its
 * motion, shrinks as it falls in, and the pile's folder tips open, bounces and counts up. Launches
 * are staggered and capped so a fast batch reads as a flock, not a stream.
 *
 * @param waiting files not processed yet, in processing order.
 * @param done every processed file → the key of the pile it belongs to.
 * @param lookup details of a processed file, for its thumbnail in flight.
 */
@Composable
fun SortingStage(
    waiting: List<StageItem>,
    done: Map<Long, String>,
    lookup: (Long) -> StageItem?,
    targets: List<StageTarget>,
    modifier: Modifier = Modifier,
    remainingLabel: (Int) -> String? = { null },
    center: @Composable BoxScope.() -> Unit,
    caption: @Composable ColumnScope.() -> Unit = {},
) {
    val c = Sortora.colors
    val density = LocalDensity.current
    val reduceMotion = Sortora.reduceMotion
    val haptics = LocalHapticFeedback.current
    val clock = rememberLoopClock()

    val sim = remember { SortSimulation(density.density) }
    val slots = remember { SlotAssigner(SLOTS) }
    val landed = remember { mutableStateMapOf<String, Int>() }
    val arrivals = remember { mutableStateMapOf<String, Int>() }
    val flights = remember { mutableStateListOf<SortSimulation.Flight>() }
    val bursts = remember { mutableStateListOf<Burst>() }
    val known = remember { HashSet<Long>() }
    val pileCoords = remember { HashMap<String, LayoutCoordinates>() }
    val geometry = remember { OrbitGeometry() }
    var root by remember { mutableRefOf<LayoutCoordinates?>(null) }
    var frame by remember { mutableLongStateOf(0L) }
    var wake by remember { mutableIntStateOf(0) }

    // Stable pile order: first seen, first shown.
    val order = remember { LinkedHashMap<String, StageTarget>() }
    targets.forEach { order.getOrPut(it.key) { it } }
    done.values.forEach { k -> if (k !in order) order[k] = StageTarget(k, k) }

    val slotItems = slots.update(waiting.map { it.id })
    val waitingById = remember(waiting) { waiting.associateBy { it.id } }

    fun centerOf(key: String): Vec? {
        val r = root ?: return null
        val p = pileCoords[key] ?: return null
        if (!r.isAttached || !p.isAttached) return null
        val o = r.localPositionOf(p, Offset(p.size.width / 2f, p.size.height * 0.36f))
        return Vec(o.x, o.y)
    }

    val currentDone by rememberUpdatedState(done)
    val currentLookup by rememberUpdatedState(lookup)
    LaunchedEffect(Unit) {
        var first = true
        snapshotFlow { currentDone }.collect { map ->
            var launched = false
            for ((id, key) in map) {
                if (!known.add(id)) continue
                if (first || reduceMotion) {
                    landed[key] = (landed[key] ?: 0) + 1
                    if (!first) arrivals[key] = (arrivals[key] ?: 0) + 1
                    continue
                }
                val slot = slots.slotOf(id)
                val (start, scale) = if (slot >= 0) geometry.slotPosition(slot, clock.value) to geometry.slotScale(slot, clock.value)
                else geometry.center to 0.55f
                sim.request(id, key, start, scale, currentLookup(id))
                launched = true
            }
            first = false
            if (launched) wake++
        }
    }

    LaunchedEffect(wake) {
        var last = 0L
        var elapsed = 0f
        var lastTick = -1f
        while (sim.busy || bursts.isNotEmpty()) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 1f / 60f else (now - last) / 1_000_000_000f
                last = now
                elapsed += dt
                for (l in sim.advance(dt, ::centerOf)) {
                    landed[l.targetKey] = (landed[l.targetKey] ?: 0) + 1
                    arrivals[l.targetKey] = (arrivals[l.targetKey] ?: 0) + 1
                    if (l.flew) {
                        bursts += Burst(l.at, elapsed, (l.id % 7).toInt())
                        if (elapsed - lastTick > 0.07f) {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                            lastTick = elapsed
                        }
                    }
                }
                bursts.removeAll { elapsed - it.born > Burst.LIFE }
                bursts.forEach { it.now = elapsed }
                if (flights.size != sim.flights.size || flights.indices.any { flights[it] !== sim.flights[it] }) {
                    flights.clear()
                    flights.addAll(sim.flights)
                }
                frame = now
            }
        }
        flights.clear()
    }

    Box(modifier.onGloballyPositioned { root = it }) {
        Column(Modifier.fillMaxWidth()) {
            // Orbit of waiting files around the centre piece.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(ORBIT_HEIGHT)
                    .onGloballyPositioned { coords ->
                        val r = root ?: return@onGloballyPositioned
                        val o = r.localPositionOf(coords, Offset.Zero)
                        with(density) {
                            geometry.update(
                                center = Vec(o.x + coords.size.width / 2f, o.y + coords.size.height / 2f),
                                rx = min(coords.size.width * 0.4f, 172.dp.toPx()),
                                ry = 82.dp.toPx(),
                                bob = 3.dp.toPx(),
                            )
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.layout { m, cs -> val p = m.measure(cs); layout(p.width, p.height) { p.place(0, 0, zIndex = 1f) } }, content = center)
                slotItems.forEachIndexed { slot, id ->
                    val item = id?.let(waitingById::get) ?: return@forEachIndexed
                    key(item.id) {
                        OrbitingThumb(item, slot, geometry, clock, reduceMotion)
                    }
                }
                val hidden = waiting.size - slotItems.count { it != null }
                remainingLabel(hidden)?.takeIf { hidden > 0 }?.let { label ->
                    Text(
                        label,
                        style = MaterialTheme.typography.labelMedium,
                        color = c.inkTertiary,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = Space.xxs),
                    )
                }
            }
            caption()
            Gap(Space.xl)
            // Piles.
            val gap = Space.sm
            FlowRow(
                Modifier.fillMaxWidth().padding(horizontal = Space.gutter),
                horizontalArrangement = Arrangement.spacedBy(gap),
                verticalArrangement = Arrangement.spacedBy(Space.lg),
                maxItemsInEachRow = 3,
            ) {
                order.values.forEach { target ->
                    key(target.key) {
                        Pile(
                            target = target,
                            count = landed[target.key] ?: 0,
                            arrivals = arrivals[target.key] ?: 0,
                            reduceMotion = reduceMotion,
                            modifier = Modifier
                                .weight(1f)
                                .onGloballyPositioned { pileCoords[target.key] = it },
                        )
                    }
                }
                // Keep the last row's piles the same width as the others.
                repeat((3 - order.size % 3) % 3) { Box(Modifier.weight(1f)) }
            }
        }

        // Flights, trails and arrival sparkles, above everything.
        Canvas(Modifier.matchParentSize()) {
            if (frame < 0) return@Canvas // reading the frame makes this redraw every simulation step
            flights.forEach { f ->
                val points = f.trail
                points.forEachIndexed { i, p ->
                    val strength = (i + 1f) / (points.size + 1f)
                    drawCircle(c.accent.copy(alpha = 0.10f * strength * f.alpha), 3.dp.toPx() + 9.dp.toPx() * strength * f.scale, Offset(p.x, p.y))
                }
            }
            bursts.forEach { b -> drawBurst(b, c.accent) }
        }
        flights.forEach { f ->
            key(f.id) {
                Box(
                    Modifier.layout { m, cs ->
                        val p = m.measure(cs)
                        layout(0, 0) {
                            p.placeWithLayer(0, 0, zIndex = 3f) {
                                if (frame < 0) return@placeWithLayer // read so the layer updates each frame
                                translationX = f.position.x - p.width / 2f
                                translationY = f.position.y - p.height / 2f
                                rotationZ = f.rotation
                                scaleX = f.scale
                                scaleY = f.scale
                                alpha = f.alpha
                            }
                        }
                    },
                ) { Thumb(f.payload as? StageItem, lifted = true) }
            }
        }
    }
}

@Composable
private fun Gap(height: Dp) = Box(Modifier.height(height))

/** Orbit maths shared by the drawn thumbnails and the flight launcher. */
private class OrbitGeometry {
    var center = Vec.Zero
        private set
    private var rx = 1f
    private var ry = 1f
    private var bob = 0f

    fun update(center: Vec, rx: Float, ry: Float, bob: Float) {
        this.center = center
        this.rx = rx
        this.ry = ry
        this.bob = bob
    }

    fun angle(slot: Int, t: Float) = (2 * PI.toFloat() * slot / SLOTS) + t * ORBIT_SPEED + 0.4f

    /** 0 at the far (top) side of the ellipse, 1 at the near side. */
    fun depth(slot: Int, t: Float) = (sin(angle(slot, t)) + 1f) / 2f

    fun slotPosition(slot: Int, t: Float): Vec {
        val a = angle(slot, t)
        return Vec(center.x + cos(a) * rx, center.y + sin(a) * ry + sin(t * 1.3f + slot) * bob)
    }

    fun slotScale(slot: Int, t: Float) = 0.74f + 0.26f * depth(slot, t)

    fun offsetFromCenter(slot: Int, t: Float): Vec = slotPosition(slot, t) - center
}

@Composable
private fun OrbitingThumb(item: StageItem, slot: Int, geometry: OrbitGeometry, clock: androidx.compose.runtime.State<Float>, reduceMotion: Boolean) {
    val appear = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.gentle()) }
    Box(
        Modifier.layout { m, cs ->
            val p = m.measure(cs)
            layout(p.width, p.height) {
                val t = clock.value
                val depth = geometry.depth(slot, t)
                val o = geometry.offsetFromCenter(slot, t)
                // Near side in front of the ring, far side behind it.
                p.placeWithLayer(0, 0, zIndex = if (depth > 0.5f) 2f else 0f) {
                    translationX = o.x
                    translationY = o.y
                    val s = geometry.slotScale(slot, t) * (0.6f + 0.4f * appear.value)
                    scaleX = s
                    scaleY = s
                    alpha = (0.5f + 0.5f * depth) * appear.value
                    rotationZ = -cos(geometry.angle(slot, t)) * 7f + sin(t * 0.9f + slot) * 2f
                }
            }
        },
    ) { Thumb(item, lifted = false) }
}

/** A thumbnail printed like a photo: thin white border, rounded, softly shadowed. */
@Composable
private fun Thumb(item: StageItem?, lifted: Boolean) {
    val c = Sortora.colors
    Box(
        Modifier
            .size(THUMB)
            .softShadow(Radius.Sm, if (lifted) Depth.High else Depth.Mid, c)
            .clip(Radius.Sm)
            .background(if (c.dark) c.raised else Color.White)
            .hairline(Radius.Sm, c.hairline)
            .padding(2.5.dp)
            .clip(RoundedCornerShape(9.5.dp))
            .background(c.thumbnail),
    ) {
        if (item != null) {
            AsyncImage(model = item.uri, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            if (item.kind == MediaKind.VIDEO) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .size(16.dp)
                        .clip(Radius.Pill)
                        .background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.PlayArrow, null, tint = Color.White, modifier = Modifier.size(12.dp)) }
            }
        }
    }
}

@Composable
private fun Pile(target: StageTarget, count: Int, arrivals: Int, reduceMotion: Boolean, modifier: Modifier) {
    val c = Sortora.colors
    val appear = remember { Animatable(if (reduceMotion) 1f else 0f) }
    LaunchedEffect(Unit) { appear.animateTo(1f, Motion.bouncy()) }
    Column(
        modifier.layout { m, cs ->
            val p = m.measure(cs)
            layout(p.width, p.height) {
                p.placeWithLayer(0, 0) {
                    val a = appear.value
                    scaleX = 0.7f + 0.3f * a
                    scaleY = 0.7f + 0.3f * a
                    alpha = a.coerceIn(0f, 1f)
                    translationY = (1f - a) * 12.dp.toPx()
                }
            }
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        FolderGlyph(width = 62.dp, pulseKey = arrivals, icon = target.icon, active = arrivals > 0)
        Gap(Space.xs)
        Text(
            target.label,
            style = MaterialTheme.typography.labelMedium,
            color = c.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(104.dp),
        )
        AnimatedCount(count, MaterialTheme.typography.labelMedium, c.inkSecondary)
    }
}

private class Burst(val at: Vec, val born: Float, val seed: Int) {
    var now: Float = born

    companion object {
        const val LIFE = 0.55f
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawBurst(b: Burst, color: Color) {
    val p = ((b.now - b.born) / Burst.LIFE).coerceIn(0f, 1f)
    val e = 1f - (1f - p) * (1f - p)
    val center = Offset(b.at.x, b.at.y)
    drawCircle(
        Brush.radialGradient(listOf(color.copy(alpha = 0.22f * (1f - p)), Color.Transparent), center, 34.dp.toPx() * (0.5f + e)),
        34.dp.toPx() * (0.5f + e), center,
    )
    repeat(7) { i ->
        val angle = (i / 7f) * 2 * PI.toFloat() + b.seed * 0.6f - PI.toFloat() / 2
        val dist = (10.dp.toPx() + 22.dp.toPx() * e) * (if (i % 2 == 0) 1f else 0.75f)
        drawCircle(color.copy(alpha = 0.75f * (1f - p)), max(0.5f, 2.4.dp.toPx() * (1f - p * 0.6f)), center + Offset(cos(angle) * dist, sin(angle) * dist))
    }
}

/** A plain (non-snapshot) mutable holder usable with `by`. */
private class Ref<T>(var value: T)

private fun <T> mutableRefOf(value: T) = Ref(value)

private operator fun <T> Ref<T>.getValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>): T = value

private operator fun <T> Ref<T>.setValue(thisRef: Any?, property: kotlin.reflect.KProperty<*>, v: T) {
    value = v
}
