package com.hitit.app.reminder

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.hitit.app.MainActivity
import com.hitit.app.R

/** Builds and posts Rep reminder notifications on dedicated channels. */
object ReminderNotifier {
    const val CHANNEL_ID = "rep_reminders"
    private const val CHANNEL_NAME = "Reminders"
    const val CHANNEL_SUDDEN_DEATH = "sudden_death"
    private const val CHANNEL_SUDDEN_DEATH_NAME = "Sudden Death alerts"
    private const val SUDDEN_DEATH_NOTIF_ID = 7777

    fun ensureChannel(context: Context) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (manager.getNotificationChannel(CHANNEL_ID) == null) {
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        if (manager.getNotificationChannel(CHANNEL_SUDDEN_DEATH) == null) {
            val ch = NotificationChannel(
                CHANNEL_SUDDEN_DEATH,
                CHANNEL_SUDDEN_DEATH_NAME,
                NotificationManager.IMPORTANCE_HIGH,
            )
            // A low-frequency "heartbeat" double-pulse for high-stakes alerts.
            ch.vibrationPattern = longArrayOf(0, 200, 250, 200)
            ch.enableVibration(true)
            manager.createNotificationChannel(ch)
        }
    }

    /** High-stakes "heartbeat" alert when Sudden Death reps were missed and Momentum was lost. */
    fun notifySuddenDeath(context: Context, penalizedCount: Int) {
        ensureChannel(context)
        val pendingIntent = android.app.PendingIntent.getActivity(
            context,
            SUDDEN_DEATH_NOTIF_ID,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            android.app.PendingIntent.FLAG_IMMUTABLE or android.app.PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notification = NotificationCompat.Builder(context, CHANNEL_SUDDEN_DEATH)
            .setContentTitle("⚠️ Sudden Death: Momentum lost")
            .setContentText("You missed $penalizedCount high-stakes rep(s). Get back on the board.")
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        if (NotificationManagerCompat.from(context).areNotificationsEnabled()) {
            NotificationManagerCompat.from(context).notify(SUDDEN_DEATH_NOTIF_ID, notification)
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
