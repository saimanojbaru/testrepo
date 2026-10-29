package com.sortora.app.data.ai

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import androidx.core.net.toUri
import com.google.mlkit.vision.common.InputImage
import com.sortora.app.data.media.MetadataReader
import com.sortora.app.domain.classify.Classification
import com.sortora.app.domain.classify.FrameSignature
import com.sortora.app.domain.classify.ImageMetrics
import com.sortora.app.domain.classify.LabelVocabulary
import com.sortora.app.domain.classify.SceneClassifier
import com.sortora.app.domain.classify.ScreenshotHeuristics
import com.sortora.app.domain.classify.TagSelector
import com.sortora.app.domain.model.AnalysisOptions
import com.sortora.app.domain.model.AnalysisOutput
import com.sortora.app.domain.model.AnalysisResult
import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.DetectedLabel
import com.sortora.app.domain.model.FaceSample
import com.sortora.app.domain.model.FaceSummary
import com.sortora.app.domain.model.MediaFile
import com.sortora.app.domain.model.MediaKind
import com.sortora.app.domain.model.MediaMetadata
import com.sortora.app.domain.model.TextSummary
import com.sortora.app.domain.model.VideoStats
import com.sortora.app.domain.repository.AnalysisSession
import com.sortora.app.domain.repository.MediaAnalyzer
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import javax.inject.Inject

/**
 * The on-device analysis pipeline.
 *
 * Images: decode (≤ ~2k px) → ML Kit labels + objects + faces + OCR in parallel → screenshot
 * score → rule-based category → name tags. Plus, for the extra features: a perceptual hash and a
 * TFLite embedding (duplicates), a blur score, and aligned face crops with embeddings (face groups).
 *
 * Videos: sample N key frames → labels + faces per frame (OCR on two frames) → face presence,
 * face size and frame-to-frame change → talking head / B-roll / screen recording. The middle frame
 * provides the hash and embedding, the frame with the biggest face provides the face crop.
 *
 * Nothing here performs I/O beyond reading the picked file through its content URI.
 */
