package com.nonverbal.mastery.data.db

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface ProgressDao {
    @Query("SELECT * FROM progress")
    fun observeAll(): Flow<List<LevelProgress>>

    @Query("SELECT * FROM progress")
    suspend fun getAll(): List<LevelProgress>

    @Query("SELECT * FROM progress WHERE levelId = :levelId")
    suspend fun get(levelId: String): LevelProgress?

    @Upsert
    suspend fun upsert(progress: LevelProgress)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<LevelProgress>)

    @Query("DELETE FROM progress")
    suspend fun clear()
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM review_log ORDER BY reviewedAt DESC")
    fun observeAll(): Flow<List<ReviewLog>>

    @Query("SELECT * FROM review_log ORDER BY reviewedAt ASC")
    suspend fun getAll(): List<ReviewLog>

    @Insert
    suspend fun insert(log: ReviewLog)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<ReviewLog>)

    @Query("DELETE FROM review_log")
    suspend fun clear()
}

@Dao
interface PoseBookmarkDao {
    @Query("SELECT * FROM pose_bookmarks ORDER BY savedAt DESC")
    fun observeAll(): Flow<List<PoseBookmark>>

    @Query("SELECT * FROM pose_bookmarks")
    suspend fun getAll(): List<PoseBookmark>

    @Upsert
    suspend fun upsert(bookmark: PoseBookmark)

    @Query("DELETE FROM pose_bookmarks WHERE poseId = :poseId")
    suspend fun delete(poseId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<PoseBookmark>)

    @Query("DELETE FROM pose_bookmarks")
    suspend fun clear()
}

@Database(
    entities = [LevelProgress::class, PoseBookmark::class, ReviewLog::class],
    version = 1,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun progressDao(): ProgressDao
    abstract fun reviewDao(): ReviewDao
    abstract fun poseBookmarkDao(): PoseBookmarkDao

    companion object {
        fun create(context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "nonverbal-mastery.db").build()
    }
}
