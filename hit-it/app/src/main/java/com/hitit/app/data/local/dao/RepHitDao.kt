package com.hitit.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hitit.app.data.local.entity.RepHitEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface RepHitDao {

    @Query("SELECT * FROM rep_hits WHERE repId = :repId ORDER BY date")
    fun observeForRep(repId: Long): Flow<List<RepHitEntity>>

    @Query("SELECT * FROM rep_hits WHERE repId = :repId")
    suspend fun getForRep(repId: Long): List<RepHitEntity>

    @Query("SELECT * FROM rep_hits WHERE date BETWEEN :start AND :end ORDER BY date")
    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<RepHitEntity>>

    @Query("SELECT * FROM rep_hits WHERE repId = :repId AND date = :date LIMIT 1")
    suspend fun getForRepOnDate(repId: Long, date: LocalDate): RepHitEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(hit: RepHitEntity): Long

    @Query("DELETE FROM rep_hits WHERE repId = :repId AND date = :date")
    suspend fun deleteForRepOnDate(repId: Long, date: LocalDate)
}
