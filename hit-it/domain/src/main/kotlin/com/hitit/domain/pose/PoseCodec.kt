package com.hitit.domain.pose

/**
 * Serializes a pose (33 landmarks) to a compact string for Room storage and back. Format:
 * "x,y,z,v|x,y,z,v|..." with 5-decimal floats. Decode is defensive: any malformed input → null.
 */
object PoseCodec {

    fun encode(landmarks: List<PoseLandmark>): String =
        landmarks.joinToString("|") { lm ->
            // Locale.US: decimal POINTS always — a comma-decimal device locale would corrupt the CSV.
            "%.5f,%.5f,%.5f,%.5f".format(java.util.Locale.US, lm.x, lm.y, lm.z, lm.visibility)
        }

    fun decode(encoded: String): List<PoseLandmark>? {
        if (encoded.isBlank()) return null
        val landmarks = encoded.split("|").map { part ->
            val nums = part.split(",")
            if (nums.size != 4) return null
            PoseLandmark(
                x = nums[0].toFloatOrNull() ?: return null,
                y = nums[1].toFloatOrNull() ?: return null,
                z = nums[2].toFloatOrNull() ?: return null,
                visibility = nums[3].toFloatOrNull() ?: return null,
            )
        }
        return landmarks.takeIf { it.isNotEmpty() }
    }
}

/**
 * Tracks hold time across a live practice session: feed (timestamp, score) samples; time accrues
 * while the score stays at/above the threshold and the current run resets on a dip. Deterministic —
 * timestamps are injected.
 */
class HoldTracker(private val thresholdScore: Int = PoseMatcher.GOOD_HOLD_SCORE) {
    var currentHoldMs: Long = 0
        private set
    var bestHoldMs: Long = 0
        private set
    private var lastTimestampMs: Long? = null
    private var lastGood = false

    fun feed(timestampMs: Long, score: Int) {
        val good = score >= thresholdScore
        val last = lastTimestampMs
        if (good && lastGood && last != null && timestampMs > last) {
            currentHoldMs += timestampMs - last
        } else if (!good) {
            currentHoldMs = 0
        }
        if (currentHoldMs > bestHoldMs) bestHoldMs = currentHoldMs
        lastTimestampMs = timestampMs
        lastGood = good
    }

    fun reset() {
        currentHoldMs = 0
        bestHoldMs = 0
        lastTimestampMs = null
        lastGood = false
    }
}
