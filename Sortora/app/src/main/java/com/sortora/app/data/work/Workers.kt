package com.sortora.app.data.work

import android.content.Context
import android.os.SystemClock
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.sortora.app.R
import com.sortora.app.domain.model.SessionStatus
import com.sortora.app.domain.repository.SessionRepository
import com.sortora.app.domain.usecase.RunAnalysisUseCase
import com.sortora.app.domain.usecase.RunApplyUseCase
import com.sortora.app.domain.usecase.UndoBatchUseCase
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Base worker: runs as a foreground job (so large batches survive leaving the app) and throttles
 * notification/progress updates.
 */
abstract class SortoraWorker(
    context: Context,
    params: WorkerParameters,
    private val notifications: ProgressNotifications,
) : CoroutineWorker(context, params) {

    protected abstract val notificationId: Int
    protected abstract val titleRes: Int
    private var lastUpdate = 0L

    protected val sessionId: Long get() = inputData.getLong(KEY_SESSION_ID, -1)

    override suspend fun getForegroundInfo(): ForegroundInfo =
        notifications.foregroundInfo(id, notificationId, applicationContext.getString(titleRes), 0, 0, null)

    protected suspend fun startForeground() {
        try {
            setForeground(getForegroundInfo())
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Android 12+ may refuse to start a foreground service from the background, and some
            // devices throw other errors here. The work still runs; it just may be paused by the
            // system on very large batches.
            Log.w(TAG, "Running without foreground service", e)
        }
    }

    protected suspend fun reportProgress(done: Int, total: Int, current: String?) {
        setProgress(workDataOf(KEY_DONE to done, KEY_TOTAL to total, KEY_CURRENT to current))
        val now = SystemClock.elapsedRealtime()
        if (now - lastUpdate < UPDATE_INTERVAL_MS && done < total) return
        lastUpdate = now
        runCatching {
            setForeground(notifications.foregroundInfo(id, notificationId, applicationContext.getString(titleRes), done, total, current))
        }
    }

    companion object {
        const val KEY_SESSION_ID = "session_id"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_CURRENT = "current"
        private const val TAG = "SortoraWorker"
        private const val UPDATE_INTERVAL_MS = 750L
    }
}

@HiltWorker
class AnalyzeMediaWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    notifications: ProgressNotifications,
    private val runAnalysis: RunAnalysisUseCase,
    private val sessions: SessionRepository,
) : SortoraWorker(context, params, notifications) {

    override val notificationId = ProgressNotifications.ANALYSIS_NOTIFICATION_ID
    override val titleRes = R.string.notification_analyzing_title

    override suspend fun doWork(): Result {
        if (sessionId < 0) return Result.failure()
        startForeground()
        return try {
            runAnalysis(sessionId, ::reportProgress)
            Result.success()
        } catch (e: CancellationException) {
            // User cancelled: keep what was analysed so far so it can still be previewed.
            withContext(NonCancellable) { sessions.setStatus(sessionId, SessionStatus.ANALYZED) }
            throw e
        } catch (e: Exception) {
            Log.e("AnalyzeMediaWorker", "Analysis failed", e)
            if (runAttemptCount < MAX_ATTEMPTS) Result.retry() else Result.failure()
        }
    }

    private companion object {
        const val MAX_ATTEMPTS = 2
    }
}

@HiltWorker
class ApplyPlanWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    notifications: ProgressNotifications,
    private val runApply: RunApplyUseCase,
    private val sessions: SessionRepository,
) : SortoraWorker(context, params, notifications) {

    override val notificationId = ProgressNotifications.APPLY_NOTIFICATION_ID
    override val titleRes = R.string.notification_applying_title

    override suspend fun doWork(): Result {
        if (sessionId < 0) return Result.failure()
        startForeground()
        return try {
            runApply(sessionId, ::reportProgress)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("ApplyPlanWorker", "Apply failed", e)
            // Items already processed are marked; a retry continues with the rest.
            if (runAttemptCount < 1) {
                Result.retry()
            } else {
                sessions.setStatus(sessionId, SessionStatus.APPLIED)
                Result.failure(workDataOf(KEY_ERROR to (e.message ?: e.javaClass.simpleName)))
            }
        }
    }

    companion object {
        const val KEY_ERROR = "error"
    }
}

@HiltWorker
class UndoBatchWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    notifications: ProgressNotifications,
    private val undoBatch: UndoBatchUseCase,
) : SortoraWorker(context, params, notifications) {

    override val notificationId = ProgressNotifications.UNDO_NOTIFICATION_ID
    override val titleRes = R.string.notification_undo_title

    override suspend fun doWork(): Result {
        // For undo, the "session id" input is the history batch id (batches are keyed by session).
        if (sessionId < 0) return Result.failure()
        startForeground()
        return try {
            undoBatch(sessionId, inputData.getString(KEY_RESTORE_TREE), ::reportProgress)
            Result.success()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e("UndoBatchWorker", "Undo failed", e)
            Result.failure(workDataOf(ApplyPlanWorker.KEY_ERROR to (e.message ?: e.javaClass.simpleName)))
        }
    }

    companion object {
        const val KEY_RESTORE_TREE = "restore_tree"
    }
}
