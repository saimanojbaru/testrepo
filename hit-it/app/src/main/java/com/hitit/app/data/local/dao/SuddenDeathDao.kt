package com.hitit.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hitit.app.data.local.entity.SuddenDeathPenaltyEntity
import java.time.LocalDate

@Dao
interface SuddenDeathDao {

    @Query("SELECT date FROM sudden_death_penalties WHERE repId = :repId")
    suspend fun penalizedDates(repId: Long): List<LocalDate>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun record(penalty: SuddenDeathPenaltyEntity)
}
