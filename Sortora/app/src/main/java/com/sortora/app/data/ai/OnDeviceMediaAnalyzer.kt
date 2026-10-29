package com.sortora.app.data.ai

import android.graphics.Bitmap
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.google.mlkit.vision.common.InputImage
import com.sortora.app.data.media.MetadataReader
import com.sortora.app.domain.classify.Classification
import com.sortora.app.domain.classify.FrameSignature
import com.sortora.app.domain.classify.LabelVocabulary
import com.sortora.app.domain.classify.SceneClassifier
import com.sortora.app.domain.classify.ScreenshotHeuristics
import com.sortora.app.domain.classify.TagSelector
import com.sortora.app.domain.model.AnalysisOptions
import com.sortora.app.domain.model.AnalysisResult
import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.DetectedLabel
import com.sortora.app.domain.model.FaceSummary
import com.sortora.app.domain.model.MediaFile
import com.sortora.app.domain.model.MediaKind
import com.sortora.app.domain.model.MediaMetadata
import com.sortora.app.domain.model.TextSummary
import com.sortora.app.domain.model.VideoStats
import com.sortora.app.domain.repository.AnalysisSession
import com.sortora.app.domain.repository.MediaAnalyzer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The on-device analysis pipeline.
 *
 * Images: decode (≤ ~2k px) → labels + objects + faces + OCR in parallel → screenshot score →
 * rule-based category → name tags.
 *
 * Videos: sample N key frames → labels + faces per frame (OCR on two frames) → face presence,
 * face size and frame-to-frame change → talking head / B-roll / screen recording.
 *
 * Nothing here performs I/O beyond reading the picked file through its content URI.
 */
