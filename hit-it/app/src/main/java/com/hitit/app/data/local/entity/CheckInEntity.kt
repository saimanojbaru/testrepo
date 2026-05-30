package com.hitit.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/** A daily Check-In (journal). One row per day, keyed by date. */
@Entity(tableName = "check_ins")
data class CheckInEntity(
    @PrimaryKey val date: LocalDate,
    val morning: String = "",
    val evening: String = "",
    val mood: Int? = null,
    val energy: Int? = null,
    /** Sticky flags so Momentum is awarded only the first time each entry gets content. */
    val morningAwarded: Boolean = false,
    val eveningAwarded: Boolean = false,
    val updatedAt: Instant = Instant.now(),
)
