package com.hitit.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.hitit.app.data.local.entity.BigPlayEntity
import com.hitit.app.data.local.entity.BigPlayWithCheckpoints
import com.hitit.app.data.local.entity.CheckpointEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface BigPlayDao {

    @Transaction
    @Query("SELECT * FROM big_plays ORDER BY isCompleted, sortOrder, id")
    fun observeAllWithCheckpoints(): Flow<List<BigPlayWithCheckpoints>>

    @Transaction
    @Query("SELECT * FROM big_plays WHERE id = :id")
    fun observeWithCheckpoints(id: Long): Flow<BigPlayWithCheckpoints?>

    @Query("SELECT * FROM big_plays WHERE id = :id")
    suspend fun getById(id: Long): BigPlayEntity?

    @Query("SELECT COUNT(*) FROM big_plays WHERE isCompleted = 1")
    fun observeCompletedCount(): Flow<Int>

    @Insert
    suspend fun insert(play: BigPlayEntity): Long

    @Update
    suspend fun update(play: BigPlayEntity)

    @Delete
    suspend fun delete(play: BigPlayEntity)

    @Insert
    suspend fun insertCheckpoint(checkpoint: CheckpointEntity): Long

    @Update
    suspend fun updateCheckpoint(checkpoint: CheckpointEntity)

    @Delete
    suspend fun deleteCheckpoint(checkpoint: CheckpointEntity)

    @Query("SELECT * FROM checkpoints WHERE id = :id")
    suspend fun getCheckpoint(id: Long): CheckpointEntity?
}
