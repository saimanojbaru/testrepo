package com.sortora.app.domain

import com.sortora.app.domain.classify.OcrLine
import com.sortora.app.domain.classify.TagSelector
import com.sortora.app.domain.classify.TextAnalysis
import com.sortora.app.domain.history.LogCsv
import com.sortora.app.domain.model.ApplyStatus
import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.FaceSummary
import com.sortora.app.domain.model.OperationLogEntry
import com.sortora.app.domain.model.OperationMode
import com.sortora.app.domain.model.TextSummary
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TextAnalysisTest {

    @Test
    fun `status bar clock, title and keywords are extracted from a screenshot`() {
        val lines = listOf(
            OcrLine("12:45", 40, 20, 140, 60),          // status bar
            OcrLine("Settings", 60, 200, 420, 290),     // large title
            OcrLine("Network & internet", 60, 400, 600, 440),
            OcrLine("Connected devices", 60, 500, 600, 540),
        )
        val summary = TextAnalysis.summarize(lines, 1080, 2400)
        assertTrue(summary.hasStatusBar)
        assertEquals("Settings", summary.title)
        assertTrue("network" in summary.keywords)
    }

    @Test
    fun `photos of text without a status bar`() {
        val summary = TextAnalysis.summarize(listOf(OcrLine("TOTAL 12:45", 100, 1500, 600, 1560)), 1080, 2400)
        assertFalse(summary.hasStatusBar)
    }

    @Test
    fun `talking head tags start with person`() {
        val tags = TagSelector.select(ContentCategory.TALKING_HEAD, listOf(label("Desk", 0.8f), label("Smile", 0.9f)), FaceSummary.NONE, TextSummary.NONE, hasFacesInVideo = true)
        assertEquals(listOf("person", "desk"), tags.take(2))
    }

    @Test
    fun `tags skip generic labels and repeats of the category`() {
        val tags = TagSelector.select(ContentCategory.FOOD, listOf(label("Food", 0.95f), label("Pizza", 0.9f), label("Font", 0.9f)), FaceSummary.NONE, TextSummary.NONE)
        assertEquals(listOf("pizza"), tags)
    }

    @Test
    fun `csv log escapes quotes, commas and formulas`() {
        val csv = LogCsv.format(
            listOf(OperationLogEntry(0, 1, millis(2025, 10, 12), OperationMode.COPY, "a,\"b\".jpg", "", "=cmd.jpg", "Pets", null, ApplyStatus.SUCCESS, null)),
            UTC,
        )
        assertTrue(csv.lines()[1].contains("\"a,\"\"b\"\".jpg\""))
        assertTrue(csv.lines()[1].contains("'=cmd.jpg"))
    }
}
