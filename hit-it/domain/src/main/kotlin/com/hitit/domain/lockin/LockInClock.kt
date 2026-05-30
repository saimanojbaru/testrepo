package com.hitit.domain.lockin

/** Pure timer math for a Lock In (focus) session, so the UI/Service stay thin and testable. */
object LockInClock {

    /** Fraction elapsed in [0,1]. */
    fun progress(remainingMillis: Long, totalMillis: Long): Float {
        if (totalMillis <= 0L) return 0f
        val elapsed = (totalMillis - remainingMillis).coerceIn(0L, totalMillis)
        return (elapsed.toFloat() / totalMillis.toFloat()).coerceIn(0f, 1f)
    }

    /** "m:ss" countdown text, rounding up so a fresh 25-minute session reads "25:00". */
    fun format(remainingMillis: Long): String {
        val totalSeconds = (remainingMillis.coerceAtLeast(0L) + 999L) / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return "%d:%02d".format(minutes, seconds)
    }

    /** Whole minutes actually focused so far. */
    fun focusedMinutes(totalMillis: Long, remainingMillis: Long): Int =
        ((totalMillis - remainingMillis).coerceAtLeast(0L) / 60_000L).toInt()
}
