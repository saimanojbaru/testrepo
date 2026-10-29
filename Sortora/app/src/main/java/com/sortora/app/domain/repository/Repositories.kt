package com.sortora.app.domain.repository

import com.sortora.app.domain.model.AnalysisOptions
import com.sortora.app.domain.model.AnalysisResult
import com.sortora.app.domain.model.AppSettings
import com.sortora.app.domain.model.ApplyStatus
import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.LogBatch
import com.sortora.app.domain.model.MediaFile
import com.sortora.app.domain.model.NamingSettings
import com.sortora.app.domain.model.OperationLogEntry
import com.sortora.app.domain.model.OperationMode
import com.sortora.app.domain.model.SessionItem
import com.sortora.app.domain.model.SessionStatus
import com.sortora.app.domain.model.SortSession
import com.sortora.app.domain.model.ThemeMode
import com.sortora.app.domain.model.UserCategory
import kotlinx.coroutines.flow.Flow

/** User preferences (DataStore). */
interface SettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun current(): AppSettings
    suspend fun setOnboardingComplete(complete: Boolean)
    suspend fun setThemeMode(mode: ThemeMode)
    suspend fun setDynamicColor(enabled: Boolean)
    suspend fun setDefaultMode(mode: OperationMode)
    suspend fun setDestination(uri: String?, label: String?)
    suspend fun updateNaming(transform: (NamingSettings) -> NamingSettings)
    suspend fun setFolderName(category: ContentCategory, name: String?)
    suspend fun resetFolderNames()
    suspend fun upsertCustomCategory(category: UserCategory)
    suspend fun deleteCustomCategory(id: String)
    suspend fun moveCustomCategory(id: String, up: Boolean)
    suspend fun setGroupByYear(enabled: Boolean)
    suspend fun setWriteLogFile(enabled: Boolean)
    suspend fun updateAnalysis(transform: (AnalysisOptions) -> AnalysisOptions)
}

/** Sort sessions and their items (Room). */
interface SessionRepository {
    suspend fun createSession(sourceLabel: String, files: List<MediaFile>): Long
    fun observeSession(sessionId: Long): Flow<SortSession?>
    fun observeItems(sessionId: Long): Flow<List<SessionItem>>
    fun observeCategoryCounts(sessionId: Long): Flow<Map<ContentCategory, Int>>
    /** The most recent session that was analysed but not yet applied or discarded. */
    fun observeResumableSession(): Flow<SortSession?>
    suspend fun getSession(sessionId: Long): SortSession?
    /** Sessions still being analysed, previewed or applied. */
    suspend fun openSessions(): List<SortSession>
    suspend fun getItems(sessionId: Long): List<SessionItem>
    suspend fun pendingItems(sessionId: Long): List<SessionItem>
    suspend fun saveAnalysis(itemId: Long, result: AnalysisResult)
    suspend fun markAnalysisFailed(itemId: Long, error: String)
    suspend fun setStatus(sessionId: Long, status: SessionStatus)
    suspend fun updateEdit(itemId: Long, customName: String?, customFolder: String?)
    suspend fun setIncluded(itemId: Long, included: Boolean)
    suspend fun setIncludedForAll(sessionId: Long, included: Boolean)
    /** Freezes the previewed plan: itemId → (name, folder). Items not in [plan] are excluded. */
    suspend fun freezePlan(sessionId: Long, mode: OperationMode, plan: Map<Long, Pair<String, String>>)
    suspend fun itemsToApply(sessionId: Long): List<SessionItem>
    suspend fun markApplied(itemId: Long, status: ApplyStatus, message: String?)
    suspend fun discard(sessionId: Long)
    /** Deletes finished/discarded sessions (and their cached OCR text). */
    suspend fun purgeFinishedSessions()
}

/** Permanent log of original → new names. */
interface HistoryRepository {
    fun observeBatches(): Flow<List<LogBatch>>
    fun observeEntries(batchId: Long): Flow<List<OperationLogEntry>>
    suspend fun entries(batchId: Long): List<OperationLogEntry>
    suspend fun allEntries(): List<OperationLogEntry>
    suspend fun record(entry: OperationLogEntry)
    suspend fun clear()
}

/** Turns picked documents / folders into [MediaFile]s (Storage Access Framework). */
interface MediaSourceRepository {
    suspend fun resolveFiles(uris: List<String>): List<MediaFile>
    suspend fun scanFolder(treeUri: String, includeSubfolders: Boolean, excludeTreeUri: String?): List<MediaFile>
    fun persistTreeAccess(treeUri: String): Boolean
    fun hasTreeAccess(treeUri: String, needWrite: Boolean): Boolean
    fun releaseTreeAccess(treeUri: String)
    fun treeDisplayName(treeUri: String): String?
}
