package com.spotifymashup.generator

import android.util.Log
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lightweight HTTP client for the mashup backend.
 * Uses only android.jar and java.net — no Retrofit, no OkHttp.
 *
 * Change BASE_URL to point to your backend server.
 * Emulator loopback: http://10.0.2.2:8000
 * Physical device:   http://<your-LAN-ip>:8000
 */
object ApiClient {

    var baseUrl = "http://10.0.2.2:8000"

    data class JobResult(
        val jobId: String,
        val status: String,
        val message: String,
        val progress: Int
    )

    /** POST /api/mashup — creates a new job and returns its ID */
    fun createMashup(
        trackA: String,
        trackB: String,
        youtubeOnly: Boolean,
        bpmB: Double?,
        bpmA: Double?,
        applyPitchShift: Boolean
    ): JobResult {
        val body = JSONObject().apply {
            put("track_a", trackA)
            put("track_b", trackB)
            put("youtube_only", youtubeOnly)
            if (bpmB != null) put("bpm_b", bpmB)
            if (bpmA != null) put("bpm_a", bpmA)
            put("apply_pitch_shift", applyPitchShift)
            put("stem_backend", "demucs")
        }.toString()

        val url = URL("$baseUrl/api/mashup")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connectTimeout = 15_000
            readTimeout = 30_000
        }

        OutputStreamWriter(conn.outputStream, "UTF-8").use { it.write(body) }

        val code = conn.responseCode
        val response = if (code in 200..299) {
            conn.inputStream.bufferedReader().readText()
        } else {
            val err = conn.errorStream?.bufferedReader()?.readText() ?: "HTTP $code"
            throw RuntimeException("Server error $code: $err")
        }

        return parseJobResult(JSONObject(response))
    }

    /** GET /api/mashup/{jobId} — polls job status */
    fun getJob(jobId: String): JobResult {
        val conn = (URL("$baseUrl/api/mashup/$jobId").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        val response = conn.inputStream.bufferedReader().readText()
        return parseJobResult(JSONObject(response))
    }

    /** Returns the streaming URL for a completed job */
    fun downloadUrl(jobId: String) = "$baseUrl/api/mashup/$jobId/download"

    private fun parseJobResult(json: JSONObject) = JobResult(
        jobId    = json.getString("job_id"),
        status   = json.getString("status"),
        message  = json.getString("message"),
        progress = json.getInt("progress")
    )
}
