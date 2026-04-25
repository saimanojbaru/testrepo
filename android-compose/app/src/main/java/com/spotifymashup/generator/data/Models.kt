package com.spotifymashup.generator.data

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TrendingHookRequest(
    @SerialName("spotify_url") val spotifyUrl: String,
    @SerialName("top_k") val topK: Int = 5,
)

@Serializable
data class HookDto(
    @SerialName("start_ms") val startMs: Int,
    @SerialName("end_ms") val endMs: Int,
    @SerialName("duration_ms") val durationMs: Int,
    val score: Float,
    val confidence: Float,
    val label: String,
    val reasons: List<String> = emptyList(),
    val signals: Map<String, Float> = emptyMap(),
)

@Serializable
data class TrendingHookResponse(
    @SerialName("track_id") val trackId: String,
    @SerialName("track_name") val trackName: String,
    @SerialName("artist_name") val artistName: String,
    @SerialName("duration_ms") val durationMs: Int,
    val bpm: Float? = null,
    @SerialName("key_label") val keyLabel: String? = null,
    val hooks: List<HookDto> = emptyList(),
)

@Serializable
data class CompatibilityRequest(
    @SerialName("spotify_url_a") val spotifyUrlA: String,
    @SerialName("spotify_url_b") val spotifyUrlB: String,
)

@Serializable
data class CompatibilityResponse(
    @SerialName("bpm_a") val bpmA: Float? = null,
    @SerialName("bpm_b") val bpmB: Float? = null,
    @SerialName("key_a_label") val keyALabel: String? = null,
    @SerialName("key_b_label") val keyBLabel: String? = null,
    @SerialName("bpm_score") val bpmScore: Float,
    @SerialName("key_score") val keyScore: Float,
    @SerialName("energy_score") val energyScore: Float,
    @SerialName("overall_score") val overallScore: Float,
    @SerialName("suggested_pitch_shift") val suggestedPitchShift: Int? = null,
    @SerialName("suggested_tempo_ratio") val suggestedTempoRatio: Float? = null,
)

@Serializable
data class MashupRequest(
    @SerialName("track_a") val trackA: String,
    @SerialName("track_b") val trackB: String,
    @SerialName("youtube_only") val youtubeOnly: Boolean = false,
    @SerialName("bpm_a") val bpmA: Double? = null,
    @SerialName("bpm_b") val bpmB: Double? = null,
    @SerialName("apply_pitch_shift") val applyPitchShift: Boolean = true,
    @SerialName("stem_backend") val stemBackend: String = "demucs",
    @SerialName("hook_a_start_ms") val hookAStartMs: Int? = null,
    @SerialName("hook_a_end_ms") val hookAEndMs: Int? = null,
    @SerialName("hook_b_start_ms") val hookBStartMs: Int? = null,
    @SerialName("hook_b_end_ms") val hookBEndMs: Int? = null,
)

@Serializable
data class JobResponse(
    @SerialName("job_id") val jobId: String,
    val status: String,
    val message: String,
    val progress: Int,
)
