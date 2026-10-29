package com.sortora.app.domain

import com.sortora.app.domain.classify.ScreenshotHeuristics
import com.sortora.app.domain.classify.ScreenshotHeuristics.THRESHOLD
import com.sortora.app.domain.model.MediaKind
import com.sortora.app.domain.model.MediaMetadata
import com.sortora.app.domain.model.TextSummary
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotHeuristicsTest {

    private val screens = listOf(1080 to 2400)

    private fun score(
        name: String,
        mime: String = "image/png",
        path: String? = null,
        meta: MediaMetadata = MediaMetadata(width = 1080, height = 2400),
        text: TextSummary? = null,
        kind: MediaKind = MediaKind.IMAGE,
    ) = ScreenshotHeuristics.score(ScreenshotHeuristics.Input(name, mime, path, kind, meta, screens, text))

    @Test
    fun `android screenshot names are detected`() {
        assertTrue(score("Screenshot_20251012-143015_Chrome.jpg", "image/jpeg") >= THRESHOLD)
    }

    @Test
    fun `screenshots folder is detected`() {
        assertTrue(score("IMG_1.png", path = "Pictures/Screenshots") >= THRESHOLD)
    }

    @Test
    fun `ios screenshots are detected by exif comment`() {
        assertTrue(score("IMG_0042.PNG", meta = MediaMetadata(width = 1170, height = 2532, userComment = "Screenshot")) >= THRESHOLD)
    }

    @Test
    fun `renamed screenshot found by visual cues`() {
        val text = TextSummary(charCount = 300, lineCount = 12, coverage = 0.15f, hasStatusBar = true)
        assertTrue(score("IMG-20251012-WA0003.jpg", "image/jpeg", meta = MediaMetadata(width = 720, height = 1600), text = text) >= THRESHOLD)
    }

    @Test
    fun `camera photos are not screenshots`() {
        val meta = MediaMetadata(width = 4000, height = 3000, cameraMake = "Google", cameraModel = "Pixel 9")
        assertTrue(score("PXL_20251012_143015123.jpg", "image/jpeg", meta = meta) < THRESHOLD)
    }

    @Test
    fun `a wallpaper with the screen size is not enough on its own`() {
        assertTrue(score("wallpaper.png") < THRESHOLD)
    }

    @Test
    fun `screen recordings by name`() {
        assertTrue(score("Screen_Recording_20251012-143015.mp4", "video/mp4", kind = MediaKind.VIDEO) >= THRESHOLD)
    }
}
