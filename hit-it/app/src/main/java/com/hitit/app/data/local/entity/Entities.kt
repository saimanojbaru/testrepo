package com.hitit.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/** The single local user. */
@Entity(tableName = "user_profile")
data class UserProfileEntity(
    @PrimaryKey val id: String = ID,
    val momentum: Long = 0,
    val level: Int = 1,
    val tier: String = "Rookie",
    val joinDate: LocalDate = LocalDate.now(),
) {
    companion object {
        const val ID = "me"
    }
}

/** A Rep (habit). Schedule is stored in storable primitives and mapped to domain RepCore. */
@Entity(tableName = "reps")
data class RepEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val emoji: String = "✅",
    val colorHex: String = "#33E1FF",
    /** ScheduleType name: DAILY / WEEKDAYS / CUSTOM / WEEKLY. */
    val scheduleType: String = "DAILY",
    /** ISO day numbers for CUSTOM, e.g. "1,3,5" (Mon,Wed,Fri). */
    val customDaysCsv: String = "",
    /** Required hits per ISO week for WEEKLY. */
    val weeklyTarget: Int = 1,
    /** Hits needed in a day to count as met (multi-hit). */
    val targetCount: Int = 1,
    /** Skip-protection budget (rest days). */
    val restDaysAllowed: Int = 2,
    val restModeStart: LocalDate? = null,
    val restModeEnd: LocalDate? = null,
    val isArchived: Boolean = false,
    val sortOrder: Int = 0,
    val createdDate: LocalDate = LocalDate.now(),
)

/** A logged completion ("hit") of a Rep on a given calendar day. */
@Entity(
    tableName = "rep_hits",
    foreignKeys = [
        ForeignKey(
            entity = RepEntity::class,
            parentColumns = ["id"],
            childColumns = ["repId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["repId", "date"], unique = true),
        Index(value = ["date"]),
    ],
)
data class RepHitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val repId: Long,
    val date: LocalDate,
    val hitCount: Int = 1,
    val loggedZone: String = "",
    val timestamp: Instant = Instant.now(),
)

/** Append-only Momentum (XP) ledger entry. */
@Entity(tableName = "momentum_txns")
data class MomentumTxnEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Int,
    val reason: String,
    val repId: Long? = null,
    val timestamp: Instant = Instant.now(),
)
