package com.hitit.app.data.repository

import com.hitit.app.data.local.dao.LockerNoteDao
import com.hitit.app.data.local.entity.LockerNoteEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/** Source of truth for The Locker (hierarchical notes). Notes aren't gamified — no Momentum. */
@Singleton
class LockerRepository @Inject constructor(
    private val noteDao: LockerNoteDao,
) {
    fun observeChildren(parentId: Long?): Flow<List<LockerNoteEntity>> = noteDao.observeChildren(parentId)
    fun observeNote(id: Long): Flow<LockerNoteEntity?> = noteDao.observeNote(id)

    suspend fun getNote(id: Long): LockerNoteEntity? = noteDao.getNote(id)

    suspend fun createNote(parentId: Long?, title: String = "Untitled"): Long =
        noteDao.insert(LockerNoteEntity(parentId = parentId, title = title))

    suspend fun saveNote(note: LockerNoteEntity) = noteDao.update(note.copy(updatedAt = Instant.now()))

    suspend fun deleteNote(note: LockerNoteEntity) = noteDao.delete(note)
}
