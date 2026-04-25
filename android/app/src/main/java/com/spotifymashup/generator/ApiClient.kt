package com.spotifymashup.generator

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

/**
 * Lightweight HTTP client for the Mashup Studio backend.
 * Uses only the Android framework — no Retrofit, no OkHttp.
 *
 * Set [baseUrl] from the UI before any call.
 */
object ApiClient {

    var baseUrl: String = "http://10.0.2.2:8000"

    // ── DTOs ─────────────────────────────────────────────────────────────────

    data class JobResult(
        val jobId: String,
        val status: String,
        val message: String,
        val progress: Int,
    )

    data class Hook(
        val startMs: Int,
        val endMs: Int,
        val durationMs: Int,
        val score: Float,
        val confidence: Float,
        val label: String,
        val reasons: List<String>,
        val signals: Map<String, Float>,
    )

    data class TrendingHooks(
        val trackId: String,
        val trackName: String,
        val artistName: String,
        val durationMs: Int,
        val bpm: Float?,
        val keyLabel: String?,
        val hooks: List<Hook>,
    )

    data class Compatibility(
        val bpmA: Float?,
        val bpmB: Float?,
        val keyALabel: String?,
        val keyBLabel: String?,
        val bpmScore: Float,
        val keyScore: Float,
        val energyScore: Float,
        val overallScore: Float,
        val suggestedPitchShift: Int?,
        val suggestedTempoRatio: Float?,
    )

    // ── Public API ───────────────────────────────────────────────────────────

    fun health(): Boolean = try {
        val conn = (URL("$baseUrl/health").openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000
            readTimeout = 5_000
        }
        conn.responseCode in 200..299
    } catch (_: Exception) {
        false
    }

    fun trendingHook(spotifyUrl: String, topK: Int = 5): TrendingHooks {
        val body = JSONObject().apply {
            put("spotify_url", spotifyUrl)
            put("top_k", topK)
        }
        val json = postJson("/api/trending-hook", body)
        return parseTrendingHooks(json)
    }

    fun compatibility(urlA: String, urlB: String): Compatibility {
        val body = JSONObject().apply {
            put("spotify_url_a", urlA)
            put("spotify_url_b", urlB)
        }
        val j = postJson("/api/compatibility", body)
        return Compatibility(
            bpmA = j.optDoubleOrNull("bpm_a")?.toFloat(),
            bpmB = j.optDoubleOrNull("bpm_b")?.toFloat(),
            keyALabel = j.optString("key_a_label", null),
            keyBLabel = j.optString("key_b_label", null),
            bpmScore = j.getDouble("bpm_score").toFloat(),
            keyScore = j.getDouble("key_score").toFloat(),
            energyScore = j.getDouble("energy_score").toFloat(),
            overallScore = j.getDouble("overall_score").toFloat(),
            suggestedPitchShift = j.optIntOrNull("suggested_pitch_shift"),
            suggestedTempoRatio = j.optDoubleOrNull("suggested_tempo_ratio")?.toFloat(),
        )
    }

    fun createMashup(
        trackA: String,
        trackB: String,
        youtubeOnly: Boolean,
        bpmA: Double?,
        bpmB: Double?,
        applyPitchShift: Boolean,
        hookA: Hook? = null,
        hookB: Hook? = null,
    ): JobResult {
        val body = JSONObject().apply {
            put("track_a", trackA)
            put("track_b", trackB)
            put("youtube_only", youtubeOnly)
            if (bpmA != null) put("bpm_a", bpmA)
            if (bpmB != null) put("bpm_b", bpmB)
            put("apply_pitch_shift", applyPitchShift)
            put("stem_backend", "demucs")
            if (hookA != null) {
                put("hook_a_start_ms", hookA.startMs)
                put("hook_a_end_ms", hookA.endMs)
            }
            if (hookB != null) {
                put("hook_b_start_ms", hookB.startMs)
                put("hook_b_end_ms", hookB.endMs)
            }
        }
        val j = postJson("/api/mashup", body)
        return parseJobResult(j)
    }

    fun getJob(jobId: String): JobResult {
        val conn = (URL("$baseUrl/api/mashup/$jobId").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 15_000
        }
        val response = conn.inputStream.bufferedReader().use { it.readText() }
        return parseJobResult(JSONObject(response))
    }

    fun downloadUrl(jobId: String): String = "$baseUrl/api/mashup/$jobId/download"

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun postJson(path: String, body: JSONObject): JSONObject {
        val url = URL("$baseUrl$path")
        val conn = (url.openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15_000
            readTimeout = 60_000
        }
        OutputStreamWriter(conn.outputStream, Charsets.UTF_8).use { it.write(body.toString()) }
        val code = conn.responseCode
        val text = if (code in 200..299) {
            conn.inputStream.bufferedReader().use { it.readText() }
        } else {
            val err = conn.errorStream?.bufferedReader()?.use { it.readText() } ?: "HTTP $code"
            throw RuntimeException("Server $code: $err")
        }
        return JSONObject(text)
    }

    private fun parseJobResult(j: JSONObject) = JobResult(
        jobId = j.getString("job_id"),
        status = j.getString("status"),
        message = j.getString("message"),
        progress = j.getInt("progress"),
    )

    private fun parseTrendingHooks(j: JSONObject): TrendingHooks {
        val arr: JSONArray = j.getJSONArray("hooks")
        val hooks = (0 until arr.length()).map { idx ->
            val h = arr.getJSONObject(idx)
            val reasons = h.optJSONArray("reasons")?.let { ra ->
                (0 until ra.length()).map { ra.getString(it) }
            } ?: emptyList()
            val signals = mutableMapOf<String, Float>()
            h.optJSONObject("signals")?.let { so ->
                so.keys().forEach { k -> signals[k] = so.getDouble(k).toFloat() }
            }
            Hook(
                startMs = h.getInt("start_ms"),
                endMs = h.getInt("end_ms"),
                durationMs = h.getInt("duration_ms"),
                score = h.getDouble("score").toFloat(),
                confidence = h.getDouble("confidence").toFloat(),
                label = h.getString("label"),
                reasons = reasons,
                signals = signals,
            )
        }
        return TrendingHooks(
            trackId = j.getString("track_id"),
            trackName = j.getString("track_name"),
            artistName = j.getString("artist_name"),
            durationMs = j.getInt("duration_ms"),
            bpm = j.optDoubleOrNull("bpm")?.toFloat(),
            keyLabel = j.optString("key_label", null),
            hooks = hooks,
        )
    }

    private fun JSONObject.optDoubleOrNull(name: String): Double? =
        if (isNull(name) || !has(name)) null else getDouble(name)

    private fun JSONObject.optIntOrNull(name: String): Int? =
        if (isNull(name) || !has(name)) null else getInt(name)
}
