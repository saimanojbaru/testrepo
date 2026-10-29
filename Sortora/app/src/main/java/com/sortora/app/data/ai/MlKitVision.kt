package com.sortora.app.data.ai

import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.google.mlkit.vision.objects.ObjectDetection
import com.google.mlkit.vision.objects.defaults.ObjectDetectorOptions
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.sortora.app.domain.classify.OcrLine
import com.sortora.app.domain.classify.TextAnalysis
import com.sortora.app.domain.model.DetectedLabel
import com.sortora.app.domain.model.FaceSummary
import com.sortora.app.domain.model.LabelSource
import com.sortora.app.domain.model.TextSummary
import kotlinx.coroutines.tasks.await
import java.io.Closeable

/**
 * Thin coroutine wrapper around the four ML Kit detectors Sortora uses.
 *
 * All models are the *bundled* variants: they ship inside the APK and run fully offline. No
 * model is downloaded through Google Play services.
 *
 * Instances hold native resources; create one per batch and [close] it afterwards.
 */
class MlKitVision(enableOcr: Boolean) : Closeable {

    private val labeler = ImageLabeling.getClient(
        ImageLabelerOptions.Builder().setConfidenceThreshold(LABEL_THRESHOLD).build(),
    )
    private val objectDetector = ObjectDetection.getClient(
        ObjectDetectorOptions.Builder()
            .setDetectorMode(ObjectDetectorOptions.SINGLE_IMAGE_MODE)
            .enableMultipleObjects()
            .enableClassification()
            .build(),
    )
    private val faceDetector = FaceDetection.getClient(
        FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
            .setMinFaceSize(0.05f)
            .build(),
    )
    private val textRecognizer = if (enableOcr) TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) else null

    val ocrEnabled: Boolean get() = textRecognizer != null

    data class Objects(val labels: List<DetectedLabel>, val count: Int, val largestArea: Float)

    suspend fun labels(image: InputImage): List<DetectedLabel> =
        labeler.process(image).await().map { DetectedLabel(it.text, it.confidence, LabelSource.IMAGE_LABEL) }

    suspend fun objects(image: InputImage): Objects {
        val (width, height) = uprightSize(image)
        val detected = objectDetector.process(image).await()
        val area = (width.toFloat() * height).coerceAtLeast(1f)
        val largest = detected.maxOfOrNull { it.boundingBox.width().toFloat() * it.boundingBox.height() / area } ?: 0f
        val labels = detected.flatMap { obj -> obj.labels.map { DetectedLabel(it.text, it.confidence, LabelSource.OBJECT) } }
        return Objects(labels, detected.size, largest.coerceIn(0f, 1f))
    }

    suspend fun faces(image: InputImage): FaceSummary {
        val (width, height) = uprightSize(image)
        val faces = faceDetector.process(image).await()
        if (faces.isEmpty()) return FaceSummary.NONE
        val largest = faces.maxBy { it.boundingBox.width() * it.boundingBox.height() }.boundingBox
        val area = largest.width().toFloat() * largest.height() / (width.toFloat() * height).coerceAtLeast(1f)
        return FaceSummary(
            count = faces.size,
            largestFaceArea = area.coerceIn(0f, 1f),
            largestFaceCenterX = (largest.exactCenterX() / width.coerceAtLeast(1)).coerceIn(0f, 1f),
        )
    }

    /** Returns null when OCR is turned off in Settings. */
    suspend fun text(image: InputImage): TextSummary? {
        val recognizer = textRecognizer ?: return null
        val (width, height) = uprightSize(image)
        val result = recognizer.process(image).await()
        val lines = result.textBlocks.flatMap { block ->
            block.lines.mapNotNull { line ->
                val box = line.boundingBox ?: return@mapNotNull null
                OcrLine(line.text, box.left, box.top, box.right, box.bottom)
            }
        }
        return TextAnalysis.summarize(lines, width, height)
    }

    override fun close() {
        labeler.close()
        objectDetector.close()
        faceDetector.close()
        textRecognizer?.close()
    }

    /** ML Kit reports boxes in upright coordinates, so swap sides for 90°/270° inputs. */
    private fun uprightSize(image: InputImage): Pair<Int, Int> =
        if (image.rotationDegrees == 90 || image.rotationDegrees == 270) image.height to image.width else image.width to image.height

    private companion object {
        const val LABEL_THRESHOLD = 0.5f
    }
}
