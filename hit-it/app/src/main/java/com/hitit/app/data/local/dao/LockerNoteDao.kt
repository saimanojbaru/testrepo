package com.hitit.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import com.hitit.app.data.local.entity.LockerNoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface LockerNoteDao {

    /** Children of [parentId]; pass null for the root level. */
    @Query(
        "SELECT * FROM locker_notes WHERE (:parentId IS NULL AND parentId IS NULL) " +
            "OR parentId = :parentId ORDER BY sortOrder, id",
    )
    fun observeChildren(parentId: Long?): Flow<List<LockerNoteEntity>>

    @Query("SELECT * FROM locker_notes WHERE id = :id")
    fun observeNote(id: Long): Flow<LockerNoteEntity?>

    @Query("SELECT * FROM locker_notes WHERE id = :id")
    suspend fun getNote(id: Long): LockerNoteEntity?

    @Insert
    suspend fun insert(note: LockerNoteEntity): Long

    @Update
    suspend fun update(note: LockerNoteEntity)

    @Delete
    suspend fun delete(note: LockerNoteEntity)
}
