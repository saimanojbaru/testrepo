package com.sortora.app.domain

import com.sortora.app.domain.classify.SceneClassifier
import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.FaceSummary
import com.sortora.app.domain.model.TextSummary
import com.sortora.app.domain.model.VideoStats
import org.junit.Assert.assertEquals
import org.junit.Test

class SceneClassifierTest {

    private fun image(
        labels: List<com.sortora.app.domain.model.DetectedLabel> = emptyList(),
        faces: FaceSummary = FaceSummary.NONE,
        text: TextSummary = TextSummary.NONE,
        screenshot: Float = 0f,
        objectArea: Float = 0f,
    ) = SceneClassifier.classifyImage(labels, faces, text, screenshot, objectArea).category

    @Test
    fun `high screenshot score wins`() {
        assertEquals(ContentCategory.SCREENSHOT, image(faces = FaceSummary(1, 0.3f), screenshot = 0.8f))
    }

    @Test
    fun `dense text is a document`() {
        assertEquals(ContentCategory.DOCUMENT, image(text = TextSummary(charCount = 400, lineCount = 20, coverage = 0.3f)))
    }

    @Test
    fun `faces drive people categories`() {
        assertEquals(ContentCategory.SELFIE, image(faces = FaceSummary(1, 0.25f)))
        assertEquals(ContentCategory.PEOPLE, image(faces = FaceSummary(2, 0.05f)))
        assertEquals(ContentCategory.GROUP, image(faces = FaceSummary(5, 0.03f)))
    }

    @Test
    fun `tiny background faces do not hide the scene`() {
        val labels = listOf(label("Dog", 0.92f))
        assertEquals(ContentCategory.PET, image(labels = labels, faces = FaceSummary(1, 0.004f)))
    }

    @Test
    fun `scene labels map to categories`() {
        assertEquals(ContentCategory.LANDSCAPE, image(labels = listOf(label("Sky", 0.9f), label("Mountain", 0.8f))))
        assertEquals(ContentCategory.FOOD, image(labels = listOf(label("Food", 0.85f), label("Pizza", 0.7f))))
        assertEquals(ContentCategory.CITY, image(labels = listOf(label("Skyscraper", 0.8f))))
    }

    @Test
    fun `a single prominent object is a product shot`() {
        assertEquals(ContentCategory.PRODUCT, image(labels = listOf(label("Shoe", 0.6f)), objectArea = 0.4f))
    }

    @Test
    fun `nothing recognisable is other`() {
        assertEquals(ContentCategory.OTHER, image(labels = listOf(label("Pattern", 0.6f))))
    }

    @Test
    fun `steady single face video is a talking head`() {
        val stats = VideoStats(
            sampledFrames = 5, framesWithFaces = 5, framesWithSingleFace = 5,
            averageFaceArea = 0.08f, averageFaceCenterX = 0.5f, averageFrameDifference = 0.03f,
        )
        assertEquals(ContentCategory.TALKING_HEAD, SceneClassifier.classifyVideo(stats, 0f).category)
    }

    @Test
    fun `moving footage without faces is b-roll`() {
        val stats = VideoStats(sampledFrames = 5, framesWithFaces = 0, averageFrameDifference = 0.25f)
        assertEquals(ContentCategory.B_ROLL, SceneClassifier.classifyVideo(stats, 0f).category)
    }

    @Test
    fun `face in a fast moving vlog is still b-roll`() {
        val stats = VideoStats(
            sampledFrames = 5, framesWithFaces = 2, framesWithSingleFace = 2,
            averageFaceArea = 0.03f, averageFrameDifference = 0.3f,
        )
        assertEquals(ContentCategory.B_ROLL, SceneClassifier.classifyVideo(stats, 0f).category)
    }

    @Test
    fun `screen recordings are detected`() {
        assertEquals(ContentCategory.SCREEN_RECORDING, SceneClassifier.classifyVideo(VideoStats(sampledFrames = 5), 0.9f).category)
    }
}
