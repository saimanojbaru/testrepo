package com.sortora.app.domain

import com.sortora.app.domain.classify.ImageMetrics
import com.sortora.app.domain.faces.FaceClustering
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class FaceClusteringTest {

    private val random = Random(7)
    private val dim = 64

    private fun around(center: FloatArray, noise: Float) =
        ImageMetrics.normalize(FloatArray(dim) { center[it] + (random.nextFloat() - 0.5f) * noise })

    private fun person(seed: Int) = Random(seed).let { r -> FloatArray(dim) { r.nextFloat() } }

    @Test
    fun `faces of the same person cluster together`() {
        // A shared "face" component plus a per-person component, like general-purpose embeddings.
        val common = FloatArray(dim) { 1f }
        val alice = person(1).mapIndexed { i, v -> v + common[i] * 3 }.toFloatArray()
        val bob = person(2).mapIndexed { i, v -> v + common[i] * 3 }.toFloatArray()
        val faces = List(5) { around(alice, 0.3f) } + List(5) { around(bob, 0.3f) }

        val clusters = FaceClustering.index(faces).clusters(FaceClustering.DEFAULT_THRESHOLD)
        val big = clusters.filter { it.size > 1 }
        assertEquals(2, big.size)
        assertTrue(big.any { it.all { i -> i < 5 } })
        assertTrue(big.any { it.all { i -> i >= 5 } })
    }

    @Test
    fun `a very strict threshold leaves everyone alone`() {
        val faces = List(6) { around(person(it + 10), 0.5f) }
        assertTrue(FaceClustering.index(faces).clusters(0.99f).all { it.size == 1 })
    }

    @Test
    fun `cluster name is the most common name`() {
        assertEquals("Mom", FaceClustering.clusterName(listOf("Mom", null, "Mom", "Mum")))
        assertEquals(null, FaceClustering.clusterName(listOf(null, " ")))
    }
}
