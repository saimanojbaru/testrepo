package com.hitit.app.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hitit.app.data.repository.SuddenDeathRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Runs the Sudden Death evaluation (penalize missed high-stakes days). Idempotent, so it's safe to
 * run on app start and on a daily schedule. Posts a "heartbeat" warning notification if anything
 * was penalized.
 */
@HiltWorker
class SuddenDeathWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val suddenDeathRepository: SuddenDeathRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val penalized = suddenDeathRepository.evaluate()
        if (penalized > 0) {
            ReminderNotifier.notifySuddenDeath(applicationContext, penalized)
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "sudden_death_eval"
    }
}
