package com.spotifymashup.generator

import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

object ApiClient {

    var baseUrl: String = "https://aggdynamo-mashup.hf.space"

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

    data class SearchResult(
        val id: String,
        val title: String,
        val artist: String,
        val durationMs: Int,
        val thumbnailUrl: String,
        val previewUrl: String?,
        val source: String,   // "spotify" | "youtube"
        val url: String,
    )

    data class TrackInput(
        val url: String,
        val role: String = "full",
        val hookStartMs: Int? = null,
        val hookEndMs: Int? = null,
        val bpmOverride: Float? = null,
        val pitchShift: Int = 0,
        val volume: Float = 1.0f,
    )

    // ── Public API ───────────────────────────────────────────────────────────

    fun health(): Boolean = try {
        val conn = (URL("$baseUrl/health").openConnection() as HttpURLConnection).apply {
            connectTimeout = 5_000; readTimeout = 5_000
        }
        conn.responseCode in 200..299
    } catch (_: Exception) { false }

    fun search(query: String, source: String = "spotify", limit: Int = 8): List<SearchResult> {
        val body = JSONObject().apply {
            put("query", query)
            put("source", source)
            put("limit", limit)
        }
        val j = postJson("/api/search", body)
        val arr = j.getJSONArray("results")
        return (0 until arr.length()).map { i ->
            val r = arr.getJSONObject(i)
            SearchResult(
                id = r.getString("id"),
                title = r.getString("title"),
                artist = r.getString("artist"),
                durationMs = r.getInt("duration_ms"),
                thumbnailUrl = r.optString("thumbnail_url", ""),
                previewUrl = r.optString("preview_url").takeIf { it.isNotEmpty() },
                source = r.getString("source"),
                url = r.getString("url"),
            )
        }
    }

    fun trendingHook(spotifyUrl: String, topK: Int = 5): TrendingHooks {
        val body = JSONObject().apply {
            put("spotify_url", spotifyUrl)
            put("top_k", topK)
        }
        return parseTrendingHooks(postJson("/api/trending-hook", body))
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

    /** Multi-track mashup (2–4 tracks with roles). */
    fun createMultiMashup(tracks: List<TrackInput>, applyPitchShift: Boolean): JobResult {
        val tracksArr = JSONArray()
        tracks.forEach { t ->
            val obj = JSONObject().apply {
                put("url", t.url)
                put("role", t.role)
                put("pitch_shift", t.pitchShift)
                put("volume", t.volume)
                if (t.bpmOverride != null) put("bpm_override", t.bpmOverride)
                if (t.hookStartMs != null) put("hook_start_ms", t.hookStartMs)
                if (t.hookEndMs != null) put("hook_end_ms", t.hookEndMs)
            }
            tracksArr.put(obj)
        }
        val body = JSONObject().apply {
            put("tracks", tracksArr)
            put("apply_pitch_shift", applyPitchShift)
            put("stem_backend", "demucs")
        }
        return parseJobResult(postJson("/api/mashup/multi", body))
    }

    /** Legacy 2-track mashup kept for compatibility. */
    fun createMashup(
        trackA: String, trackB: String,
        youtubeOnly: Boolean, bpmA: Double?, bpmB: Double?,
        applyPitchShift: Boolean,
        hookA: Hook? = null, hookB: Hook? = null,
    ): JobResult {
        val body = JSONObject().apply {
            put("track_a", trackA); put("track_b", trackB)
            put("youtube_only", youtubeOnly)
            if (bpmA != null) put("bpm_a", bpmA)
            if (bpmB != null) put("bpm_b", bpmB)
            put("apply_pitch_shift", applyPitchShift)
            put("stem_backend", "demucs")
            if (hookA != null) { put("hook_a_start_ms", hookA.startMs); put("hook_a_end_ms", hookA.endMs) }
            if (hookB != null) { put("hook_b_start_ms", hookB.startMs); put("hook_b_end_ms", hookB.endMs) }
        }
        return parseJobResult(postJson("/api/mashup", body))
    }

    fun getJob(jobId: String): JobResult {
        val conn = (URL("$baseUrl/api/mashup/$jobId").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000; readTimeout = 15_000
        }
        return parseJobResult(JSONObject(conn.inputStream.bufferedReader().use { it.readText() }))
    }

    fun downloadUrl(jobId: String): String = "$baseUrl/api/mashup/$jobId/download"

    // ── Helpers ──────────────────────────────────────────────────────────────

    private fun postJson(path: String, body: JSONObject): JSONObject {
        val conn = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"; doOutput = true
            setRequestProperty("Content-Type", "application/json; charset=utf-8")
            setRequestProperty("Accept", "application/json")
            connectTimeout = 15_000; readTimeout = 60_000
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
                startMs = h.getInt("start_ms"), endMs = h.getInt("end_ms"),
                durationMs = h.getInt("duration_ms"),
                score = h.getDouble("score").toFloat(),
                confidence = h.getDouble("confidence").toFloat(),
                label = h.getString("label"),
                reasons = reasons, signals = signals,
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
