package com.hitit.domain.goal

/** Pure progress math for Big Plays (goals). */
object GoalProgress {

    /** Fraction in [0,1] from a numeric current/target. */
    fun fromValue(current: Double, target: Double): Float {
        if (target <= 0.0) return 0f
        return (current / target).toFloat().coerceIn(0f, 1f)
    }

    /** Fraction in [0,1] from completed vs total checkpoints. */
    fun fromCheckpoints(done: Int, total: Int): Float {
        if (total <= 0) return 0f
        return (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
    }

    fun percent(fraction: Float): Int = (fraction.coerceIn(0f, 1f) * 100f).toInt()
}
