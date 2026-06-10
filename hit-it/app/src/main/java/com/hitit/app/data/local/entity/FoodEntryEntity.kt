package com.hitit.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/** One logged food/fuel entry in BodyFlow. kcal is the (estimated or manual) energy for the entry. */
@Entity(tableName = "food_entries")
data class FoodEntryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val date: LocalDate,
    val description: String,
    val kcal: Int,
    val createdAt: Long = System.currentTimeMillis(),
)
