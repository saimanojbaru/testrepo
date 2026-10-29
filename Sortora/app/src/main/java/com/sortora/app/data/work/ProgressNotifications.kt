package com.sortora.app.data.work

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.ForegroundInfo
import androidx.work.WorkManager
import com.sortora.app.MainActivity
import com.sortora.app.R
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** Builds the ongoing "Analysing 12 of 80" notification shown while a batch runs. */
@Singleton
class ProgressNotifications @Inject constructor(@ApplicationContext private val context: Context) {

    fun foregroundInfo(workId: UUID, notificationId: Int, title: String, done: Int, total: Int, current: String?): ForegroundInfo {
        ensureChannel()
        val openApp = PendingIntent.getActivity(
            context, 0,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(
                if (total > 0) context.getString(R.string.notification_progress, done, total) else context.getString(R.string.notification_preparing),
            )
            .setSubText(current)
            .setProgress(total, done, total == 0)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setContentIntent(openApp)
            .addAction(0, context.getString(R.string.action_cancel), WorkManager.getInstance(context).createCancelPendingIntent(workId))
            .build()

        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.VANILLA_ICE_CREAM ->
                ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROCESSING)
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q ->
                ForegroundInfo(notificationId, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
            else -> ForegroundInfo(notificationId, notification)
        }
    }

    private fun ensureChannel() {
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel_name), NotificationManager.IMPORTANCE_LOW).apply {
                description = context.getString(R.string.notification_channel_description)
                setShowBadge(false)
            },
        )
    }

    companion object {
        const val CHANNEL_ID = "sortora_progress"
        const val ANALYSIS_NOTIFICATION_ID = 1001
        const val APPLY_NOTIFICATION_ID = 1002
    }
}
