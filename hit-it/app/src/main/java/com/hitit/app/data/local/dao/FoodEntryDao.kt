package com.hitit.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hitit.app.data.local.entity.FoodEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface FoodEntryDao {
    @Insert
    suspend fun insert(entry: FoodEntryEntity): Long

    @Query("SELECT * FROM food_entries WHERE date = :date ORDER BY createdAt DESC")
    fun observeForDate(date: LocalDate): Flow<List<FoodEntryEntity>>

    @Query("SELECT * FROM food_entries WHERE id = :id")
    suspend fun getById(id: Long): FoodEntryEntity?

    @Query("SELECT COUNT(DISTINCT date) FROM food_entries WHERE date >= :since")
    fun observeDistinctDaysSince(since: LocalDate): Flow<Int>

    @Query("SELECT COUNT(*) FROM food_entries")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM food_entries WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM food_entries")
    suspend fun deleteAll()
}
