package com.hitit.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.hitit.app.data.local.entity.HitTaskEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate

@Dao
interface HitTaskDao {

    @Query("SELECT * FROM hit_tasks ORDER BY isDone, sortOrder, id")
    fun observeAll(): Flow<List<HitTaskEntity>>

    @Query("SELECT COUNT(*) FROM hit_tasks WHERE isDone = 1")
    fun observeCompletedCount(): Flow<Int>

    @Query("SELECT * FROM hit_tasks WHERE mainTargetDate = :date LIMIT 1")
    fun observeMainTarget(date: LocalDate): Flow<HitTaskEntity?>

    @Query("SELECT * FROM hit_tasks WHERE id = :id")
    fun observeById(id: Long): Flow<HitTaskEntity?>

    @Query("SELECT * FROM hit_tasks WHERE id = :id")
    suspend fun getById(id: Long): HitTaskEntity?

    @Insert
    suspend fun insert(task: HitTaskEntity): Long

    @Update
    suspend fun update(task: HitTaskEntity)

    @Delete
    suspend fun delete(task: HitTaskEntity)

    @Query("UPDATE hit_tasks SET isDone = :done, completedAt = :at WHERE id = :id")
    suspend fun setDone(id: Long, done: Boolean, at: Instant?)

    /** Clear the Main Target flag from any task currently holding it for [date]. */
    @Query("UPDATE hit_tasks SET mainTargetDate = NULL WHERE mainTargetDate = :date")
    suspend fun clearMainTargetFor(date: LocalDate)

    @Query("UPDATE hit_tasks SET mainTargetDate = :date WHERE id = :id")
    suspend fun setMainTargetDate(id: Long, date: LocalDate?)
}
