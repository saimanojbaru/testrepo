package com.hitit.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/** A "Hit" — a one-off task. Optionally linked to a Rep and/or flagged as a day's Main Target. */
@Entity(
    tableName = "hit_tasks",
    indices = [Index(value = ["mainTargetDate"]), Index(value = ["isDone"])],
)
data class HitTaskEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    /** Priority name: HIGH / MEDIUM / LOW. */
    val priority: String = "MEDIUM",
    val dueDate: LocalDate? = null,
    val tagsCsv: String = "",
    val isDone: Boolean = false,
    val completedAt: Instant? = null,
    /** The day on which this task is the single "Main Target"; null otherwise. */
    val mainTargetDate: LocalDate? = null,
    /** Optional link to a Rep (no FK so the task survives if the rep is removed). */
    val repId: Long? = null,
    val sortOrder: Int = 0,
    val createdAt: Instant = Instant.now(),
)
