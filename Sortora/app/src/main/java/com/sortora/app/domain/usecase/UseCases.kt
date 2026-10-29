package com.sortora.app.domain.usecase

import com.sortora.app.domain.duplicates.DuplicateDetector
import com.sortora.app.domain.history.LogCsv
import com.sortora.app.domain.model.DuplicateHandling
import com.sortora.app.domain.model.UndoStatus
import com.sortora.app.domain.planning.SortPlanner
import com.sortora.app.domain.repository.ContentHasher
import com.sortora.app.domain.repository.FaceRepository
import com.sortora.app.domain.repository.UndoResult
import com.sortora.app.domain.model.AnalysisStatus
import com.sortora.app.domain.model.ApplyStatus
import com.sortora.app.domain.model.MediaFile
import com.sortora.app.domain.model.OperationLogEntry
import com.sortora.app.domain.model.OperationMode
import com.sortora.app.domain.model.SessionStatus
import com.sortora.app.domain.planning.PlannedFile
import com.sortora.app.domain.repository.FileOrganizer
import com.sortora.app.domain.repository.HistoryRepository
import com.sortora.app.domain.repository.BackgroundJobs
import com.sortora.app.domain.repository.MediaAnalyzer
import com.sortora.app.domain.repository.SessionRepository
import com.sortora.app.domain.repository.SettingsRepository
import com.sortora.app.domain.repository.TransferRequest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/** Creates a session for the picked files and starts background analysis. Returns the session id. */
class StartSortSessionUseCase @Inject constructor(
    private val sessions: SessionRepository,
    private val jobs: BackgroundJobs,
) {
    suspend operator fun invoke(sourceLabel: String, files: List<MediaFile>): Long {
        require(files.isNotEmpty()) { "Nothing to analyse" }
        // Only one open session at a time: starting over discards the previous preview.
        // A session that is mid-copy is left alone so its files are not half-applied.
        sessions.openSessions()
            .filter { it.status != SessionStatus.APPLYING }
            .forEach {
                jobs.cancelAnalysis(it.id)
                sessions.discard(it.id)
            }
        sessions.purgeFinishedSessions()
        val id = sessions.createSession(sourceLabel, files)
        jobs.startAnalysis(id)
        return id
    }
}

/**
 * Analyses every pending item of a session, one at a time. Results are saved as they are
 * produced, so an interrupted run resumes where it stopped.
 */
class RunAnalysisUseCase @Inject constructor(
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val analyzer: MediaAnalyzer,
    private val faces: FaceRepository,
    private val findDuplicates: FindDuplicatesUseCase,
) {
    suspend operator fun invoke(sessionId: Long, onProgress: suspend (done: Int, total: Int, current: String?) -> Unit) {
        val all = sessions.getItems(sessionId)
        val pending = all.filter { it.analysisStatus == AnalysisStatus.PENDING }
        var done = all.size - pending.size
        sessions.setStatus(sessionId, SessionStatus.ANALYZING)

        analyzer.openSession(settings.current().analysis).use { session ->
            for (item in pending) {
                currentCoroutineContext().ensureActive()
                onProgress(done, all.size, item.file.displayName)
                try {
                    val output = session.analyze(item.file)
                    sessions.saveAnalysis(item.id, output.result, output.imageEmbedding)
                    if (output.faces.isNotEmpty()) faces.saveFaces(sessionId, item.id, output.faces)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: SecurityException) {
                    sessions.markAnalysisFailed(item.id, "Access to this file was lost. Pick it again.")
                } catch (e: OutOfMemoryError) {
                    sessions.markAnalysisFailed(item.id, "File is too large to analyse on this device.")
                } catch (e: Exception) {
                    sessions.markAnalysisFailed(item.id, e.message ?: e.javaClass.simpleName)
                }
                done++
            }
        }
        onProgress(done, all.size, null)
        findDuplicates(sessionId)
        sessions.setStatus(sessionId, SessionStatus.ANALYZED)
    }
}

/**
 * Groups exact and near-duplicate files once analysis is done, then applies the user's default
 * handling (skip extras / separate folder / keep all) to newly found duplicates only, so choices
 * made in the preview are never overwritten. Cheap to call again: it only re-runs when more files
 * have been analysed since last time.
 */
