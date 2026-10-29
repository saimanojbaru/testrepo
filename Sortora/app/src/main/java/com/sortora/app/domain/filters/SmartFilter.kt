package com.sortora.app.domain.filters

import com.sortora.app.domain.classify.ImageMetrics
import com.sortora.app.domain.classify.ScreenshotHeuristics
import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.MediaKind
import com.sortora.app.domain.model.SessionItem

/** Quick filters in the preview. Several can be combined (all must match). */
enum class SmartFilter {
    HAS_FACE,
    SCREENSHOT,
    HAS_TEXT,
    LANDSCAPE,
    PRODUCT,
    BLURRY,
    DUPLICATE,
    VIDEO,
    ;

    fun matches(item: SessionItem): Boolean {
        val analysis = item.analysis ?: return false
        return when (this) {
            HAS_FACE -> analysis.faces.count > 0 || (analysis.video?.framesWithFaces ?: 0) > 0
            SCREENSHOT -> analysis.category == ContentCategory.SCREENSHOT ||
                analysis.category == ContentCategory.SCREEN_RECORDING ||
                analysis.screenshotScore >= ScreenshotHeuristics.THRESHOLD
            HAS_TEXT -> analysis.text.charCount >= MIN_TEXT_CHARS
            LANDSCAPE -> analysis.category == ContentCategory.LANDSCAPE
            PRODUCT -> analysis.category == ContentCategory.PRODUCT
            BLURRY -> isBlurry(analysis.sharpness)
            DUPLICATE -> item.duplicate != null
            VIDEO -> item.file.kind == MediaKind.VIDEO
        }
    }

    companion object {
        const val MIN_TEXT_CHARS = 20

        fun isBlurry(sharpness: Float?): Boolean = sharpness != null && sharpness < ImageMetrics.BLUR_THRESHOLD

        fun apply(items: List<SessionItem>, filters: Set<SmartFilter>): List<SessionItem> =
            if (filters.isEmpty()) items else items.filter { item -> filters.all { it.matches(item) } }
    }
}
