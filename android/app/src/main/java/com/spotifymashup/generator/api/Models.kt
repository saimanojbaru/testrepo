package com.spotifymashup.generator.api

import com.google.gson.annotations.SerializedName

// ── Request ───────────────────────────────────────────────────────────────────

data class MashupRequest(
    @SerializedName("track_a") val trackA: String,
    @SerializedName("track_b") val trackB: String,
    @SerializedName("youtube_only") val youtubeOnly: Boolean = false,
    @SerializedName("bpm_a") val bpmA: Float? = null,
    @SerializedName("bpm_b") val bpmB: Float? = null,
    @SerializedName("key_a") val keyA: Int? = null,
    @SerializedName("key_b") val keyB: Int? = null,
    @SerializedName("apply_pitch_shift") val applyPitchShift: Boolean = true,
    @SerializedName("stem_backend") val stemBackend: String = "demucs",
)

// ── Response ──────────────────────────────────────────────────────────────────

data class JobResponse(
    @SerializedName("job_id") val jobId: String,
    @SerializedName("status") val status: String,
    @SerializedName("message") val message: String,
    @SerializedName("progress") val progress: Int,
)
