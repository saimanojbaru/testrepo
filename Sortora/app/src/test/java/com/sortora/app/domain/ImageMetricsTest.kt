package com.sortora.app.domain

import com.sortora.app.domain.classify.ImageMetrics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class ImageMetricsTest {

    private val w = 72
    private val h = 64

    /** A smooth diagonal gradient with a bright square: a stand-in "photo". */
    private fun photo(brightness: Float = 0f, shiftX: Int = 0) = FloatArray(w * h) { i ->
        val x = i % w + shiftX
        val y = i / w
        val base = (x * 2f + y) % 255f
        val square = if (x in 20..40 && y in 10..30) 120f else 0f
        (base + square + brightness).coerceIn(0f, 255f)
    }

    @Test
    fun `identical images have identical hashes`() {
        assertEquals(0, ImageMetrics.hammingDistance(ImageMetrics.differenceHash(photo(), w, h), ImageMetrics.differenceHash(photo(), w, h)))
    }

    @Test
    fun `small brightness changes barely move the hash`() {
        val a = ImageMetrics.differenceHash(photo(), w, h)
        val b = ImageMetrics.differenceHash(photo(brightness = 10f), w, h)
        assertTrue(ImageMetrics.hammingDistance(a, b) <= 8)
    }

    @Test
    fun `different images are far apart`() {
        val random = Random(42)
        val noise = FloatArray(w * h) { random.nextFloat() * 255f }
        val distance = ImageMetrics.hammingDistance(ImageMetrics.differenceHash(photo(), w, h), ImageMetrics.differenceHash(noise, w, h))
        assertTrue("distance was $distance", distance > 18)
    }

    @Test
    fun `sharp detail scores higher than a blurred version`() {
        val size = 128
        val checker = FloatArray(size * size) { i -> if (((i % size) / 4 + (i / size) / 4) % 2 == 0) 230f else 20f }
        val blurred = boxBlur(boxBlur(checker, size), size)
        val sharp = ImageMetrics.laplacianVariance(checker, size, size)
        val soft = ImageMetrics.laplacianVariance(blurred, size, size)
        assertTrue(sharp > ImageMetrics.BLUR_THRESHOLD)
        assertTrue(soft < sharp / 10)
    }

    @Test
    fun `flat images count as blurry`() {
        assertTrue(ImageMetrics.laplacianVariance(FloatArray(100 * 100) { 128f }, 100, 100) < ImageMetrics.BLUR_THRESHOLD)
    }

    @Test
    fun `normalised dot product is cosine similarity`() {
        val a = ImageMetrics.normalize(floatArrayOf(3f, 4f))
        assertEquals(1f, ImageMetrics.dot(a, a), 1e-5f)
        assertEquals(0f, ImageMetrics.dot(a, ImageMetrics.normalize(floatArrayOf(-4f, 3f))), 1e-5f)
    }

    private fun boxBlur(src: FloatArray, size: Int): FloatArray = FloatArray(src.size) { i ->
        val x = i % size
        val y = i / size
        var sum = 0f
        var n = 0
        for (dy in -3..3) for (dx in -3..3) {
            val xx = x + dx
            val yy = y + dy
            if (xx in 0 until size && yy in 0 until size) {
                sum += src[yy * size + xx]
                n++
            }
        }
        sum / n
    }
}
