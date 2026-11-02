package com.nonverbal.mastery.data.repo

import com.nonverbal.mastery.data.db.LevelProgress
import java.util.concurrent.TimeUnit

/**
 * Spaced return. After mastery a level comes back after 1, 3, 7, 21, 60 and 120 days.
 * Each return asks for one real task, not a quiz. Missing a return locks nothing:
 * the level simply waits in the queue. After the sixth return the level is retained.
 */
object ReviewScheduler {
    val INTERVAL_DAYS = listOf(1, 3, 7, 21, 60, 120)
    private val DAY = TimeUnit.DAYS.toMillis(1)

    fun onMastered(p: LevelProgress, now: Long): LevelProgress =
        p.copy(nextReviewAt = now + INTERVAL_DAYS[0] * DAY, reviewStreak = 0)

    fun onReviewed(p: LevelProgress, now: Long): LevelProgress {
        val streak = p.reviewStreak + 1
        val next = INTERVAL_DAYS.getOrNull(streak)?.let { now + it * DAY }
        return p.copy(reviewStreak = streak, nextReviewAt = next, lastReviewedAt = now)
    }

    fun isDue(p: LevelProgress, now: Long): Boolean = p.nextReviewAt != null && p.nextReviewAt <= now

    fun isRetained(p: LevelProgress): Boolean = p.masteredAt != null && p.nextReviewAt == null

    /** Days a due review has been waiting. Shown gently, never as a penalty. */
    fun daysWaiting(p: LevelProgress, now: Long): Int =
        p.nextReviewAt?.let { ((now - it) / DAY).toInt().coerceAtLeast(0) } ?: 0

    /** The next interval in days, for display ("returns in 7 days"). Null once retained. */
    fun nextIntervalDays(p: LevelProgress): Int? = INTERVAL_DAYS.getOrNull(p.reviewStreak)

    /** One sitting should take about ten minutes: at most five returns at a time. */
    const val SITTING_SIZE = 5
}
