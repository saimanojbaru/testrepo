package com.sortora.app.domain.model

/** Lifecycle of one "sort" run: pick → analyse → preview → apply. */
enum class SessionStatus { ANALYZING, ANALYZED, APPLYING, APPLIED, DISCARDED }

enum class AnalysisStatus { PENDING, DONE, FAILED }

/** Result of copying/moving a single file. */
enum class ApplyStatus {
    SUCCESS,
    /** Move was requested and the copy succeeded, but the original could not be deleted. */
    COPIED_ORIGINAL_KEPT,
    FAILED,
    SKIPPED,
}

data class SortSession(
    val id: Long,
    val createdAt: Long,
    val status: SessionStatus,
    val sourceLabel: String,
    val mode: OperationMode?,
    val totalItems: Int,
    val analyzedItems: Int,
    val failedItems: Int,
)

/** One file inside a session, with its analysis and any edits the user made in the preview. */
data class SessionItem(
    val id: Long,
    val sessionId: Long,
    val file: MediaFile,
    val analysisStatus: AnalysisStatus,
    val analysis: AnalysisResult?,
    val error: String?,
    /** User overrides from the preview screen; null means "use the suggestion". */
    val customName: String?,
    val customFolder: String?,
    val included: Boolean,
    /** Frozen when the user taps Apply, so what was previewed is exactly what gets applied. */
    val plannedName: String?,
    val plannedFolder: String?,
    val applyStatus: ApplyStatus?,
    val applyMessage: String?,
)

/** One line of the permanent log of original → new file names. */
data class OperationLogEntry(
    val id: Long = 0,
    val batchId: Long,
    val timestamp: Long,
    val mode: OperationMode,
    val originalName: String,
    val originalUri: String,
    val newName: String,
    val targetFolder: String,
    val targetUri: String?,
    val status: ApplyStatus,
    val message: String?,
)

/** Summary row for the history screen. */
data class LogBatch(
    val batchId: Long,
    val timestamp: Long,
    val mode: OperationMode,
    val total: Int,
    val succeeded: Int,
    val failed: Int,
)
