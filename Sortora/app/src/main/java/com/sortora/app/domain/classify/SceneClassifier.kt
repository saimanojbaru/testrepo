package com.sortora.app.domain.classify

import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.DetectedLabel
import com.sortora.app.domain.model.FaceSummary
import com.sortora.app.domain.model.TextSummary
import com.sortora.app.domain.model.VideoStats

/** A category decision plus how confident the rules were (0..1). */
data class Classification(val category: ContentCategory, val confidence: Float)

/**
 * Rule-based scene classification on top of the on-device model outputs.
 *
 * ML Kit gives us generic labels, faces, objects and text; these rules turn them into the
 * categories people actually sort by. Thresholds were picked to favour precision: when in
 * doubt a file goes to "Other" rather than the wrong folder.
 *
 * To plug in a custom TensorFlow Lite scene model later, run it in the analyzer and feed its
 * labels in alongside ML Kit's: everything below works on plain [DetectedLabel]s.
 */
object SceneClassifier {

    /** Faces smaller than this fraction of the frame are background people, not the subject. */
    private const val MIN_SUBJECT_FACE = 0.015f
    private const val SELFIE_FACE = 0.12f
    private const val SCENE_THRESHOLD = 0.55f

    fun classifyImage(
        labels: List<DetectedLabel>,
        faces: FaceSummary,
        text: TextSummary,
        screenshotScore: Float,
        /** Area (0..1) of the largest detected object, 0 if none. */
        prominentObjectArea: Float = 0f,
    ): Classification {
        if (screenshotScore >= ScreenshotHeuristics.THRESHOLD) {
            return Classification(ContentCategory.SCREENSHOT, screenshotScore)
        }

        val scores = LabelVocabulary.categoryScores(labels).toMutableMap()
        val documentScore = documentScore(text, scores[ContentCategory.DOCUMENT] ?: 0f)
        if (documentScore >= 0.7f && faces.largestFaceArea < 0.1f) {
            return Classification(ContentCategory.DOCUMENT, documentScore.coerceAtMost(1f))
        }

        val subjectFaces = faces.count > 0 && faces.largestFaceArea >= MIN_SUBJECT_FACE
        if (faces.count >= 3 && subjectFaces) {
            return Classification(ContentCategory.GROUP, if (faces.count >= 4) 0.9f else 0.8f)
        }
        if (subjectFaces) {
            val selfieLabel = labels.firstOrNull { it.text.equals("selfie", ignoreCase = true) }?.confidence ?: 0f
            if (selfieLabel >= 0.6f || (faces.count == 1 && faces.largestFaceArea >= SELFIE_FACE)) {
                return Classification(ContentCategory.SELFIE, maxOf(selfieLabel, 0.6f + faces.largestFaceArea).coerceAtMost(1f))
            }
            return Classification(ContentCategory.PEOPLE, 0.7f)
        }

        // A single clear object on a clean background with no people reads as a product shot.
        if (faces.count == 0 && prominentObjectArea in 0.12f..0.9f) {
            scores[ContentCategory.PRODUCT] = (scores[ContentCategory.PRODUCT] ?: 0f) + 0.3f
        }
        val sceneCategories = listOf(
            ContentCategory.FOOD, ContentCategory.PET, ContentCategory.PRODUCT,
            ContentCategory.CITY, ContentCategory.LANDSCAPE,
        )
        val bestScene = sceneCategories
            .map { it to (scores[it] ?: 0f) }
            .maxByOrNull { it.second }
        if (bestScene != null && bestScene.second >= SCENE_THRESHOLD) {
            return Classification(bestScene.first, bestScene.second.coerceAtMost(1f))
        }

        if (faces.count > 0) return Classification(ContentCategory.PEOPLE, 0.5f)
        if (documentScore >= 0.45f) return Classification(ContentCategory.DOCUMENT, documentScore)
        return Classification(ContentCategory.OTHER, 0.3f)
    }

    /**
     * Video rules. "Talking head" = the same single face, reasonably large and roughly centred, in
     * most sampled frames, with little change between frames (locked-off camera). Everything else
     * that isn't a screen recording is treated as B-roll.
     */
    fun classifyVideo(stats: VideoStats, screenRecordingScore: Float): Classification {
        if (screenRecordingScore >= ScreenshotHeuristics.THRESHOLD) {
            return Classification(ContentCategory.SCREEN_RECORDING, screenRecordingScore)
        }
        if (stats.sampledFrames == 0) return Classification(ContentCategory.OTHER, 0.2f)

        val faceSize = (stats.averageFaceArea / 0.08f).coerceAtMost(1f)
        val stillness = 1f - (stats.averageFrameDifference / 0.2f).coerceAtMost(1f)
        var talkingHead = stats.faceFrameRatio * 0.4f +
            stats.singleFaceRatio * 0.2f +
            faceSize * 0.2f +
            stillness * 0.2f
        if (stats.averageFaceCenterX !in 0.2f..0.8f) talkingHead -= 0.1f

        return if (talkingHead >= 0.6f && stats.faceFrameRatio >= 0.5f && stats.averageFaceArea >= 0.02f) {
            Classification(ContentCategory.TALKING_HEAD, talkingHead.coerceAtMost(1f))
        } else {
            Classification(ContentCategory.B_ROLL, (1f - talkingHead).coerceIn(0.4f, 0.95f))
        }
    }

    private fun documentScore(text: TextSummary, documentLabelScore: Float): Float {
        val density = when {
            text.charCount >= 200 || text.coverage >= 0.15f -> 1f
            text.charCount >= 80 || text.coverage >= 0.08f -> 0.7f
            text.charCount >= 30 -> 0.3f
            else -> 0f
        }
        return density * 0.8f + documentLabelScore.coerceAtMost(1f) * 0.5f
    }
}
