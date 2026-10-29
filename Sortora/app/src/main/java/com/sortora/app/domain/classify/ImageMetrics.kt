package com.sortora.app.domain.classify

import kotlin.math.sqrt

/**
 * Small, dependency-free image measurements computed from luminance values (0..255), so they can
 * be unit-tested on the JVM. The data layer only has to turn a Bitmap into a luminance array.
 */
object ImageMetrics {

    /** Grid the perceptual hash is computed on: 9 × 8 cells → 8 comparisons per row → 64 bits. */
    const val HASH_COLUMNS = 9
    const val HASH_ROWS = 8

    /** Below this variance of the Laplacian (on a ≤512 px image) a photo counts as blurry. */
    const val BLUR_THRESHOLD = 60f

    /** Luminance (0..255) of each ARGB pixel. */
    fun luminance(argb: IntArray): FloatArray = FloatArray(argb.size) { i ->
        val p = argb[i]
        0.299f * ((p shr 16) and 0xFF) + 0.587f * ((p shr 8) and 0xFF) + 0.114f * (p and 0xFF)
    }

    /**
     * Difference hash ("dHash"): the image is averaged into a 9×8 grid and each bit records
     * whether a cell is brighter than its right neighbour. Robust to resizing, re-compression and
     * small colour changes, so near-identical photos get hashes a few bits apart.
     */
    fun differenceHash(lum: FloatArray, width: Int, height: Int): Long {
        require(lum.size == width * height && width >= HASH_COLUMNS && height >= HASH_ROWS)
        val cells = FloatArray(HASH_COLUMNS * HASH_ROWS)
        for (row in 0 until HASH_ROWS) {
            val y0 = row * height / HASH_ROWS
            val y1 = ((row + 1) * height / HASH_ROWS).coerceAtLeast(y0 + 1)
            for (col in 0 until HASH_COLUMNS) {
                val x0 = col * width / HASH_COLUMNS
                val x1 = ((col + 1) * width / HASH_COLUMNS).coerceAtLeast(x0 + 1)
                var sum = 0f
                for (y in y0 until y1) for (x in x0 until x1) sum += lum[y * width + x]
                cells[row * HASH_COLUMNS + col] = sum / ((y1 - y0) * (x1 - x0))
            }
        }
        var hash = 0L
        var bit = 0
        for (row in 0 until HASH_ROWS) {
            for (col in 0 until HASH_COLUMNS - 1) {
                if (cells[row * HASH_COLUMNS + col] > cells[row * HASH_COLUMNS + col + 1]) hash = hash or (1L shl bit)
                bit++
            }
        }
        return hash
    }

    fun hammingDistance(a: Long, b: Long): Int = java.lang.Long.bitCount(a xor b)

    /**
     * Sharpness as the variance of the Laplacian: sharp edges give large second derivatives,
     * blur flattens them. Compare results only between images of similar size (the analyzer
     * always measures at ≤ 512 px).
     */
    fun laplacianVariance(lum: FloatArray, width: Int, height: Int): Float {
        if (width < 3 || height < 3) return 0f
        var sum = 0.0
        var sumSq = 0.0
        var n = 0
        for (y in 1 until height - 1) {
            for (x in 1 until width - 1) {
                val i = y * width + x
                val v = (lum[i - width] + lum[i + width] + lum[i - 1] + lum[i + 1] - 4 * lum[i]).toDouble()
                sum += v
                sumSq += v * v
                n++
            }
        }
        val mean = sum / n
        return (sumSq / n - mean * mean).toFloat()
    }

    /** L2-normalised copy, so dot products are cosine similarities. */
    fun normalize(vector: FloatArray): FloatArray {
        var norm = 0.0
        for (v in vector) norm += v * v
        val scale = if (norm > 0) (1.0 / sqrt(norm)).toFloat() else 0f
        return FloatArray(vector.size) { vector[it] * scale }
    }

    /** Cosine similarity of two vectors that are already L2-normalised. */
    fun dot(a: FloatArray, b: FloatArray): Float {
        if (a.size != b.size) return 0f
        var s = 0f
        for (i in a.indices) s += a[i] * b[i]
        return s
    }
}
