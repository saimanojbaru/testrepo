package com.hitit.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hitit.app.data.local.entity.CheckInEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface CheckInDao {

    @Query("SELECT * FROM check_ins WHERE date = :date")
    fun observeForDate(date: LocalDate): Flow<CheckInEntity?>

    @Query("SELECT * FROM check_ins WHERE date = :date")
    suspend fun getForDate(date: LocalDate): CheckInEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CheckInEntity)

    /** Dates with a "counting" Check-In (any content) — used for The Grid. */
    @Query("SELECT date FROM check_ins WHERE morning != '' OR evening != '' OR mood IS NOT NULL")
    fun observeActiveDates(): Flow<List<LocalDate>>

    @Query("SELECT COUNT(*) FROM check_ins WHERE morning != '' OR evening != '' OR mood IS NOT NULL")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM check_ins")
    suspend fun deleteAll()
}
