package com.sortora.app.domain.usecase

import com.sortora.app.domain.history.LogCsv
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
                    sessions.saveAnalysis(item.id, session.analyze(item.file))
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
        sessions.setStatus(sessionId, SessionStatus.ANALYZED)
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
 * Copies or moves each frozen item into the destination and records every result in the
 * history log. Originals are deleted only in MOVE mode, and only after the copy is verified.
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
        val destination = config.destinationUri ?: error("No output folder selected")
        val items = sessions.itemsToApply(sessionId)
        val batch = organizer.openBatch(destination)

        items.forEachIndexed { index, item ->
            currentCoroutineContext().ensureActive()
            onProgress(index, items.size, item.file.displayName)
            val name = item.plannedName ?: return@forEachIndexed
            val folder = item.plannedFolder.orEmpty()
            val outcome = batch.transfer(
                TransferRequest(item.file.uri, item.file.displayName, item.file.mimeType, item.file.sizeBytes, folder, name),
                mode,
            )
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
                ),
            )
        }
        onProgress(items.size, items.size, null)

        if (config.writeLogFile) {
            val entries = history.entries(sessionId)
            if (entries.isNotEmpty()) {
                val stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                runCatching { batch.writeTextFile("Sortora_log_$stamp.csv", "text/csv", LogCsv.format(entries)) }
            }
        }
        sessions.setStatus(sessionId, SessionStatus.APPLIED)
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
