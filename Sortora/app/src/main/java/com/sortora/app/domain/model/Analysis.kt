package com.sortora.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class LabelSource { IMAGE_LABEL, OBJECT }

/** A label produced by an on-device model, e.g. "Desk" at 0.91 confidence. */
@Serializable
data class DetectedLabel(
    val text: String,
    val confidence: Float,
    val source: LabelSource = LabelSource.IMAGE_LABEL,
)

/** Face statistics for a single image or frame. Areas are fractions of the frame (0..1). */
@Serializable
data class FaceSummary(
    val count: Int = 0,
    val largestFaceArea: Float = 0f,
    val largestFaceCenterX: Float = 0.5f,
) {
    companion object {
        val NONE = FaceSummary()
    }
}

/** What OCR found, reduced to what classification and naming need. */
@Serializable
data class TextSummary(
    val charCount: Int = 0,
    val lineCount: Int = 0,
    /** Fraction of the image covered by text lines (0..1). */
    val coverage: Float = 0f,
    /** True if a clock/battery-like line sits in the top strip, like a phone status bar. */
    val hasStatusBar: Boolean = false,
    /** Best guess at a screen or document title (largest short line near the top). */
    val title: String? = null,
    /** Lower-cased distinct words (>= 3 chars), capped, used for keyword matching. */
    val keywords: List<String> = emptyList(),
) {
    companion object {
        val NONE = TextSummary()
    }
}

/** Aggregated statistics over sampled video frames. */
@Serializable
data class VideoStats(
    val sampledFrames: Int = 0,
    val framesWithFaces: Int = 0,
    val framesWithSingleFace: Int = 0,
    /** Mean area of the largest face over frames that contain a face. */
    val averageFaceArea: Float = 0f,
    /** Mean horizontal centre (0..1) of the largest face over frames that contain one. */
    val averageFaceCenterX: Float = 0.5f,
    /** Mean visual difference between consecutive sampled frames (0 = identical, 1 = opposite). */
    val averageFrameDifference: Float = 0f,
    val textHeavyFrames: Int = 0,
) {
    val faceFrameRatio: Float
        get() = if (sampledFrames == 0) 0f else framesWithFaces.toFloat() / sampledFrames

    val singleFaceRatio: Float
        get() = if (sampledFrames == 0) 0f else framesWithSingleFace.toFloat() / sampledFrames
}

/** The full on-device analysis of one file. Persisted as JSON in the session database. */
@Serializable
data class AnalysisResult(
    val category: ContentCategory,
    /** 0..1: how sure the rules were about [category]. Shown as "low confidence" in the preview. */
    val confidence: Float,
    val labels: List<DetectedLabel> = emptyList(),
    val faces: FaceSummary = FaceSummary.NONE,
    val text: TextSummary = TextSummary.NONE,
    val screenshotScore: Float = 0f,
    val video: VideoStats? = null,
    val metadata: MediaMetadata = MediaMetadata(),
    /** Short, name-friendly keywords chosen from the labels (e.g. ["person", "desk"]). */
    val tags: List<String> = emptyList(),
    /** True when pixels could not be decoded and only metadata was used. */
    val limited: Boolean = false,
    /** 64-bit difference hash for near-duplicate detection (middle frame for videos). */
    val perceptualHash: Long? = null,
    /** Variance of the Laplacian at ≤512 px; low values mean blurry. */
    val sharpness: Float? = null,
)

/** One face found during analysis, ready for clustering. Never leaves the device. */
class FaceSample(
    /** L2-normalised embedding of the aligned face crop. */
    val embedding: FloatArray,
    /** Small JPEG of the crop, shown in the face groups screen. */
    val thumbnailJpeg: ByteArray,
)

/** Everything one analysis step produces. */
class AnalysisOutput(
    val result: AnalysisResult,
    val faces: List<FaceSample> = emptyList(),
    /** L2-normalised whole-image embedding, used to confirm near-duplicates. */
    val imageEmbedding: FloatArray? = null,
)