class OnDeviceMediaAnalyzer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val bitmapLoader: BitmapLoader,
    private val frameExtractor: VideoFrameExtractor,
    private val metadataReader: MetadataReader,
    private val displaySizes: DisplaySizes,
) : MediaAnalyzer {

    override fun openSession(options: AnalysisOptions): AnalysisSession = Session(options)

    private inner class Session(private val options: AnalysisOptions) : AnalysisSession {
        private val vision = MlKitVision(options.enableOcr, faceLandmarks = options.faceGrouping)
        /** Needed for near-duplicate confirmation and face groups; null if either is off or it can't load. */
        private val embedder = if (options.faceGrouping || options.detectDuplicates) ImageEmbedder.create(context) else null

        override suspend fun analyze(file: MediaFile): AnalysisOutput = when (file.kind) {
            MediaKind.IMAGE -> analyzeImage(file)
            MediaKind.VIDEO -> analyzeVideo(file)
        }

        override fun close() {
            vision.close()
            embedder?.close()
        }

        private suspend fun analyzeImage(file: MediaFile): AnalysisOutput {
            val baseMetadata = withContext(Dispatchers.IO) { metadataReader.readImage(file) }
            val loaded = withContext(Dispatchers.IO) { bitmapLoader.load(file.uri.toUri(), IMAGE_MAX_DIMENSION) }
                ?: return AnalysisOutput(limitedResult(file, baseMetadata))

            try {
                val bitmap = loaded.bitmap
                val metadata = baseMetadata.copy(
                    width = baseMetadata.width ?: loaded.sourceWidth.takeIf { it > 0 },
                    height = baseMetadata.height ?: loaded.sourceHeight.takeIf { it > 0 },
                )
                val image = InputImage.fromBitmap(bitmap, 0)
                val (labels, objects, detectedFaces, text) = coroutineScope {
                    val labels = async { vision.labels(image) }
                    val objects = async { vision.objects(image) }
                    val faces = async { vision.detectFaces(image) }
                    val text = async { vision.text(image) }
                    Quad(labels.await(), objects.await(), faces.await(), text.await())
                }
                val faces = vision.summarize(detectedFaces, image)
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
                val pixels = pixelMetrics(bitmap)
                val result = AnalysisResult(
                    category = classification.category,
                    confidence = classification.confidence,
                    labels = allLabels.take(MAX_STORED_LABELS),
                    faces = faces,
                    text = textSummary,
                    screenshotScore = screenshotScore,
                    metadata = metadata,
                    tags = TagSelector.select(classification.category, allLabels, faces, textSummary),
                    perceptualHash = pixels.first,
                    sharpness = pixels.second,
                )
                return AnalysisOutput(
                    result = result,
                    // Screenshots rarely show real people worth grouping (avatars, video calls).
                    faces = if (classification.category == ContentCategory.SCREENSHOT) emptyList() else faceSamples(bitmap, detectedFaces),
                    imageEmbedding = embedIfEnabled(bitmap),
                )
            } finally {
                loaded.bitmap.recycle()
            }
        }

        private suspend fun analyzeVideo(file: MediaFile): AnalysisOutput {
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
            if (extracted.frames.isEmpty()) return AnalysisOutput(limitedResult(file, metadata))

            try {
                val frameLabels = mutableListOf<List<DetectedLabel>>()
                val frameFaces = mutableListOf<FaceSummary>()
                val signatures = mutableListOf<FloatArray>()
                val sharpness = mutableListOf<Float>()
                var bestText: TextSummary? = null
                var textHeavyFrames = 0
                var bestFaceFrame: Pair<Int, List<MlKitVision.DetectedFace>>? = null
                // OCR is the slowest detector, so only run it on two frames.
                val ocrFrames = setOf(0, extracted.frames.size / 2)

                extracted.frames.forEachIndexed { index, frame ->
                    val image = InputImage.fromBitmap(frame.bitmap, frame.rotationDegrees)
                    frameLabels += vision.labels(image)
                    val detected = vision.detectFaces(image)
                    val summary = vision.summarize(detected, image)
                    frameFaces += summary
                    if (detected.isNotEmpty() && summary.largestFaceArea > (bestFaceFrame?.let { frameFaces[it.first].largestFaceArea } ?: 0f)) {
                        bestFaceFrame = index to detected
                    }
                    signatures += signature(frame.bitmap)
                    sharpness += pixelMetrics(frame.bitmap).second
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
                val middle = extracted.frames[extracted.frames.size / 2].bitmap
                val result = AnalysisResult(
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
                    perceptualHash = pixelMetrics(middle).first,
                    sharpness = sharpness.sorted()[sharpness.size / 2],
                )
                val faceSamples = bestFaceFrame?.let { (index, detected) ->
                    faceSamples(extracted.frames[index].bitmap, detected).take(1)
                }.orEmpty()
                return AnalysisOutput(result, faceSamples, embedIfEnabled(middle))
            } finally {
                extracted.frames.forEach { it.bitmap.recycle() }
            }
        }

        private fun embedIfEnabled(bitmap: Bitmap): FloatArray? =
            if (options.detectDuplicates) embedder?.let { runCatching { it.embed(bitmap) }.getOrNull() } else null

        /** Aligned crops of the biggest faces, embedded for clustering. */
        private fun faceSamples(bitmap: Bitmap, faces: List<MlKitVision.DetectedFace>): List<FaceSample> {
            val model = embedder ?: return emptyList()
            if (!options.faceGrouping) return emptyList()
            return faces
                .filter { it.box.width() >= FaceCrops.MIN_FACE_PX && it.box.height() >= FaceCrops.MIN_FACE_PX }
                .sortedByDescending { it.box.width() * it.box.height() }
                .take(MAX_FACES_PER_IMAGE)
                .mapNotNull { face ->
                    runCatching {
                        val crop = FaceCrops.aligned(bitmap, face.box, face.eyes)
                        try {
                            FaceSample(model.embed(crop), FaceCrops.thumbnailJpeg(crop))
                        } finally {
                            crop.recycle()
                        }
                    }.getOrNull()
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
        val faces: List<MlKitVision.DetectedFace>,
        val text: TextSummary?,
    )

    /** Perceptual hash (from a 72×64 thumbnail) and blur score (Laplacian variance at ≤512 px). */
    private fun pixelMetrics(bitmap: Bitmap): Pair<Long, Float> {
        val hashW = ImageMetrics.HASH_COLUMNS * 8
        val hashH = ImageMetrics.HASH_ROWS * 8
        val small = bitmap.scale(hashW, hashH)
        val hash = ImageMetrics.differenceHash(luminance(small), hashW, hashH)
        if (small !== bitmap) small.recycle()

        val factor = (SHARPNESS_SIZE.toFloat() / maxOf(bitmap.width, bitmap.height)).coerceAtMost(1f)
        val w = (bitmap.width * factor).toInt().coerceAtLeast(3)
        val h = (bitmap.height * factor).toInt().coerceAtLeast(3)
        val sharpSource = if (factor < 1f) bitmap.scale(w, h) else bitmap
        val sharpness = ImageMetrics.laplacianVariance(luminance(sharpSource), sharpSource.width, sharpSource.height)
        if (sharpSource !== bitmap) sharpSource.recycle()
        return hash to sharpness
    }

    private fun luminance(bitmap: Bitmap): FloatArray {
        val pixels = IntArray(bitmap.width * bitmap.height)
        bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
        return ImageMetrics.luminance(pixels)
    }

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
        const val MAX_FACES_PER_IMAGE = 4
        const val SHARPNESS_SIZE = 512
    }
}
