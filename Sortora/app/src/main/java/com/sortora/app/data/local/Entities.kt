package com.sortora.app.data.local

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "sessions")
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val createdAt: Long,
    val status: String,
    val sourceLabel: String,
    val mode: String? = null,
    /** Analysed-item count the stored duplicate groups were computed from (-1 = never). */
    val duplicatesComputedFor: Int = -1,
)

/**
 * One file in a session. The analysis (labels, OCR keywords, …) is stored as JSON so the model
 * can evolve without schema migrations. It is deleted together with the session.
 */
@Entity(
    tableName = "session_items",
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sessionId")],
)
data class SessionItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val sourceUri: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModified: Long?,
    val kind: String,
    val pathHint: String?,
    val dateTaken: Long? = null,
    val parentUri: String? = null,
    val analysisStatus: String,
    val analysisJson: String? = null,
    /** Denormalised from the analysis so progress screens can count categories in SQL. */
    val category: String? = null,
    val errorMessage: String? = null,
    val customName: String? = null,
    val customFolder: String? = null,
    val customPattern: String? = null,
    val included: Boolean = true,
    val plannedName: String? = null,
    val plannedFolder: String? = null,
    val applyStatus: String? = null,
    val applyMessage: String? = null,
    /** Whole-image embedding, int8-quantised (see EmbeddingCodec). */
    val embedding: ByteArray? = null,
    /** SHA-256, only computed for files whose size matches another file's. */
    val contentHash: String? = null,
    val duplicateGroup: Long? = null,
    val duplicateExact: Boolean = false,
    val duplicateKeeper: Boolean = false,
)

/** Permanent history of original → new names. Never stores image content or OCR text. */
@Entity(tableName = "operation_log", indices = [Index("batchId")])
data class OperationLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val batchId: Long,
    val timestamp: Long,
    val mode: String,
    val originalName: String,
    val originalUri: String,
    val newName: String,
    val targetFolder: String,
    val targetUri: String?,
    val status: String,
    val message: String?,
    val sourceParentUri: String? = null,
    val sizeBytes: Long = -1,
    val undoStatus: String? = null,
    val undoMessage: String? = null,
)

/**
 * A face found in the current session. Embedding and thumbnail stay in app-private storage and
 * are deleted with the session.
 */
@Entity(
    tableName = "faces",
    foreignKeys = [
        ForeignKey(entity = SessionEntity::class, parentColumns = ["id"], childColumns = ["sessionId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index("sessionId"), Index("itemId")],
)
data class FaceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val itemId: Long,
    val embedding: ByteArray,
    val thumbnailPath: String,
    val personName: String? = null,
)

data class SessionWithCounts(
    @Embedded val session: SessionEntity,
    val total: Int,
    val analyzed: Int,
    val failed: Int,
)

data class CategoryCount(val category: String, val count: Int)

data class ItemProgressRow(
    val id: Long,
    val sourceUri: String,
    val kind: String,
    val analysisStatus: String,
    val category: String?,
    val applyStatus: String?,
    val plannedFolder: String?,
)

data class LogBatchRow(
    val batchId: Long,
    val timestamp: Long,
    val mode: String,
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    val undone: Int,
    val undoable: Int,
    val needsRestoreFolder: Boolean,
)

data class DuplicateCandidateRow(
    val id: Long,
    val sourceUri: String,
    val kind: String,
    val sizeBytes: Long,
    val contentHash: String?,
    val embedding: ByteArray?,
    val analysisJson: String?,
)

data class PersonRow(val itemId: Long, val personName: String)
