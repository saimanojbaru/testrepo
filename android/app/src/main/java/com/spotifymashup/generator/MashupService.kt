package com.spotifymashup.generator

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import org.json.JSONArray
import kotlin.concurrent.thread

class MashupService : Service() {

    companion object {
        const val ACTION_START = "com.spotifymashup.START_MASHUP"
        const val EXTRA_TRACKS_JSON = "tracks_json"
        const val EXTRA_PITCH_SHIFT = "apply_pitch_shift"
        const val CHANNEL_ID = "mashup_gen"
        const val NOTIF_ID = 1001

        /**
         * Registered by MainActivity before starting the service.
         * Called on a background thread — post to main looper before touching UI.
         * jobId is non-null once the backend has accepted the job.
         */
        @Volatile
        var onProgress: ((status: String, message: String, progress: Int, jobId: String?) -> Unit)? = null

        fun start(ctx: Context, tracksJson: String, applyPitchShift: Boolean) {
            val i = Intent(ctx, MashupService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_TRACKS_JSON, tracksJson)
                putExtra(EXTRA_PITCH_SHIFT, applyPitchShift)
            }
            ctx.startForegroundService(i)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        val channel = NotificationChannel(
            CHANNEL_ID, "Mashup Generation",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Runs while your mashup is being created"
            setSound(null, null)
        }
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(channel)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIF_ID, buildNotification("Starting…"))

        val tracksJson = intent?.getStringExtra(EXTRA_TRACKS_JSON)
        val applyPitch = intent?.getBooleanExtra(EXTRA_PITCH_SHIFT, true) ?: true

        if (tracksJson == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        thread {
            try {
                val arr = JSONArray(tracksJson)
                val tracks = (0 until arr.length()).map { i ->
                    val o = arr.getJSONObject(i)
                    ApiClient.TrackInput(
                        url = o.getString("url"),
                        role = o.optString("role", "full"),
                        hookStartMs = if (o.has("hook_start_ms")) o.getInt("hook_start_ms") else null,
                        hookEndMs   = if (o.has("hook_end_ms"))   o.getInt("hook_end_ms")   else null,
                    )
                }

                postProgress("running", "Connecting to backend…", 5, null)
                val job = ApiClient.createMultiMashup(tracks, applyPitch)

                // Persist jobId so Activity can recover it after restart
                getSharedPreferences("mashup_prefs", MODE_PRIVATE)
                    .edit().putString("running_job_id", job.jobId).apply()

                // Poll until complete — hard 5-minute timeout
                val deadline = System.currentTimeMillis() + 5 * 60 * 1000L
                while (System.currentTimeMillis() < deadline) {
                    val r = ApiClient.getJob(job.jobId)
                    postProgress(r.status, r.message, r.progress, job.jobId)
                    pushNotification(r.message)
                    when (r.status) {
                        "done"   -> break
                        "failed" -> break
                        else     -> Thread.sleep(2500)
                    }
                }
                if (System.currentTimeMillis() >= deadline) {
                    postProgress("failed", "Timed out after 5 minutes. Try again.", 0, null)
                }
            } catch (e: Exception) {
                postProgress("failed", "Error: ${e.message?.take(80) ?: "unknown"}", 0, null)
                pushNotification("Failed — tap to reopen")
            } finally {
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private fun postProgress(status: String, message: String, progress: Int, jobId: String?) {
        onProgress?.invoke(status, message, progress, jobId)
    }

    private fun pushNotification(msg: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager)
            .notify(NOTIF_ID, buildNotification(msg))
    }

    private fun buildNotification(msg: String): Notification {
        val pi = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("Mashup Studio")
            .setContentText(msg)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(pi)
            .setOngoing(true)
            .build()
    }
}
