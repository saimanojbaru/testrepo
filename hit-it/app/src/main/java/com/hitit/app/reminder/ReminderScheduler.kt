package com.hitit.app.reminder

import android.content.Context
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.hitit.app.data.local.dao.RepDao
import com.hitit.domain.reminder.ReminderSchedule
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.LocalDateTime
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Schedules and cancels per-Rep daily reminders via WorkManager. Each reminder is a one-shot worker
 * delayed until the next HH:mm; the worker re-schedules itself for the following day (see
 * [ReminderWorker]).
 */
@Singleton
class ReminderScheduler @Inject constructor(
    @ApplicationContext private val context: Context,
    private val repDao: RepDao,
) {
    private val workManager get() = WorkManager.getInstance(context)

    /** Queue the next occurrence of [hour]:[minute] for [repId], replacing any pending one. */
    fun schedule(repId: Long, hour: Int, minute: Int) {
        val delayMillis = ReminderSchedule.millisUntilNext(LocalDateTime.now(), hour, minute)
        val request = OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(delayMillis, TimeUnit.MILLISECONDS)
            .setInputData(Data.Builder().putLong(ReminderWorker.KEY_REP_ID, repId).build())
            .addTag(TAG)
            .build()
        workManager.enqueueUniqueWork(
            ReminderWorker.workName(repId),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    fun cancel(repId: Long) {
        workManager.cancelUniqueWork(ReminderWorker.workName(repId))
    }

    fun applyForRep(repId: Long, enabled: Boolean, hour: Int, minute: Int) {
        if (enabled) schedule(repId, hour, minute) else cancel(repId)
    }

    /** Re-arm all enabled reminders (call on app start so they survive reboots/process death). */
    suspend fun rescheduleAll() {
        repDao.getReminderEnabled().forEach { rep ->
            schedule(rep.id, rep.reminderHour, rep.reminderMinute)
        }
    }

    private companion object {
        const val TAG = "rep_reminder"
    }
}
