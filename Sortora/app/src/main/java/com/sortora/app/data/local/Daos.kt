package com.sortora.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

private const val SESSION_WITH_COUNTS = """
    SELECT s.*,
        (SELECT COUNT(*) FROM session_items i WHERE i.sessionId = s.id) AS total,
        (SELECT COUNT(*) FROM session_items i WHERE i.sessionId = s.id AND i.analysisStatus = 'DONE') AS analyzed,
        (SELECT COUNT(*) FROM session_items i WHERE i.sessionId = s.id AND i.analysisStatus = 'FAILED') AS failed
    FROM sessions s
"""

@Dao
interface SessionDao {
    @Insert
    suspend fun insertSession(session: SessionEntity): Long

    @Insert
    suspend fun insertItems(items: List<SessionItemEntity>)

    @Query("$SESSION_WITH_COUNTS WHERE s.id = :id")
    fun observeSession(id: Long): Flow<SessionWithCounts?>

    @Query("$SESSION_WITH_COUNTS WHERE s.id = :id")
    suspend fun getSession(id: Long): SessionWithCounts?

    @Query("$SESSION_WITH_COUNTS WHERE s.status IN ('ANALYZING', 'ANALYZED', 'APPLYING') ORDER BY s.createdAt DESC LIMIT 1")
    fun observeResumable(): Flow<SessionWithCounts?>

    @Query("$SESSION_WITH_COUNTS WHERE s.status IN ('ANALYZING', 'ANALYZED', 'APPLYING')")
    suspend fun openSessions(): List<SessionWithCounts>

    @Query("SELECT * FROM session_items WHERE sessionId = :sessionId ORDER BY id")
    fun observeItems(sessionId: Long): Flow<List<SessionItemEntity>>

    @Query("SELECT * FROM session_items WHERE sessionId = :sessionId ORDER BY id")
    suspend fun getItems(sessionId: Long): List<SessionItemEntity>

    @Query("SELECT * FROM session_items WHERE sessionId = :sessionId AND analysisStatus = 'PENDING' ORDER BY id")
    suspend fun pendingItems(sessionId: Long): List<SessionItemEntity>

    @Query("UPDATE session_items SET analysisStatus = :status, analysisJson = :json, category = :category, errorMessage = :error WHERE id = :itemId")
    suspend fun updateAnalysis(itemId: Long, status: String, json: String?, category: String?, error: String?)

    @Query("SELECT category, COUNT(*) AS count FROM session_items WHERE sessionId = :sessionId AND category IS NOT NULL GROUP BY category")
    fun observeCategoryCounts(sessionId: Long): Flow<List<CategoryCount>>

    @Query("UPDATE sessions SET status = :status WHERE id = :sessionId")
    suspend fun setStatus(sessionId: Long, status: String)

    @Query("UPDATE sessions SET mode = :mode WHERE id = :sessionId")
    suspend fun setMode(sessionId: Long, mode: String)

    @Query("UPDATE session_items SET customName = :name, customFolder = :folder WHERE id = :itemId")
    suspend fun updateEdit(itemId: Long, name: String?, folder: String?)

    @Query("UPDATE session_items SET included = :included WHERE id = :itemId")
    suspend fun setIncluded(itemId: Long, included: Boolean)

    @Query("UPDATE session_items SET included = :included WHERE sessionId = :sessionId AND analysisStatus = 'DONE'")
    suspend fun setIncludedForAll(sessionId: Long, included: Boolean)

    @Query("UPDATE session_items SET plannedName = NULL, plannedFolder = NULL WHERE sessionId = :sessionId AND applyStatus IS NULL")
    suspend fun clearPlan(sessionId: Long)

    @Query("UPDATE session_items SET plannedName = :name, plannedFolder = :folder WHERE id = :itemId")
    suspend fun setPlan(itemId: Long, name: String, folder: String)

    @Transaction
    suspend fun freezePlan(sessionId: Long, mode: String, plan: Map<Long, Pair<String, String>>) {
        clearPlan(sessionId)
        plan.forEach { (itemId, target) -> setPlan(itemId, target.first, target.second) }
        setMode(sessionId, mode)
    }

    @Query("SELECT * FROM session_items WHERE sessionId = :sessionId AND plannedName IS NOT NULL AND applyStatus IS NULL ORDER BY id")
    suspend fun itemsToApply(sessionId: Long): List<SessionItemEntity>

    @Query("UPDATE session_items SET applyStatus = :status, applyMessage = :message WHERE id = :itemId")
    suspend fun markApplied(itemId: Long, status: String, message: String?)

    @Query("DELETE FROM sessions WHERE status IN ('APPLIED', 'DISCARDED')")
    suspend fun purgeFinished()
}

@Dao
interface LogDao {
    @Insert
    suspend fun insert(entry: OperationLogEntity)

    @Query(
        """
        SELECT batchId, MIN(timestamp) AS timestamp, mode, COUNT(*) AS total,
            SUM(CASE WHEN status IN ('SUCCESS', 'COPIED_ORIGINAL_KEPT') THEN 1 ELSE 0 END) AS succeeded,
            SUM(CASE WHEN status = 'FAILED' THEN 1 ELSE 0 END) AS failed
        FROM operation_log GROUP BY batchId ORDER BY timestamp DESC
        """,
    )
    fun observeBatches(): Flow<List<LogBatchRow>>

    @Query("SELECT * FROM operation_log WHERE batchId = :batchId ORDER BY id")
    fun observeEntries(batchId: Long): Flow<List<OperationLogEntity>>

    @Query("SELECT * FROM operation_log WHERE batchId = :batchId ORDER BY id")
    suspend fun entries(batchId: Long): List<OperationLogEntity>

    @Query("SELECT * FROM operation_log ORDER BY id")
    suspend fun all(): List<OperationLogEntity>

    @Query("DELETE FROM operation_log")
    suspend fun clear()
}
