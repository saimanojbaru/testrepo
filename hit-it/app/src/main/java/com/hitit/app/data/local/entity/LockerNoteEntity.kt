package com.hitit.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

/** A note in The Locker. Notes form a tree via [parentId]; a note with children acts as a folder. */
@Entity(
    tableName = "locker_notes",
    foreignKeys = [
        ForeignKey(
            entity = LockerNoteEntity::class,
            parentColumns = ["id"],
            childColumns = ["parentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["parentId"])],
)
data class LockerNoteEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val parentId: Long? = null,
    val title: String = "Untitled",
    val body: String = "",
    val sortOrder: Int = 0,
    val updatedAt: Instant = Instant.now(),
    val createdAt: Instant = Instant.now(),
)