class FindDuplicatesUseCase @Inject constructor(
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val hasher: ContentHasher,
) {
    suspend operator fun invoke(sessionId: Long) {
        val config = settings.current()
        if (!config.analysis.detectDuplicates) return
        val analyzed = sessions.getSession(sessionId)?.analyzedItems ?: return
        if (sessions.duplicatesComputedFor(sessionId) == analyzed) return

        val candidates = sessions.duplicateCandidates(sessionId)
        // Hash bytes only where it can matter: files that share their exact size with another.
        val sharedSizes = candidates.filter { it.sizeBytes > 0 }.groupingBy { it.sizeBytes }.eachCount().filterValues { it > 1 }.keys
        val hashed = candidates.map { c ->
            currentCoroutineContext().ensureActive()
            if (c.contentHash == null && c.sizeBytes in sharedSizes) {
                hasher.sha256(c.uri)?.let { hash ->
                    sessions.saveContentHash(c.itemId, hash)
                    c.copy(contentHash = hash)
                } ?: c
            } else {
                c
            }
        }
        val groups = DuplicateDetector.findGroups(hashed)
        val newlyGrouped = sessions.saveDuplicateGroups(sessionId, groups, analyzed)
        val extras = groups.flatMap { g -> g.members.filter { it != g.keeper } }.filter { it in newlyGrouped }
        if (extras.isEmpty()) return
        when (config.duplicateHandling) {
            DuplicateHandling.SKIP_EXTRAS -> sessions.setIncluded(extras, false)
            DuplicateHandling.SEPARATE_FOLDER -> sessions.setCustomFolder(extras, SortPlanner.DUPLICATES_FOLDER)
            DuplicateHandling.KEEP_ALL -> Unit
        }
    }
}

/** "Keep this one" on a duplicate: makes it the keeper and moves the handling to the others. */
class KeepDuplicateUseCase @Inject constructor(
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
) {
    suspend operator fun invoke(sessionId: Long, itemId: Long) {
        val items = sessions.getItems(sessionId)
        val groupId = items.firstOrNull { it.id == itemId }?.duplicate?.groupId ?: return
        val others = items.filter { it.duplicate?.groupId == groupId && it.id != itemId }.map { it.id }
        sessions.setDuplicateKeeper(itemId)
        when (settings.current().duplicateHandling) {
            DuplicateHandling.SKIP_EXTRAS -> {
                sessions.setIncluded(listOf(itemId), true)
                sessions.setIncluded(others, false)
            }
            DuplicateHandling.SEPARATE_FOLDER -> {
                sessions.setCustomFolder(listOf(itemId), null)
                sessions.setCustomFolder(others, SortPlanner.DUPLICATES_FOLDER)
            }
            DuplicateHandling.KEEP_ALL -> Unit
        }
    }
}

/** Freezes what the user previewed and starts the copy/move job. */
class ApplySortPlanUseCase @Inject constructor(
    private val sessions: SessionRepository,
    private val jobs: BackgroundJobs,
) {
    suspend operator fun invoke(sessionId: Long, mode: OperationMode, plan: Collection<PlannedFile>, includedIds: Set<Long>) {
        val frozen = plan.filter { it.itemId in includedIds }.associate { it.itemId to (it.name to it.folder) }
        require(frozen.isNotEmpty()) { "Nothing selected" }
        sessions.freezePlan(sessionId, mode, frozen)
        sessions.setStatus(sessionId, SessionStatus.APPLYING)
        jobs.startApply(sessionId)
    }
}

/**
 * Copies, moves or renames each frozen item and records every result in the history log.
 * Originals are deleted only in MOVE mode, and only after the copy is verified. RENAME mode
 * renames files where they are and needs no output folder.
 */
