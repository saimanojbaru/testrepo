package com.hitit.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hitit.app.data.local.entity.LockInSessionEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface LockInSessionDao {

    @Insert
    suspend fun insert(session: LockInSessionEntity): Long

    @Query("SELECT COALESCE(SUM(focusedMinutes), 0) FROM lock_in_sessions")
    fun observeTotalFocusMinutes(): Flow<Int>

    @Query("SELECT COALESCE(SUM(focusedMinutes), 0) FROM lock_in_sessions WHERE date = :date")
    fun observeFocusMinutesOn(date: LocalDate): Flow<Int>

    @Query("SELECT COUNT(*) FROM lock_in_sessions")
    fun observeSessionCount(): Flow<Int>

    @Query("SELECT * FROM lock_in_sessions ORDER BY startTime DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<LockInSessionEntity>>
}
