package com.sortora.app.domain.repository

import com.sortora.app.domain.model.AnalysisOptions
import com.sortora.app.domain.model.AnalysisOutput
import com.sortora.app.domain.model.ApplyStatus
import com.sortora.app.domain.model.MediaFile
import com.sortora.app.domain.model.OperationMode
import kotlinx.coroutines.flow.Flow

/**
 * On-device media analysis. [openSession] loads the models once for a whole batch; close the
 * session when the batch ends so native model memory is released.
 */
interface MediaAnalyzer {
    fun openSession(options: AnalysisOptions): AnalysisSession
}

interface AnalysisSession : AutoCloseable {
    suspend fun analyze(file: MediaFile): AnalysisOutput
}

/** SHA-256 of a file's bytes, for exact-duplicate detection. */
interface ContentHasher {
    suspend fun sha256(uri: String): String?
}

/** Copies/moves files into the destination folder, renames in place, and undoes all three. */
interface FileOrganizer {
    /** Prepares a batch against the destination tree. Throws if access to it was lost. */
    suspend fun openBatch(destinationTreeUri: String): OrganizerBatch

    /** Batch rename: gives the file a new name where it is. */
    suspend fun renameInPlace(sourceUri: String, newName: String): TransferOutcome

    // Undo.
    /** Deletes a copy Sortora made, unless it changed since (size differs). */
    suspend fun deleteCopy(targetUri: String, expectedSize: Long): UndoResult
    /** Moves a file back: copies it into [parentUri] under [originalName], then deletes the moved one. */
    suspend fun restoreMoved(targetUri: String, originalName: String, parentUri: String, expectedSize: Long): UndoResult
    suspend fun renameBack(targetUri: String, originalName: String): UndoResult
}

data class UndoResult(val success: Boolean, val message: String? = null)

data class TransferRequest(
    val sourceUri: String,
    /** Folder containing the source, when known (recorded so moves can be undone in place). */
    val sourceParentUri: String? = null,
    val sourceName: String,
    val mimeType: String,
    val sizeBytes: Long,
    /** Relative folder path under the destination, e.g. "Pets/2025". */
    val targetFolder: String,
    val targetName: String,
)

data class TransferOutcome(
    val status: ApplyStatus,
    /** Name actually written (may differ from the request if the name was already taken). */
    val finalName: String?,
    val targetUri: String?,
    val message: String?,
)

interface OrganizerBatch {
    suspend fun transfer(request: TransferRequest, mode: OperationMode): TransferOutcome
    /** Writes a text file (the CSV log) at the destination root. Returns the name written. */
    suspend fun writeTextFile(name: String, mimeType: String, content: String): String?
}

enum class JobState { IDLE, QUEUED, RUNNING, SUCCEEDED, FAILED, CANCELLED }

data class JobProgress(val state: JobState, val currentFile: String? = null, val error: String? = null)

/** Writes text to a file the user chose with the system "Save as" dialog. */
interface TextFileWriter {
    suspend fun write(uri: String, content: String)
}

/** Background execution (WorkManager), so big batches survive leaving the app. */
interface BackgroundJobs {
    fun startAnalysis(sessionId: Long)
    fun cancelAnalysis(sessionId: Long)
    fun startApply(sessionId: Long)
    fun observeAnalysis(sessionId: Long): Flow<JobProgress>
    fun observeApply(sessionId: Long): Flow<JobProgress>
    /** [restoreTreeUri]: where to put moved files whose original folder is unknown. */
    fun startUndo(batchId: Long, restoreTreeUri: String?)
    fun observeUndo(batchId: Long): Flow<JobProgress>
}
