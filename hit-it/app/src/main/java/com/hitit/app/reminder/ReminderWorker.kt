package com.hitit.app.reminder

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hitit.app.data.local.dao.RepDao
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import java.time.LocalDate

/**
 * Fires a single Rep's daily reminder, then asks [ReminderScheduler] to queue tomorrow's. Reminders
 * are skipped while the Rep is in rest mode. Uses one-shot self-rescheduling rather than periodic
 * work so the exact time-of-day stays precise across days.
 */
@HiltWorker
class ReminderWorker @AssistedInject constructor(
    @Assisted appContext: Context,
    @Assisted params: WorkerParameters,
    private val repDao: RepDao,
    private val scheduler: ReminderScheduler,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val repId = inputData.getLong(KEY_REP_ID, -1L)
        if (repId < 0) return Result.success()

        val rep = repDao.getById(repId)
        if (rep == null || !rep.reminderEnabled || rep.isArchived) return Result.success()

        if (!isResting(rep.restModeStart, rep.restModeEnd, LocalDate.now())) {
            ReminderNotifier.notify(applicationContext, rep.id, rep.name, rep.emoji)
        }

        // Queue the next day's reminder.
        scheduler.schedule(rep.id, rep.reminderHour, rep.reminderMinute)
        return Result.success()
    }

    private fun isResting(start: LocalDate?, end: LocalDate?, today: LocalDate): Boolean {
        if (start == null) return false
        val effectiveEnd = end ?: today
        return !today.isBefore(start) && !today.isAfter(effectiveEnd)
    }

    companion object {
        const val KEY_REP_ID = "repId"
        fun workName(repId: Long): String = "reminder_$repId"
    }
}
