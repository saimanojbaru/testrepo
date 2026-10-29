package com.sortora.app.domain.classify

import com.sortora.app.domain.model.MediaKind
import com.sortora.app.domain.model.MediaMetadata
import com.sortora.app.domain.model.TextSummary

/**
 * Scores how likely a file is a screenshot (images) or a screen recording (videos), from 0 to 1.
 *
 * Combines metadata cues (file name, folder, EXIF, exact screen resolution) with visual cues
 * (a status-bar clock found by OCR, text density). No single cue is required.
 */
object ScreenshotHeuristics {

    const val THRESHOLD = 0.6f

    data class Input(
        val displayName: String,
        val mimeType: String,
        val pathHint: String?,
        val kind: MediaKind,
        val metadata: MediaMetadata,
        /** Physical pixel sizes of this device's displays (any orientation). */
        val screenSizes: List<Pair<Int, Int>> = emptyList(),
        val text: TextSummary? = null,
        /** Videos only: fraction of sampled frames that were mostly text. */
        val textHeavyFrameRatio: Float = 0f,
    )

    // Compared against the name with spaces, underscores, dashes and dots removed.
    private val screenshotNameHints = listOf(
        "screenshot", "screencap", "scrnshot", "screengrab", "bildschirmfoto",
        "capturadepantalla", "capturedecran", "schermata", "schermafbeelding",
        "スクリーンショット", "截屏", "截图", "스크린샷",
    )
    private val recordingNameHints = listOf(
        "screenrecord", "screenrecording", "screencast", "screencapture", "bildschirmaufnahme",
        "grabaciondepantalla", "enregistrementdecran",
    )

    fun score(input: Input): Float {
        val compactName = TextNormalizer.normalize(input.displayName).replace(" ", "")
        val path = input.pathHint?.lowercase().orEmpty()
        val meta = input.metadata
        var score = 0f

        if (input.kind == MediaKind.IMAGE) {
            if (screenshotNameHints.any { it in compactName }) score += 0.7f
            if ("screenshot" in path) score += 0.6f
            if (meta.userComment?.contains("screenshot", ignoreCase = true) == true) score += 0.7f
            if (meta.software?.contains("screenshot", ignoreCase = true) == true) score += 0.5f
        } else {
            if (recordingNameHints.any { it in compactName }) score += 0.7f
            if ("screenrecord" in path.replace(" ", "") || "screen recording" in path) score += 0.6f
            if (input.textHeavyFrameRatio >= 0.5f) score += 0.25f
        }

        if (matchesScreen(meta.width, meta.height, input.screenSizes)) score += 0.25f

        if (meta.hasCameraInfo) {
            score -= 0.5f
        } else {
            score += 0.05f
            if (input.kind == MediaKind.IMAGE && input.mimeType.equals("image/png", ignoreCase = true)) score += 0.1f
            if ((meta.aspectRatio ?: 0f) >= 1.9f) score += 0.1f
        }

        input.text?.let { text ->
            if (text.hasStatusBar) score += 0.3f
            if (text.lineCount >= 5) score += 0.1f
            if (text.coverage >= 0.1f) score += 0.1f
        }

        return score.coerceIn(0f, 1f)
    }

    private fun matchesScreen(width: Int?, height: Int?, screens: List<Pair<Int, Int>>): Boolean {
        if (width == null || height == null) return false
        return screens.any { (w, h) -> (w == width && h == height) || (w == height && h == width) }
    }
}
