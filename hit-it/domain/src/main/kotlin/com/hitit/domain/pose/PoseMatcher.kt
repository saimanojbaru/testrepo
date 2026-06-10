package com.hitit.domain.pose

import kotlin.math.abs
import kotlin.math.atan2

/**
 * One body landmark in normalized image space (BlazePose convention: x/y in 0..1, z depth-ish,
 * visibility 0..1). Defined here so the matcher is pure Kotlin — the camera layer maps MediaPipe's
 * landmarks into these.
 */
data class PoseLandmark(
    val x: Float,
    val y: Float,
    val z: Float = 0f,
    val visibility: Float = 1f,
)

/** BlazePose 33-landmark indices (stable public spec) — only the joints the matcher needs. */
object PoseJoints {
    const val LEFT_SHOULDER = 11
    const val RIGHT_SHOULDER = 12
    const val LEFT_ELBOW = 13
    const val RIGHT_ELBOW = 14
    const val LEFT_WRIST = 15
    const val RIGHT_WRIST = 16
    const val LEFT_HIP = 23
    const val RIGHT_HIP = 24
    const val LEFT_KNEE = 25
    const val RIGHT_KNEE = 26
    const val LEFT_ANKLE = 27
    const val RIGHT_ANKLE = 28
    const val LANDMARK_COUNT = 33
}

/**
 * The 8 form angles used to judge a pose: angle at [b] between rays b→a and b→c. Angle-based
 * comparison is translation/scale invariant, so camera distance and framing don't matter.
 * [bendy] joints (elbows/knees) get "straighten/bend" hints; the rest get "open/close".
 */
enum class FormAngle(val label: String, val a: Int, val b: Int, val c: Int, val bendy: Boolean) {
    LEFT_ELBOW("left elbow", PoseJoints.LEFT_SHOULDER, PoseJoints.LEFT_ELBOW, PoseJoints.LEFT_WRIST, true),
    RIGHT_ELBOW("right elbow", PoseJoints.RIGHT_SHOULDER, PoseJoints.RIGHT_ELBOW, PoseJoints.RIGHT_WRIST, true),
    LEFT_SHOULDER("left shoulder", PoseJoints.LEFT_ELBOW, PoseJoints.LEFT_SHOULDER, PoseJoints.LEFT_HIP, false),
    RIGHT_SHOULDER("right shoulder", PoseJoints.RIGHT_ELBOW, PoseJoints.RIGHT_SHOULDER, PoseJoints.RIGHT_HIP, false),
    LEFT_HIP("left hip", PoseJoints.LEFT_SHOULDER, PoseJoints.LEFT_HIP, PoseJoints.LEFT_KNEE, false),
    RIGHT_HIP("right hip", PoseJoints.RIGHT_SHOULDER, PoseJoints.RIGHT_HIP, PoseJoints.RIGHT_KNEE, false),
    LEFT_KNEE("left knee", PoseJoints.LEFT_HIP, PoseJoints.LEFT_KNEE, PoseJoints.LEFT_ANKLE, true),
    RIGHT_KNEE("right knee", PoseJoints.RIGHT_HIP, PoseJoints.RIGHT_KNEE, PoseJoints.RIGHT_ANKLE, true),
}

data class JointFeedback(val label: String, val deviationDeg: Float, val hint: String)

data class PoseMatch(
    val score: Int,                    // 0..100
    val feedback: List<JointFeedback>, // worst joints first; empty when form is clean
    val anglesUsed: Int,               // how many of the 8 angles were judgeable
)

/**
 * The autonomous form checker: compares an attempt against a user-supplied reference pose, scores
 * 0..100 and names exactly which joints are off and how to fix them. Pure + deterministic.
 *
 * Scoring: per-angle deviation ≤ [FULL_CREDIT_DEG]° earns full credit, falling linearly to zero at
 * [ZERO_CREDIT_DEG]°; the score is the mean credit. Angles are skipped when any landmark involved is
 * low-visibility in either pose; fewer than [MIN_ANGLES] usable angles → null (can't judge fairly).
 */
object PoseMatcher {
    const val VISIBILITY_MIN = 0.5f
    const val FULL_CREDIT_DEG = 10f
    const val ZERO_CREDIT_DEG = 45f
    const val MIN_ANGLES = 4
    const val GOOD_HOLD_SCORE = 80

    /** Inner angle at b (degrees 0..180), computed in 2D — monocular z is too noisy to grade. */
    fun angleDeg(a: PoseLandmark, b: PoseLandmark, c: PoseLandmark): Float {
        val abx = a.x - b.x
        val aby = a.y - b.y
        val cbx = c.x - b.x
        val cby = c.y - b.y
        val dot = abx * cbx + aby * cby
        val cross = abx * cby - aby * cbx
        return Math.toDegrees(atan2(abs(cross).toDouble(), dot.toDouble())).toFloat()
    }

    fun match(reference: List<PoseLandmark>, attempt: List<PoseLandmark>): PoseMatch? {
        var creditSum = 0f
        var used = 0
        val feedback = mutableListOf<JointFeedback>()

        for (angle in FormAngle.entries) {
            val refA = reference.getOrNull(angle.a) ?: continue
            val refB = reference.getOrNull(angle.b) ?: continue
            val refC = reference.getOrNull(angle.c) ?: continue
            val attA = attempt.getOrNull(angle.a) ?: continue
            val attB = attempt.getOrNull(angle.b) ?: continue
            val attC = attempt.getOrNull(angle.c) ?: continue
            val visible = listOf(refA, refB, refC, attA, attB, attC).all { it.visibility >= VISIBILITY_MIN }
            if (!visible) continue

            val ref = angleDeg(refA, refB, refC)
            val att = angleDeg(attA, attB, attC)
            val dev = abs(ref - att)
            creditSum += (1f - ((dev - FULL_CREDIT_DEG) / (ZERO_CREDIT_DEG - FULL_CREDIT_DEG))).coerceIn(0f, 1f)
            used++

            if (dev > FULL_CREDIT_DEG) {
                val needsBigger = att < ref // attempt angle too closed vs reference
                val hint = when {
                    angle.bendy && needsBigger -> "straighten your ${angle.label}"
                    angle.bendy -> "bend your ${angle.label} more"
                    needsBigger -> "open your ${angle.label} more"
                    else -> "close your ${angle.label} a bit"
                }
                feedback += JointFeedback(angle.label, dev, hint)
            }
        }

        if (used < MIN_ANGLES) return null
        val score = ((creditSum / used) * 100f).toInt().coerceIn(0, 100)
        return PoseMatch(score, feedback.sortedByDescending { it.deviationDeg }, used)
    }

    fun verdict(score: Int): String = when {
        score >= 95 -> "FRAME PERFECT 👁️✨"
        score >= GOOD_HOLD_SCORE -> "locked in — hold it"
        score >= 60 -> "close. listen to the hints"
        score >= 35 -> "the vibe is there, the form isn't"
        else -> "that's... a different pose bestie 💀"
    }
}
