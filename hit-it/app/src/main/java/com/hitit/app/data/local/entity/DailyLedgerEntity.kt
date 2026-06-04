package com.hitit.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.time.LocalDate

/**
 * An immutable, finalized snapshot of a single day — the "strict ledger". Written once when the day
 * is finalized (the morning after, or on app open for any un-finalized past day) and then read-only.
 * Drives Momentum Debt and feeds the coach's pattern analysis.
 */
@Entity(tableName = "daily_ledger")
data class DailyLedgerEntity(
    @PrimaryKey val date: LocalDate,
    val momentumScore: Int = 0,
    val repsScheduled: Int = 0,
    val repsMet: Int = 0,
    val hitsCompleted: Int = 0,
    val focusMinutes: Int = 0,
    val checkedIn: Boolean = false,
    val debt: Int = 0,
    val reconciliationDue: Boolean = false,
    val finalizedAt: Instant = Instant.now(),
)
