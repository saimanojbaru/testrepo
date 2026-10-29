package com.sortora.app.domain.classify

/**
 * Tiny perceptual fingerprints for video frames: a frame is shrunk to a 16×16 grid of luminance
 * values. Comparing two fingerprints tells a locked-off talking-head shot (almost identical
 * frames) from B-roll with cuts and camera movement (very different frames).
 */
object FrameSignature {

    const val SIZE = 16

    /** Luminance (0..1) of each ARGB pixel. [pixels] is typically SIZE×SIZE. */
    fun fromArgb(pixels: IntArray): FloatArray = FloatArray(pixels.size) { i ->
        val p = pixels[i]
        val r = (p shr 16) and 0xFF
        val g = (p shr 8) and 0xFF
        val b = p and 0xFF
        (0.299f * r + 0.587f * g + 0.114f * b) / 255f
    }

    /** Mean absolute luminance difference, 0 (identical) to 1. */
    fun difference(a: FloatArray, b: FloatArray): Float {
        if (a.isEmpty() || a.size != b.size) return 0f
        var sum = 0f
        for (i in a.indices) sum += kotlin.math.abs(a[i] - b[i])
        return sum / a.size
    }

    /** Average difference between consecutive signatures. */
    fun averageDifference(signatures: List<FloatArray>): Float {
        if (signatures.size < 2) return 0f
        return signatures.zipWithNext { a, b -> difference(a, b) }.average().toFloat()
    }
}
