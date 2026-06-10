package com.hitit.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/** One MoneyVibe expense. Amount in paise (minor units) to avoid float money. */
@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val amountPaise: Long,
    val description: String,
    /** [com.hitit.domain.money.SpendCategory.name] — stored as the enum name string. */
    val category: String,
    val impulse: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)
