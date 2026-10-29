package com.sortora.app.domain.repository

import com.sortora.app.domain.model.AnalysisOptions
import com.sortora.app.domain.model.AnalysisResult
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
    suspend fun analyze(file: MediaFile): AnalysisResult
}

/** Copies/moves files into the destination folder. */
interface FileOrganizer {
    /** Prepares a batch against the destination tree. Throws if access to it was lost. */
    suspend fun openBatch(destinationTreeUri: String): OrganizerBatch
}

data class TransferRequest(
    val sourceUri: String,
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
}