class OnDeviceMediaAnalyzer @Inject constructor(
    private val bitmapLoader: BitmapLoader,
    private val frameExtractor: VideoFrameExtractor,
    private val metadataReader: MetadataReader,
    private val displaySizes: DisplaySizes,
) : MediaAnalyzer {

    override fun openSession(options: AnalysisOptions): AnalysisSession = Session(options)

    private inner class Session(private val options: AnalysisOptions) : AnalysisSession {
        private val vision = MlKitVision(options.enableOcr)

        override suspend fun analyze(file: MediaFile): AnalysisResult = when (file.kind) {
            MediaKind.IMAGE -> analyzeImage(file)
            MediaKind.VIDEO -> analyzeVideo(file)
        }

        override fun close() = vision.close()

        private suspend fun analyzeImage(file: MediaFile): AnalysisResult {
            val baseMetadata = withContext(Dispatchers.IO) { metadataReader.readImage(file) }
            val loaded = withContext(Dispatchers.IO) { bitmapLoader.load(file.uri.toUri(), IMAGE_MAX_DIMENSION) }
                ?: return limitedResult(file, baseMetadata)

            try {
                val metadata = baseMetadata.copy(
                    width = baseMetadata.width ?: loaded.sourceWidth.takeIf { it > 0 },
                    height = baseMetadata.height ?: loaded.sourceHeight.takeIf { it > 0 },
                )
                val image = InputImage.fromBitmap(loaded.bitmap, 0)
                val (labels, objects, faces, text) = coroutineScope {
                    val labels = async { vision.labels(image) }
                    val objects = async { vision.objects(image) }
                    val faces = async { vision.faces(image) }
                    val text = async { vision.text(image) }
                    Quad(labels.await(), objects.await(), faces.await(), text.await())
                }
                val allLabels = mergeLabels(labels + objects.labels)
                val screenshotScore = ScreenshotHeuristics.score(
                    ScreenshotHeuristics.Input(
                        displayName = file.displayName,
                        mimeType = file.mimeType,
                        pathHint = file.pathHint,
                        kind = MediaKind.IMAGE,
                        metadata = metadata,
                        screenSizes = displaySizes.sizes,
                        text = text,
                    ),
                )
                val textSummary = text ?: TextSummary.NONE
                val classification = SceneClassifier.classifyImage(allLabels, faces, textSummary, screenshotScore, objects.largestArea)
                return AnalysisResult(
                    category = classification.category,
                    confidence = classification.confidence,
                    labels = allLabels.take(MAX_STORED_LABELS),
                    faces = faces,
                    text = textSummary,
                    screenshotScore = screenshotScore,
                    metadata = metadata,
                    tags = TagSelector.select(classification.category, allLabels, faces, textSummary),
                )
            } finally {
                loaded.bitmap.recycle()
            }
        }

        private suspend fun analyzeVideo(file: MediaFile): AnalysisResult {
            val extracted = withContext(Dispatchers.IO) {
                frameExtractor.extract(file.uri.toUri(), options.videoFrameCount, VIDEO_MAX_DIMENSION)
            }
            val info = extracted.info
            val (date, dateSource) = metadataReader.resolveVideoDate(file, info.containerDate)
            val metadata = MediaMetadata(
                captureTimeMillis = date,
                dateSource = dateSource,
                width = info.width,
                height = info.height,
                durationMs = info.durationMs,
                hasAudio = info.hasAudio,
            )
            if (extracted.frames.isEmpty()) return limitedResult(file, metadata)

            try {
                val frameLabels = mutableListOf<List<DetectedLabel>>()
                val frameFaces = mutableListOf<FaceSummary>()
                val signatures = mutableListOf<FloatArray>()
                var bestText: TextSummary? = null
                var textHeavyFrames = 0
                // OCR is the slowest detector, so only run it on two frames.
                val ocrFrames = setOf(0, extracted.frames.size / 2)

                extracted.frames.forEachIndexed { index, frame ->
                    val image = InputImage.fromBitmap(frame.bitmap, frame.rotationDegrees)
                    frameLabels += vision.labels(image)
                    frameFaces += vision.faces(image)
                    signatures += signature(frame.bitmap)
                    if (index in ocrFrames) {
                        vision.text(image)?.let { text ->
                            if (text.charCount >= 150 || text.coverage >= 0.1f) textHeavyFrames++
                            if (text.charCount > (bestText?.charCount ?: -1)) bestText = text
                        }
                    }
                }

                val withFaces = frameFaces.filter { it.count > 0 }
                val stats = VideoStats(
                    sampledFrames = extracted.frames.size,
                    framesWithFaces = withFaces.size,
                    framesWithSingleFace = frameFaces.count { it.count == 1 },
                    averageFaceArea = withFaces.map { it.largestFaceArea }.average().toFloatOrZero(),
                    averageFaceCenterX = withFaces.map { it.largestFaceCenterX }.average().toFloatOrZero(0.5f),
                    averageFrameDifference = FrameSignature.averageDifference(signatures),
                    textHeavyFrames = textHeavyFrames,
                )
                val screenRecordingScore = ScreenshotHeuristics.score(
                    ScreenshotHeuristics.Input(
                        displayName = file.displayName,
                        mimeType = file.mimeType,
                        pathHint = file.pathHint,
                        kind = MediaKind.VIDEO,
                        metadata = metadata,
                        screenSizes = displaySizes.sizes,
                        textHeavyFrameRatio = textHeavyFrames.toFloat() / ocrFrames.size,
                    ),
                )
                val classification = SceneClassifier.classifyVideo(stats, screenRecordingScore)
                val labels = aggregateFrameLabels(frameLabels)
                val representativeFace = withFaces.maxByOrNull { it.largestFaceArea } ?: FaceSummary.NONE
                val text = bestText ?: TextSummary.NONE
                return AnalysisResult(
                    category = classification.category,
                    confidence = classification.confidence,
                    labels = labels.take(MAX_STORED_LABELS),
                    faces = representativeFace,
                    text = text,
                    screenshotScore = screenRecordingScore,
                    video = stats,
                    metadata = metadata,
                    tags = TagSelector.select(
                        classification.category, labels, representativeFace, text,
                        hasFacesInVideo = stats.faceFrameRatio >= 0.3f,
                    ),
                )
            } finally {
                extracted.frames.forEach { it.bitmap.recycle() }
            }
        }

        /** Used when pixels can't be decoded: classify from metadata alone. */
        private fun limitedResult(file: MediaFile, metadata: MediaMetadata): AnalysisResult {
            val dated = if (metadata.captureTimeMillis == null) {
                val (date, source) = metadataReader.fallbackDate(file)
                metadata.copy(captureTimeMillis = date, dateSource = source)
            } else {
                metadata
            }
            val score = ScreenshotHeuristics.score(
                ScreenshotHeuristics.Input(file.displayName, file.mimeType, file.pathHint, file.kind, dated, displaySizes.sizes),
            )
            val classification = when {
                score < ScreenshotHeuristics.THRESHOLD -> Classification(ContentCategory.OTHER, 0.2f)
                file.kind == MediaKind.VIDEO -> Classification(ContentCategory.SCREEN_RECORDING, score)
                else -> Classification(ContentCategory.SCREENSHOT, score)
            }
            return AnalysisResult(
                category = classification.category,
                confidence = classification.confidence,
                screenshotScore = score,
                metadata = dated,
                limited = true,
            )
        }
    }

    private data class Quad(
        val labels: List<DetectedLabel>,
        val objects: MlKitVision.Objects,
        val faces: FaceSummary,
        val text: TextSummary?,
    )

    private fun signature(bitmap: Bitmap): FloatArray {
        val small = bitmap.scale(FrameSignature.SIZE, FrameSignature.SIZE)
        val pixels = IntArray(FrameSignature.SIZE * FrameSignature.SIZE)
        small.getPixels(pixels, 0, FrameSignature.SIZE, 0, 0, FrameSignature.SIZE, FrameSignature.SIZE)
        if (small !== bitmap) small.recycle()
        return FrameSignature.fromArgb(pixels)
    }

    /** Same label from the labeler and the object detector: keep the more confident one. */
    private fun mergeLabels(labels: List<DetectedLabel>): List<DetectedLabel> =
        labels.groupBy { LabelVocabulary.stemPhrase(it.text) }
            .map { (_, group) -> group.maxBy { it.confidence } }
            .sortedByDescending { it.confidence }

    /**
     * A label's video confidence is its mean confidence where it appears, weighted by how often it
     * appears, so a desk visible throughout beats a cup seen in one frame.
     */
    private fun aggregateFrameLabels(perFrame: List<List<DetectedLabel>>): List<DetectedLabel> {
        val frames = perFrame.size.coerceAtLeast(1)
        return perFrame.flatten()
            .groupBy { LabelVocabulary.stemPhrase(it.text) }
            .map { (_, hits) ->
                val presence = hits.size.coerceAtMost(frames).toFloat() / frames
                val mean = hits.map { it.confidence }.average().toFloat()
                DetectedLabel(hits.first().text, mean * (0.5f + 0.5f * presence))
            }
            .sortedByDescending { it.confidence }
    }

    private fun Double.toFloatOrZero(default: Float = 0f): Float = if (isNaN()) default else toFloat()

    private companion object {
        const val IMAGE_MAX_DIMENSION = 1024
        const val VIDEO_MAX_DIMENSION = 640
        const val MAX_STORED_LABELS = 12
    }
}
