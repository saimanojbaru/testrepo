package com.sortora.app.data.repository

import com.sortora.app.data.local.LogDao
import com.sortora.app.data.local.OperationLogEntity
import com.sortora.app.data.local.SessionDao
import com.sortora.app.data.local.SessionItemEntity
import com.sortora.app.data.local.SessionWithCounts
import com.sortora.app.domain.model.AnalysisResult
import com.sortora.app.domain.model.AnalysisStatus
import com.sortora.app.domain.model.ApplyStatus
import com.sortora.app.data.local.EmbeddingCodec
import com.sortora.app.domain.duplicates.DuplicateCandidate
import com.sortora.app.domain.duplicates.DuplicateGroup
import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.DuplicateInfo
import com.sortora.app.domain.model.UndoStatus
import com.sortora.app.domain.model.LogBatch
import com.sortora.app.domain.model.MediaFile
import com.sortora.app.domain.model.ItemProgress
import com.sortora.app.domain.model.MediaKind
import com.sortora.app.domain.model.OperationLogEntry
import com.sortora.app.domain.model.OperationMode
import com.sortora.app.domain.model.SessionItem
import com.sortora.app.domain.model.SessionStatus
import com.sortora.app.domain.model.SortSession
import com.sortora.app.domain.repository.HistoryRepository
import com.sortora.app.domain.repository.SessionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomSessionRepository @Inject constructor(
    private val dao: SessionDao,
    private val json: Json,
    private val thumbnails: FaceThumbnailStore,
) : SessionRepository {

    private companion object {
        /** Keeps "IN (...)" lists well under SQLite's variable limit. */
        const val SQL_CHUNK = 500
    }

    override suspend fun createSession(sourceLabel: String, files: List<MediaFile>): Long {
        val id = dao.insertSession(
            com.sortora.app.data.local.SessionEntity(
                createdAt = System.currentTimeMillis(),
                status = SessionStatus.ANALYZING.name,
                sourceLabel = sourceLabel,
            ),
        )
        dao.insertItems(
            files.map { file ->
                SessionItemEntity(
                    sessionId = id,
                    sourceUri = file.uri,
                    displayName = file.displayName,
                    mimeType = file.mimeType,
                    sizeBytes = file.sizeBytes,
                    lastModified = file.lastModifiedMillis,
                    kind = file.kind.name,
                    pathHint = file.pathHint,
                    dateTaken = file.dateTakenMillis,
                    parentUri = file.parentUri,
                    analysisStatus = AnalysisStatus.PENDING.name,
                )
            },
        )
        return id
    }

    override fun observeSession(sessionId: Long): Flow<SortSession?> = dao.observeSession(sessionId).map { it?.toDomain() }

    override fun observeItems(sessionId: Long): Flow<List<SessionItem>> =
        dao.observeItems(sessionId).map { items -> items.map { it.toDomain() } }.flowOn(Dispatchers.Default)

    override fun observeResumableSession(): Flow<SortSession?> = dao.observeResumable().map { it?.toDomain() }

    override suspend fun getSession(sessionId: Long): SortSession? = dao.getSession(sessionId)?.toDomain()

    override suspend fun openSessions(): List<SortSession> = dao.openSessions().map { it.toDomain() }

    override suspend fun getItems(sessionId: Long): List<SessionItem> = dao.getItems(sessionId).map { it.toDomain() }

    override suspend fun pendingItems(sessionId: Long): List<SessionItem> = dao.pendingItems(sessionId).map { it.toDomain() }

    override suspend fun saveAnalysis(itemId: Long, result: AnalysisResult, imageEmbedding: FloatArray?) =
        dao.updateAnalysis(
            itemId, AnalysisStatus.DONE.name, json.encodeToString(AnalysisResult.serializer(), result), result.category.name, null,
            imageEmbedding?.let(EmbeddingCodec::encode),
        )

    override suspend fun markAnalysisFailed(itemId: Long, error: String) =
        dao.updateAnalysis(itemId, AnalysisStatus.FAILED.name, null, null, error, null)

    override fun observeCategoryCounts(sessionId: Long): Flow<Map<ContentCategory, Int>> =
        dao.observeCategoryCounts(sessionId).map { rows ->
            rows.associate { enumOrDefault(it.category, ContentCategory.OTHER) to it.count }
        }

    override fun observeItemProgress(sessionId: Long): Flow<List<ItemProgress>> =
        dao.observeItemProgress(sessionId).map { rows ->
            rows.map { row ->
                ItemProgress(
                    id = row.id,
                    uri = row.sourceUri,
                    kind = enumOrDefault(row.kind, MediaKind.IMAGE),
                    analysisStatus = enumOrDefault(row.analysisStatus, AnalysisStatus.PENDING),
                    category = row.category?.let { enumOrDefault(it, ContentCategory.OTHER) },
                    applyStatus = row.applyStatus?.let { enumOrDefault(it, ApplyStatus.FAILED) },
                    plannedFolder = row.plannedFolder,
                )
            }
        }

    override suspend fun setStatus(sessionId: Long, status: SessionStatus) = dao.setStatus(sessionId, status.name)

    override suspend fun updateEdit(itemId: Long, customName: String?, customFolder: String?) =
        dao.updateEdit(itemId, customName?.takeIf { it.isNotBlank() }, customFolder?.takeIf { it.isNotBlank() })

    override suspend fun setIncluded(itemId: Long, included: Boolean) = dao.setIncluded(itemId, included)

    override suspend fun setIncludedForAll(sessionId: Long, included: Boolean) = dao.setIncludedForAll(sessionId, included)

    override suspend fun setIncluded(itemIds: Collection<Long>, included: Boolean) =
        itemIds.chunked(SQL_CHUNK).forEach { dao.setIncluded(it, included) }

    override suspend fun setCustomFolder(itemIds: Collection<Long>, folder: String?) =
        itemIds.chunked(SQL_CHUNK).forEach { dao.setCustomFolder(it, folder?.takeIf { f -> f.isNotBlank() }) }

    override suspend fun setCustomPattern(itemIds: Collection<Long>, pattern: String?) =
        itemIds.chunked(SQL_CHUNK).forEach { dao.setCustomPattern(it, pattern?.takeIf { p -> p.isNotBlank() }) }

    override suspend fun resetEdits(itemIds: Collection<Long>) = itemIds.chunked(SQL_CHUNK).forEach { dao.resetEdits(it) }

    override suspend fun duplicateCandidates(sessionId: Long): List<DuplicateCandidate> =
        dao.duplicateCandidates(sessionId).map { row ->
            val analysis = row.analysisJson?.let { decodeAnalysis(row.id, it) }
            DuplicateCandidate(
                itemId = row.id,
                uri = row.sourceUri,
                kind = enumOrDefault(row.kind, MediaKind.IMAGE),
                sizeBytes = row.sizeBytes,
                contentHash = row.contentHash,
                perceptualHash = analysis?.perceptualHash,
                embedding = row.embedding?.let(EmbeddingCodec::decode),
                width = analysis?.metadata?.width,
                height = analysis?.metadata?.height,
                captureTimeMillis = analysis?.metadata?.captureTimeMillis,
            )
        }

    override suspend fun saveContentHash(itemId: Long, hash: String) = dao.saveContentHash(itemId, hash)

    override suspend fun saveDuplicateGroups(sessionId: Long, groups: List<DuplicateGroup>, analyzedCount: Int): Set<Long> {
        val before = dao.groupedItemIds(sessionId).toSet()
        dao.replaceDuplicateGroups(sessionId, groups.map { Triple(it.members, it.exact, it.keeper) }, analyzedCount)
        return groups.flatMap { it.members }.filterNot { it in before }.toSet()
    }

    override suspend fun setDuplicateKeeper(itemId: Long) {
        val group = dao.duplicateGroupOf(itemId) ?: return
        dao.setKeeper(itemId, group)
    }

    override suspend fun duplicatesComputedFor(sessionId: Long): Int = dao.duplicatesComputedFor(sessionId) ?: -1

    override suspend fun freezePlan(sessionId: Long, mode: OperationMode, plan: Map<Long, Pair<String, String>>) =
        dao.freezePlan(sessionId, mode.name, plan)

    override suspend fun itemsToApply(sessionId: Long): List<SessionItem> = dao.itemsToApply(sessionId).map { it.toDomain() }

    override suspend fun markApplied(itemId: Long, status: ApplyStatus, message: String?) =
        dao.markApplied(itemId, status.name, message)

    override suspend fun discard(sessionId: Long) = dao.setStatus(sessionId, SessionStatus.DISCARDED.name)

    override suspend fun purgeFinishedSessions() {
        dao.purgeFinished()
        decoded.clear()
        // Face rows were removed by the cascade; drop their thumbnail files too.
        val live = dao.openSessions().map { it.session.id }.toSet()
        withContext(Dispatchers.IO) { thumbnails.retainOnly(live) }
    }

    private fun SessionWithCounts.toDomain() = SortSession(
        id = session.id,
        createdAt = session.createdAt,
        status = enumOrDefault(session.status, SessionStatus.DISCARDED),
        sourceLabel = session.sourceLabel,
        mode = session.mode?.let { enumOrDefault(it, OperationMode.COPY) },
        totalItems = total,
        analyzedItems = analyzed,
        failedItems = failed,
    )

    private fun SessionItemEntity.toDomain() = SessionItem(
        id = id,
        sessionId = sessionId,
        file = MediaFile(
            uri = sourceUri,
            displayName = displayName,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            lastModifiedMillis = lastModified,
            kind = enumOrDefault(kind, MediaKind.IMAGE),
            pathHint = pathHint,
            dateTakenMillis = dateTaken,
            parentUri = parentUri,
        ),
        analysisStatus = enumOrDefault(analysisStatus, AnalysisStatus.PENDING),
        analysis = analysisJson?.let { decodeAnalysis(id, it) },
        error = errorMessage,
        customName = customName,
        customFolder = customFolder,
        customPattern = customPattern,
        included = included,
        plannedName = plannedName,
        plannedFolder = plannedFolder,
        applyStatus = applyStatus?.let { enumOrDefault(it, ApplyStatus.FAILED) },
        applyMessage = applyMessage,
        duplicate = duplicateGroup?.let { DuplicateInfo(it, duplicateExact, duplicateKeeper) },
    )

    /**
     * Item lists are re-emitted on every edit; an item's analysis JSON never changes once
     * written, so decode each one only once.
     */
    private val decoded = ConcurrentHashMap<Long, Pair<String, AnalysisResult>>()

    private fun decodeAnalysis(itemId: Long, raw: String): AnalysisResult? {
        decoded[itemId]?.let { (cachedRaw, result) -> if (cachedRaw == raw) return result }
        return try {
            json.decodeFromString(AnalysisResult.serializer(), raw).also { decoded[itemId] = raw to it }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }
}

@Singleton
class RoomHistoryRepository @Inject constructor(private val dao: LogDao) : HistoryRepository {

    override fun observeBatches(): Flow<List<LogBatch>> = dao.observeBatches().map { rows ->
        rows.map {
            LogBatch(
                it.batchId, it.timestamp, enumOrDefault(it.mode, OperationMode.COPY), it.total, it.succeeded, it.failed,
                it.undone, it.undoable, it.needsRestoreFolder,
            )
        }
    }

    override fun observeEntries(batchId: Long): Flow<List<OperationLogEntry>> =
        dao.observeEntries(batchId).map { rows -> rows.map { it.toDomain() } }

    override suspend fun entries(batchId: Long): List<OperationLogEntry> = dao.entries(batchId).map { it.toDomain() }

    override suspend fun allEntries(): List<OperationLogEntry> = dao.all().map { it.toDomain() }

    override suspend fun record(entry: OperationLogEntry) = dao.insert(
        OperationLogEntity(
            batchId = entry.batchId,
            timestamp = entry.timestamp,
            mode = entry.mode.name,
            originalName = entry.originalName,
            originalUri = entry.originalUri,
            newName = entry.newName,
            targetFolder = entry.targetFolder,
            targetUri = entry.targetUri,
            status = entry.status.name,
            message = entry.message,
            sourceParentUri = entry.sourceParentUri,
            sizeBytes = entry.sizeBytes,
        ),
    )

    override suspend fun markUndo(entryId: Long, status: UndoStatus, message: String?) = dao.markUndo(entryId, status.name, message)

    override suspend fun clear() = dao.clear()

    private fun OperationLogEntity.toDomain() = OperationLogEntry(
        id = id,
        batchId = batchId,
        timestamp = timestamp,
        mode = enumOrDefault(mode, OperationMode.COPY),
        originalName = originalName,
        originalUri = originalUri,
        newName = newName,
        targetFolder = targetFolder,
        targetUri = targetUri,
        status = enumOrDefault(status, ApplyStatus.FAILED),
        message = message,
        sourceParentUri = sourceParentUri,
        sizeBytes = sizeBytes,
        undoStatus = undoStatus?.let { enumOrDefault(it, UndoStatus.FAILED) },
        undoMessage = undoMessage,
    )
}

/** Tolerates enum values written by a newer/older app version. */
internal inline fun <reified E : Enum<E>> enumOrDefault(name: String, default: E): E =
    enumValues<E>().firstOrNull { it.name == name } ?: default
