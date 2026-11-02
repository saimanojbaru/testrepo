package com.nonverbal.mastery.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import kotlinx.serialization.Serializable

object LevelStatus {
    const val OPEN = "OPEN"
    const val IN_PROGRESS = "IN_PROGRESS"
    const val MASTERED = "MASTERED"
}

@Serializable
@Entity(tableName = "progress")
data class LevelProgress(
    @PrimaryKey val levelId: String,
    val categoryId: String,
    val status: String,
    val quizScore: Int = 0,
    val quizAnswersJson: String = "[]",   // full answer history for review
    val drillsDone: Int = 0,               // bitmask
    val fieldLogged: Boolean = false,
    val reflection: String = "",
    val masteredAt: Long? = null,
    val nextReviewAt: Long? = null,
    val reviewStreak: Int = 0,
    val lastReviewedAt: Long? = null,
    /** Additive: the logged field mission — what was seen, guessed, and what would have disproved it. */
    val fieldNote: String = "",
    /** Additive: when the reader last opened this level; drives "continue reading". */
    val lastOpenedAt: Long? = null,
)

@Serializable
@Entity(tableName = "pose_bookmarks")
data class PoseBookmark(
    @PrimaryKey val poseId: String,
    val note: String = "",
    val savedAt: Long,
)

@Serializable
@Entity(tableName = "review_log")
data class ReviewLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val levelId: String,
    val reviewedAt: Long,
    val durationMs: Long,
)
