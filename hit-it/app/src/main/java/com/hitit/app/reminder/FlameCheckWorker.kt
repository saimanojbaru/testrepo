package com.hitit.app.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hitit.app.data.repository.ProfileRepository
import com.hitit.domain.flame.LifeFlame
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

/**
 * Daily check on the Life Flame. If no positive Momentum was earned over the recent window
 * ([LifeFlame.isFading]), posts a single gentle "your flame is fading" nudge. Stateless and
 * idempotent — safe to run on app open and on a daily schedule. Mirrors [SuddenDeathWorker].
 */
@HiltWorker
class FlameCheckWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val profileRepository: ProfileRepository,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val recent = profileRepository.recentDailyMomentum(LocalDate.now(), LifeFlame.FADING_WINDOW)
        if (LifeFlame.isFading(recent)) {
            ReminderNotifier.notifyFlameFading(applicationContext)
        }
        return Result.success()
    }

    companion object {
        const val WORK_NAME = "flame_check"
    }
}
