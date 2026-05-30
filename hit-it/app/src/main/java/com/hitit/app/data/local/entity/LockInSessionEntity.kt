package com.hitit.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/** A completed (or stopped-early) Lock In focus session. */
@Entity(
    tableName = "lock_in_sessions",
    indices = [Index(value = ["date"])],
)
data class LockInSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Instant,
    val endTime: Instant,
    val plannedMinutes: Int,
    val focusedMinutes: Int,
    val repId: Long? = null,
    val taskId: Long? = null,
    val zone: String = "Deep",
    /** true if the full planned duration elapsed; false if the user stopped early. */
    val completed: Boolean = true,
    /** Local date the session ended, for future Grid integration. */
    val date: LocalDate,
)
