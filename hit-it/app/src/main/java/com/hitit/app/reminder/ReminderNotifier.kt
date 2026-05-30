package com.hitit.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hitit.app.MainActivity
import com.hitit.app.R

/** Builds and posts Rep reminder notifications on a single channel. */
object ReminderNotifier {
    const val CHANNEL_ID = "rep_reminders"
    private const val CHANNEL_NAME = "Reminders"

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
    }

    /** Posts a reminder for a Rep, keyed by [repId] so re-firing replaces the prior one. */
    fun notify(context: Context, repId: Long, repName: String, emoji: String) {
        ensureChannel(context)
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            repId.toInt(),
            Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("$emoji $repName")
            .setContentText("Time to hit it — keep your streak alive.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()

        // POST_NOTIFICATIONS (API 33+) is requested at runtime elsewhere; guard the post.
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(repId.toInt(), notification)
        }
    }
}
