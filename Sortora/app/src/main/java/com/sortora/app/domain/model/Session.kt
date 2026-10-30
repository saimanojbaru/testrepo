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

/** Membership of a duplicate group (computed after analysis). */
data class DuplicateInfo(
    val groupId: Long,
    /** Identical bytes, as opposed to visually similar. */
    val exact: Boolean,
    /** The copy Sortora suggests keeping. */
    val keeper: Boolean,
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
    /** Batch-rename pattern for this file (e.g. "Bali_{counter}"); overrides the global template. */
    val customPattern: String? = null,
    val included: Boolean,
    /** Frozen when the user taps Apply, so what was previewed is exactly what gets applied. */
    val plannedName: String?,
    val plannedFolder: String?,
    val applyStatus: ApplyStatus?,
    val applyMessage: String?,
    val duplicate: DuplicateInfo? = null,
)

/**
 * Just enough about one file to animate it on the progress screens: where it is, whether it has
 * been analysed (and into which category) and whether it has been copied/moved yet.
 */
data class ItemProgress(
    val id: Long,
    val uri: String,
    val kind: MediaKind,
    val analysisStatus: AnalysisStatus,
    val category: ContentCategory?,
    val applyStatus: ApplyStatus?,
    val plannedFolder: String?,
)

enum class UndoStatus { UNDONE, FAILED, SKIPPED }

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
    /** Folder the original lived in, when known; needed to undo a move in place. */
    val sourceParentUri: String? = null,
    val sizeBytes: Long = -1,
    val undoStatus: UndoStatus? = null,
    val undoMessage: String? = null,
) {
    /** Something Undo can act on: a file was written or renamed and hasn't been undone yet. */
    val undoable: Boolean
        get() = undoStatus != UndoStatus.UNDONE && targetUri != null &&
            (status == ApplyStatus.SUCCESS || status == ApplyStatus.COPIED_ORIGINAL_KEPT)

    /** A moved file whose original folder Sortora can't reach: Undo needs a folder from the user. */
    val needsRestoreFolder: Boolean
        get() = undoable && mode == OperationMode.MOVE && status == ApplyStatus.SUCCESS && sourceParentUri == null
}

/** A face stored for the current session. */
class FaceRecord(
    val id: Long,
    val itemId: Long,
    val embedding: FloatArray,
    val thumbnailPath: String,
    val personName: String?,
)

/** Summary row for the history screen. */
data class LogBatch(
    val batchId: Long,
    val timestamp: Long,
    val mode: OperationMode,
    val total: Int,
    val succeeded: Int,
    val failed: Int,
    val undone: Int = 0,
    val undoable: Int = 0,
    val needsRestoreFolder: Boolean = false,
)