class RunApplyUseCase @Inject constructor(
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val organizer: FileOrganizer,
    private val history: HistoryRepository,
) {
    suspend operator fun invoke(sessionId: Long, onProgress: suspend (done: Int, total: Int, current: String?) -> Unit) {
        val session = sessions.getSession(sessionId) ?: error("Session $sessionId not found")
        val mode = session.mode ?: OperationMode.COPY
        val config = settings.current()
        val items = sessions.itemsToApply(sessionId)
        val batch = if (mode == OperationMode.RENAME) {
            null
        } else {
            organizer.openBatch(config.destinationUri ?: error("No output folder selected"))
        }

        items.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            onProgress(index, items.size, item.file.displayName)
            val name = item.plannedName ?: return@forEachIndexed
            val folder = item.plannedFolder.orEmpty()
            val outcome = if (batch == null) {
                organizer.renameInPlace(item.file.uri, name)
            } else {
                batch.transfer(
                    TransferRequest(
                        sourceUri = item.file.uri,
                        sourceParentUri = item.file.parentUri,
                        sourceName = item.file.displayName,
                        mimeType = item.file.mimeType,
                        sizeBytes = item.file.sizeBytes,
                        targetFolder = folder,
                        targetName = name,
                    ),
                    mode,
                )
            }
            sessions.markApplied(item.id, outcome.status, outcome.message)
            history.record(
                OperationLogEntry(
                    batchId = sessionId,
                    timestamp = System.currentTimeMillis(),
                    mode = mode,
                    originalName = item.file.displayName,
                    originalUri = item.file.uri,
                    newName = outcome.finalName ?: name,
                    targetFolder = folder,
                    targetUri = outcome.targetUri,
                    status = outcome.status,
                    message = outcome.message,
                    sourceParentUri = item.file.parentUri,
                    sizeBytes = item.file.sizeBytes,
                ),
            )
        }
        onProgress(items.size, items.size, null)

        if (batch != null && config.writeLogFile) {
            val entries = history.entries(sessionId)
            if (entries.isNotEmpty()) {
                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                runCatching { batch.writeTextFile("Sortora_log_$stamp.csv", "text/csv", LogCsv.format(entries)) }
            }
        }
        sessions.setStatus(sessionId, SessionStatus.APPLIED)
    }
}

/**
 * Undoes a whole batch, newest file first:
 *  - Copy: deletes the copies (skipping any that were changed since).
 *  - Move: puts each file back in its original folder under its original name, then removes the
 *    moved file. If the original folder isn't reachable, [restoreTreeUri] (picked by the user) is used.
 *  - Rename: renames each file back.
 * Every entry records whether its undo worked, so a partial undo can be retried.
 */
class UndoBatchUseCase @Inject constructor(
    private val history: HistoryRepository,
    private val organizer: FileOrganizer,
) {
    suspend operator fun invoke(batchId: Long, restoreTreeUri: String?, onProgress: suspend (done: Int, total: Int, current: String?) -> Unit) {
        val entries = history.entries(batchId).filter { it.undoable }.sortedByDescending { it.id }
        entries.forEachIndexed { index, entry ->
            currentCoroutineContext().ensureActive()
            onProgress(index, entries.size, entry.newName)
            val target = entry.targetUri ?: return@forEachIndexed
            val result = try {
                when {
                    entry.mode == OperationMode.RENAME -> organizer.renameBack(target, entry.originalName)
                    // Copies, and moves whose original was never deleted: just remove the copy.
                    entry.mode == OperationMode.COPY || entry.status == ApplyStatus.COPIED_ORIGINAL_KEPT ->
                        organizer.deleteCopy(target, entry.sizeBytes)
                    else -> {
                        val parent = entry.sourceParentUri ?: restoreTreeUri
                        if (parent == null) {
                            UndoResult(false, "Original folder unknown. Undo again and choose a folder to restore into.")
                        } else {
                            organizer.restoreMoved(target, entry.originalName, parent, entry.sizeBytes)
                        }
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UndoResult(false, e.message ?: e.javaClass.simpleName)
            }
            history.markUndo(entry.id, if (result.success) UndoStatus.UNDONE else UndoStatus.FAILED, result.message)
        }
        onProgress(entries.size, entries.size, null)
    }
}

/** Counts used by the result screen. */
data class ApplySummary(val succeeded: Int, val keptOriginal: Int, val failed: Int) {
    companion object {
        fun of(statuses: List<ApplyStatus?>) = ApplySummary(
            succeeded = statuses.count { it == ApplyStatus.SUCCESS },
            keptOriginal = statuses.count { it == ApplyStatus.COPIED_ORIGINAL_KEPT },
            failed = statuses.count { it == ApplyStatus.FAILED },
        )
    }
}

/** Stores the output folder the user picked, keeping a long-lived write grant for it. */
class SetDestinationUseCase @Inject constructor(
    private val sources: com.sortora.app.domain.repository.MediaSourceRepository,
    private val settings: SettingsRepository,
) {
    /** Returns false if the folder can't be written to (e.g. a read-only provider). */
    suspend operator fun invoke(treeUri: String): Boolean {
        if (!sources.persistTreeAccess(treeUri) || !sources.hasTreeAccess(treeUri, needWrite = true)) return false
        val previous = settings.current().destinationUri
        if (previous != null && previous != treeUri) sources.releaseTreeAccess(previous)
        settings.setDestination(treeUri, sources.treeDisplayName(treeUri))
        return true
    }
}
