package com.spotifymashup.generator

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.spotifymashup.generator.api.ApiClient
import com.spotifymashup.generator.api.JobResponse
import com.spotifymashup.generator.api.MashupRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

private const val POLL_INTERVAL_MS = 3_000L
private const val MAX_POLL_ITERATIONS = 300   // 15-minute hard cap

class MashupRepository {

    private val api = ApiClient.service

    // ── Submit ────────────────────────────────────────────────────────────────

    suspend fun submitJob(request: MashupRequest): String =
        withContext(Dispatchers.IO) {
            api.createMashup(request).jobId
        }

    // ── Poll until terminal state ─────────────────────────────────────────────

    /**
     * Polls the server every [POLL_INTERVAL_MS] ms, calling [onProgress] each tick.
     *
     * @return The terminal [JobResponse] (status == "done" or "failed").
     */
    suspend fun pollUntilDone(
        jobId: String,
        onProgress: suspend (JobResponse) -> Unit,
    ): JobResponse = withContext(Dispatchers.IO) {
        var iterations = 0
        while (iterations++ < MAX_POLL_ITERATIONS) {
            val job = api.getJob(jobId)
            onProgress(job)
            when (job.status) {
                "done", "failed" -> return@withContext job
            }
            delay(POLL_INTERVAL_MS)
        }
        throw IllegalStateException("Timed out waiting for job $jobId after ${MAX_POLL_ITERATIONS * POLL_INTERVAL_MS / 1000}s")
    }

    // ── Download MP3 to device ────────────────────────────────────────────────

    /**
     * Downloads the finished MP3 and saves it to the Music directory.
     *
     * @return Absolute path to the saved file.
     */
    suspend fun downloadToMusic(context: Context, jobId: String, fileName: String): String =
        withContext(Dispatchers.IO) {
            val response = api.downloadMashup(jobId)
            val body = response.body()
                ?: throw IllegalStateException("Empty response body from server")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Scoped storage (Android 10+): use MediaStore
                val resolver = context.contentResolver
                val values = ContentValues().apply {
                    put(MediaStore.Audio.Media.DISPLAY_NAME, fileName)
                    put(MediaStore.Audio.Media.MIME_TYPE, "audio/mpeg")
                    put(MediaStore.Audio.Media.RELATIVE_PATH, Environment.DIRECTORY_MUSIC + "/Mashups")
                    put(MediaStore.Audio.Media.IS_PENDING, 1)
                }
                val uri = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                    ?: throw IllegalStateException("Failed to create MediaStore entry")
                resolver.openOutputStream(uri)!!.use { out ->
                    body.byteStream().copyTo(out)
                }
                values.clear()
                values.put(MediaStore.Audio.Media.IS_PENDING, 0)
                resolver.update(uri, values, null, null)
                uri.toString()
            } else {
                // Legacy storage (Android 9 and below)
                val dir = File(
                    Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
                    "Mashups"
                ).also { it.mkdirs() }
                val file = File(dir, fileName)
                FileOutputStream(file).use { out ->
                    body.byteStream().copyTo(out)
                }
                MediaScannerConnection.scanFile(context, arrayOf(file.absolutePath), null, null)
                file.absolutePath
            }
        }

    // ── Health check ──────────────────────────────────────────────────────────

    suspend fun isServerReachable(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Cheap GET to /health
            val raw = ApiClient.service.javaClass.getDeclaredMethod("getJob", String::class.java)
            // We can't call /health through the typed interface, so use OkHttp directly.
            val client = okhttp3.OkHttpClient()
            val req = okhttp3.Request.Builder()
                .url(com.spotifymashup.generator.BuildConfig.BASE_URL + "/health")
                .build()
            client.newCall(req).execute().use { it.isSuccessful }
        } catch (_: Exception) {
            false
        }
    }
}
