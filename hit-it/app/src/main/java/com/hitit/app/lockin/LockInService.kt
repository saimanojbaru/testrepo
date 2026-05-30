package com.hitit.app.lockin

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.hitit.app.R
import com.hitit.app.data.repository.LockInRepository
import com.hitit.domain.lockin.LockInClock
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

/**
 * Foreground service that runs a single Lock In countdown. It is the only writer of [LockInEngine];
 * the UI reads from there. On finish (natural or stopped early) it persists the session and awards
 * Momentum via [LockInRepository].
 */
@AndroidEntryPoint
class LockInService : Service() {

    @Inject lateinit var repository: LockInRepository

    private val scope = CoroutineScope(Dispatchers.Main.immediate + Job())
    private var tickJob: Job? = null

    private var endAtElapsed = 0L
    private var startInstant: Instant = Instant.now()
    private var plannedMinutes = 0
    private var stopRequested = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> handleStart(intent)
            ACTION_PAUSE -> LockInEngine.update { it.copy(paused = true) }
            ACTION_RESUME -> handleResume()
            ACTION_STOP -> stopRequested = true
        }
        return START_STICKY
    }

    private fun handleStart(intent: Intent) {
        plannedMinutes = intent.getIntExtra(EXTRA_MINUTES, 25).coerceAtLeast(1)
        val zone = intent.getStringExtra(EXTRA_ZONE) ?: FocusZones.DEFAULT
        val repId = intent.getLongExtra(EXTRA_REP_ID, -1L).takeIf { it >= 0 }
        val taskId = intent.getLongExtra(EXTRA_TASK_ID, -1L).takeIf { it >= 0 }
        val label = intent.getStringExtra(EXTRA_LABEL).orEmpty()

        val totalMillis = plannedMinutes.toLong() * 60_000L
        startInstant = Instant.now()
        endAtElapsed = SystemClock.elapsedRealtime() + totalMillis
        stopRequested = false

        LockInEngine.publish(
            LockInRunState(
                active = true,
                paused = false,
                totalMillis = totalMillis,
                remainingMillis = totalMillis,
                zone = zone,
                repId = repId,
                taskId = taskId,
                label = label,
            ),
        )

        ensureChannel()
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIF_ID, buildNotification(totalMillis), type)

        startTicking()
    }

    private fun handleResume() {
        val state = LockInEngine.current()
        if (!state.active) return
        endAtElapsed = SystemClock.elapsedRealtime() + state.remainingMillis
        LockInEngine.update { it.copy(paused = false) }
    }

    private fun startTicking() {
        tickJob?.cancel()
        tickJob = scope.launch {
            while (isActive) {
                val state = LockInEngine.current()
                if (!state.active) return@launch
                if (stopRequested) {
                    finishAndStop(stoppedEarly = true)
                    return@launch
                }
                if (!state.paused) {
                    val remaining = (endAtElapsed - SystemClock.elapsedRealtime()).coerceAtLeast(0L)
                    LockInEngine.update { if (it.active && !it.paused) it.copy(remainingMillis = remaining) else it }
                    updateNotification(remaining)
                    if (remaining <= 0L) {
                        finishAndStop(stoppedEarly = false)
                        return@launch
                    }
                }
                delay(TICK_MILLIS)
            }
        }
    }

    private suspend fun finishAndStop(stoppedEarly: Boolean) {
        val state = LockInEngine.current()
        val focused = LockInClock.focusedMinutes(state.totalMillis, state.remainingMillis)
        repository.completeSession(
            startTime = startInstant,
            endTime = Instant.now(),
            plannedMinutes = plannedMinutes,
            focusedMinutes = focused,
            repId = state.repId,
            taskId = state.taskId,
            zone = state.zone,
            completed = !stoppedEarly,
        )
        LockInEngine.publish(LockInRunState(justFinishedMinutes = focused))
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        tickJob?.cancel()
        scope.cancel()
        super.onDestroy()
    }

    // --- Notification ---

    private fun buildNotification(remainingMillis: Long): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Locked in")
            .setContentText("${LockInClock.format(remainingMillis)} left")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .build()

    private fun updateNotification(remainingMillis: Long) {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIF_ID, buildNotification(remainingMillis))
    }

    private fun ensureChannel() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Lock In", NotificationManager.IMPORTANCE_LOW),
            )
        }
    }

    companion object {
        private const val CHANNEL_ID = "lock_in"
        private const val NOTIF_ID = 4242
        private const val TICK_MILLIS = 250L

        const val ACTION_START = "com.hitit.app.lockin.START"
        const val ACTION_PAUSE = "com.hitit.app.lockin.PAUSE"
        const val ACTION_RESUME = "com.hitit.app.lockin.RESUME"
        const val ACTION_STOP = "com.hitit.app.lockin.STOP"

        private const val EXTRA_MINUTES = "minutes"
        private const val EXTRA_ZONE = "zone"
        private const val EXTRA_REP_ID = "repId"
        private const val EXTRA_TASK_ID = "taskId"
        private const val EXTRA_LABEL = "label"

        fun start(
            context: Context,
            minutes: Int,
            zone: String,
            repId: Long?,
            taskId: Long?,
            label: String,
        ) {
            val intent = Intent(context, LockInService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_MINUTES, minutes)
                putExtra(EXTRA_ZONE, zone)
                putExtra(EXTRA_REP_ID, repId ?: -1L)
                putExtra(EXTRA_TASK_ID, taskId ?: -1L)
                putExtra(EXTRA_LABEL, label)
            }
            ContextCompat.startForegroundService(context, intent)
        }

        fun send(context: Context, action: String) {
            context.startService(Intent(context, LockInService::class.java).setAction(action))
        }
    }
}
