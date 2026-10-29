package com.sortora.app.data.work

import android.content.Context
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.sortora.app.domain.repository.BackgroundJobs
import com.sortora.app.domain.repository.JobProgress
import com.sortora.app.domain.repository.JobState
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Runs analysis and apply jobs with WorkManager. No network constraints are ever set: every job
 * runs fully offline.
 */
@Singleton
class WorkManagerBackgroundJobs @Inject constructor(
    @ApplicationContext private val context: Context,
) : BackgroundJobs {

    private val workManager get() = WorkManager.getInstance(context)

    override fun startAnalysis(sessionId: Long) {
        val request = OneTimeWorkRequestBuilder<AnalyzeMediaWorker>()
            .setInputData(workDataOf(SortoraWorker.KEY_SESSION_ID to sessionId))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(analysisName(sessionId), ExistingWorkPolicy.KEEP, request)
    }

    override fun cancelAnalysis(sessionId: Long) {
        workManager.cancelUniqueWork(analysisName(sessionId))
    }

    override fun startApply(sessionId: Long) {
        val request = OneTimeWorkRequestBuilder<ApplyPlanWorker>()
            .setInputData(workDataOf(SortoraWorker.KEY_SESSION_ID to sessionId))
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(applyName(sessionId), ExistingWorkPolicy.KEEP, request)
    }

    override fun startUndo(batchId: Long, restoreTreeUri: String?) {
        val request = OneTimeWorkRequestBuilder<UndoBatchWorker>()
            .setInputData(workDataOf(SortoraWorker.KEY_SESSION_ID to batchId, UndoBatchWorker.KEY_RESTORE_TREE to restoreTreeUri))
            .addTag(TAG)
            .build()
        // REPLACE: undoing again (e.g. with a restore folder) supersedes a finished or failed run.
        workManager.enqueueUniqueWork(undoName(batchId), ExistingWorkPolicy.REPLACE, request)
    }

    override fun observeUndo(batchId: Long): Flow<JobProgress> = observe(undoName(batchId))

    override fun observeAnalysis(sessionId: Long): Flow<JobProgress> = observe(analysisName(sessionId))

    override fun observeApply(sessionId: Long): Flow<JobProgress> = observe(applyName(sessionId))

    private fun observe(name: String): Flow<JobProgress> =
        workManager.getWorkInfosForUniqueWorkFlow(name)
            .map { infos ->
                val info = infos.lastOrNull()
                JobProgress(
                    state = info?.state.toJobState(),
                    currentFile = info?.progress?.getString(SortoraWorker.KEY_CURRENT),
                    error = info?.outputData?.getString(ApplyPlanWorker.KEY_ERROR),
                )
            }
            .distinctUntilChanged()

    private fun WorkInfo.State?.toJobState(): JobState = when (this) {
        null -> JobState.IDLE
        WorkInfo.State.ENQUEUED, WorkInfo.State.BLOCKED -> JobState.QUEUED
        WorkInfo.State.RUNNING -> JobState.RUNNING
        WorkInfo.State.SUCCEEDED -> JobState.SUCCEEDED
        WorkInfo.State.FAILED -> JobState.FAILED
        WorkInfo.State.CANCELLED -> JobState.CANCELLED
    }

    private fun analysisName(id: Long) = "analyze_$id"
    private fun applyName(id: Long) = "apply_$id"
    private fun undoName(id: Long) = "undo_$id"

    private companion object {
        const val TAG = "sortora"
    }
}
