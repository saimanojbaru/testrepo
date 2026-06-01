package com.hitit.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.hitit.app.data.local.entity.RepEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface RepDao {

    @Query("SELECT * FROM reps WHERE isArchived = 0 ORDER BY sortOrder, id")
    fun observeActive(): Flow<List<RepEntity>>

    @Query("SELECT * FROM reps WHERE isArchived = 0 ORDER BY sortOrder, id")
    suspend fun activeSnapshot(): List<RepEntity>

    @Query("SELECT * FROM reps ORDER BY isArchived, sortOrder, id")
    fun observeAll(): Flow<List<RepEntity>>

    @Query("SELECT * FROM reps WHERE id = :id")
    fun observeById(id: Long): Flow<RepEntity?>

    @Query("SELECT * FROM reps WHERE id = :id")
    suspend fun getById(id: Long): RepEntity?

    @Query("SELECT * FROM reps WHERE reminderEnabled = 1 AND isArchived = 0")
    suspend fun getReminderEnabled(): List<RepEntity>

    @Insert
    suspend fun insert(rep: RepEntity): Long

    @Update
    suspend fun update(rep: RepEntity)

    @Query("UPDATE reps SET isArchived = :archived WHERE id = :id")
    suspend fun setArchived(id: Long, archived: Boolean)

    @Query("UPDATE reps SET restModeStart = :start, restModeEnd = :end WHERE id = :id")
    suspend fun setRestMode(id: Long, start: LocalDate?, end: LocalDate?)

    @Delete
    suspend fun delete(rep: RepEntity)

    @Query("SELECT COUNT(*) FROM reps")
    suspend fun count(): Int

    @Query("DELETE FROM reps")
    suspend fun deleteAll()
}
