package com.hitit.domain.coach

import org.junit.Assert.assertTrue
import org.junit.Test

class CoachPromptTest {

    @Test
    fun promptIncludesEveryFindingAndTheGuardrails() {
        val insights = listOf(
            CoachInsight(InsightTone.CRITICAL, "Rough week", "You barely showed up."),
            CoachInsight(InsightTone.WARNING, "Weekends wreck you", "You drop 30 points."),
        )
        val prompt = CoachPrompt.build(insights)
        assertTrue(prompt.contains("Rough week"))
        assertTrue(prompt.contains("Weekends wreck you"))
        assertTrue(prompt.contains("brutally honest"))
        assertTrue(prompt.contains("max 80 words"))
        assertTrue(prompt.contains("Do not invent facts"))
    }
}
