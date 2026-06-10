package com.hitit.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hitit.app.data.local.entity.YogaPoseEntity
import com.hitit.app.data.local.entity.YogaSessionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface YogaPoseDao {
    @Insert
    suspend fun insert(pose: YogaPoseEntity): Long

    @Query("SELECT * FROM yoga_poses ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<YogaPoseEntity>>

    @Query("SELECT * FROM yoga_poses WHERE id = :id")
    suspend fun getById(id: Long): YogaPoseEntity?

    @Query("DELETE FROM yoga_poses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM yoga_poses")
    suspend fun deleteAll()
}

@Dao
interface YogaSessionDao {
    @Insert
    suspend fun insert(session: YogaSessionEntity): Long

    @Query("SELECT * FROM yoga_sessions ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 20): Flow<List<YogaSessionEntity>>

    @Query("DELETE FROM yoga_sessions WHERE poseId = :poseId")
    suspend fun deleteForPose(poseId: Long)

    @Query("DELETE FROM yoga_sessions")
    suspend fun deleteAll()
}
