package com.sortora.app.ui.sorting

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SortPhysicsTest {

    private val density = 3f
    private val targets = mapOf("Pets" to Vec(100f, 1500f), "Screenshots" to Vec(900f, 1500f))

    private fun run(sim: SortSimulation, seconds: Float, frame: Float = 1f / 60f, lookup: (String) -> Vec? = targets::get): List<SortSimulation.Landing> {
        val out = ArrayList<SortSimulation.Landing>()
        var t = 0f
        while (t < seconds) {
            out += sim.advance(frame, lookup)
            t += frame
        }
        return out
    }

    @Test
    fun flightRisesFirstThenLandsOnTarget() {
        val sim = SortSimulation(density)
        sim.request(1, "Pets", Vec(500f, 400f))
        sim.advance(1f / 60f, targets::get)
        val flight = sim.flights.single()
        repeat(6) { sim.advance(1f / 60f, targets::get) }
        assertTrue("kicked upward before falling toward a target below", flight.position.y < 400f)

        val landings = run(sim, 3f)
        assertEquals(listOf(1L), landings.map { it.id })
        assertTrue(landings.single().flew)
        assertEquals(Vec(100f, 1500f), landings.single().at)
        assertTrue("finished flights are removed", !sim.busy)
    }

    @Test
    fun staysFiniteAndBoundedThroughJank() {
        val sim = SortSimulation(density)
        for (i in 0 until 10L) sim.request(i, if (i % 2 == 0L) "Pets" else "Screenshots", Vec(500f, 300f + i * 10))
        var maxTilt = 0f
        var t = 0f
        // Alternate smooth frames with 200 ms hitches.
        var frame = 0
        while (t < 6f) {
            val dt = if (frame % 15 == 14) 0.2f else 1f / 60f
            sim.advance(dt, targets::get)
            sim.flights.forEach {
                assertTrue(it.position.isFinite && it.velocity.isFinite && it.scale.isFinite())
                assertTrue("never wildly off screen", it.position.y > -2000f && it.position.y < 4000f)
                maxTilt = maxOf(maxTilt, kotlin.math.abs(it.rotation))
            }
            t += dt
            frame++
        }
        assertTrue(maxTilt <= SortSimulation.MAX_TILT + 6f)
        assertTrue(!sim.busy)
    }

    @Test
    fun launchesAreStaggeredAndCapped() {
        val sim = SortSimulation(density, maxInFlight = 4)
        for (i in 0 until 12L) sim.request(i, "Pets", Vec(500f, 400f))
        sim.advance(1f / 60f, targets::get)
        assertEquals("one launch per frame at most", 1, sim.flights.size)
        repeat(30) { sim.advance(1f / 60f, targets::get) }
        assertTrue(sim.flights.count { !it.landed } <= 4)
        assertTrue(sim.flights.size >= 2)
    }

    @Test
    fun waitsForTargetsThatAreNotLaidOutYet() {
        val sim = SortSimulation(density)
        sim.request(1, "Food", Vec(0f, 0f))
        repeat(30) { sim.advance(1f / 60f, targets::get) }
        assertTrue(sim.flights.isEmpty())
        assertEquals(1, sim.queued)
        val landed = run(sim, 3f) { key -> targets[key] ?: Vec(500f, 1500f) }
        assertEquals(listOf(1L), landed.map { it.id })
    }

    @Test
    fun longQueuesSkipTheFlightInsteadOfFallingBehind() {
        val sim = SortSimulation(density, maxQueue = 5)
        for (i in 0 until 20L) sim.request(i, "Pets", Vec(0f, 0f))
        val first = sim.advance(1f / 60f, targets::get)
        assertEquals(15, first.count { !it.flew })
        val all = first + run(sim, 6f)
        assertEquals((0 until 20L).toSet(), all.map { it.id }.toSet())
    }

    @Test
    fun duplicateRequestsAreIgnored() {
        val sim = SortSimulation(density)
        sim.request(1, "Pets", Vec(0f, 0f))
        sim.request(1, "Pets", Vec(0f, 0f))
        assertEquals(1, run(sim, 3f).size)
    }

    @Test
    fun flushLandsEverythingAtOnce() {
        val sim = SortSimulation(density)
        for (i in 0 until 5L) sim.request(i, "Pets", Vec(0f, 0f))
        sim.advance(1f / 60f, targets::get)
        val flushed = sim.flush(targets::get)
        assertEquals(5, flushed.size)
        assertTrue(!sim.busy)
    }

    @Test
    fun slotsStayStableAndSpreadOut() {
        val slots = SlotAssigner(8)
        val first = slots.update(listOf(10, 11, 12, 13))
        assertEquals(setOf(0, 4, 2, 6), first.withIndex().filter { it.value != null }.map { it.index }.toSet())
        val second = slots.update(listOf(11, 12, 13, 14, 15))
        // 10 left; everyone else kept their slot and the newcomers filled gaps.
        listOf(11L, 12L, 13L).forEach { id -> assertEquals(first.indexOf(id), second.indexOf(id)) }
        assertEquals(5, second.count { it != null })
        assertEquals(listOf(0, 4, 2, 6, 1, 3, 5, 7), SlotAssigner.spreadOrder(8))
    }
}
