package com.nonverbal.mastery.smoke

import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performScrollToIndex
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nonverbal.mastery.MainActivity
import com.nonverbal.mastery.content.TestContent
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Drives a reader through mastering Level 1.1 using only the real UI. */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34], qualifiers = "w360dp-h780dp-xhdpi")
class MasteryFlowTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private val level = TestContent.curriculum.levels.getValue("foundations-01")
    private fun waitFor(text: String) = rule.waitUntilAtLeastOneExists(hasText(text, substring = true), timeoutMillis = 10_000)
    private fun list() = rule.onNode(hasScrollToNodeAction())
    private fun scrollTo(m: SemanticsMatcher) = list().performScrollToNode(m)

    @Test
    fun aReaderCanMasterTheBaseline() {
        waitFor("chapters mastered")
        rule.onAllNodes(hasText("Foundations")).onFirst().performClick()
        waitFor("Reading the body as a language")
        list().performScrollToNode(hasText("The Baseline"))
        rule.onAllNodes(hasText("The Baseline")).onFirst().performClick()
        waitFor("A gesture means nothing")

        // The mastery card lists exactly what remains.
        scrollTo(hasText("You still need to:"))
        rule.onNode(hasText("log a field mission")).assertExists()

        // Drills: tick every one.
        val done = SemanticsMatcher.expectValue(SemanticsProperties.StateDescription, "Drill complete")
        level.drills.forEach { drill ->
            val row = hasText(drill.name)
            scrollTo(row)
            rule.onNode(row).performClick()
            // Wait for the write to land in Room and come back through the flow.
            rule.waitUntilAtLeastOneExists(row and done, timeoutMillis = 5_000)
        }

        // Field mission: observed, hypothesis, disconfirmer.
        scrollTo(hasContentDescription("What did you see?", substring = true))
        rule.onNode(hasContentDescription("What did you see?", substring = true))
            .performTextInput("Woman on the train touched her face about twice a minute for five minutes, then not at all.")
        scrollTo(hasContentDescription("What did you guess it meant?"))
        rule.onNode(hasContentDescription("What did you guess it meant?")).performTextInput("She settled once her call ended.")
        scrollTo(hasContentDescription("What would have shown you were wrong?"))
        rule.onNode(hasContentDescription("What would have shown you were wrong?")).performTextInput("If the rate stayed high after the call.")
        scrollTo(hasText("Log this field note"))
        rule.onNode(hasText("Log this field note")).performClick()
        rule.waitForIdle()

        // Quiz: answer every question correctly.
        level.quiz.forEach { q ->
            val option = hasText(q.options[q.answer])
            scrollTo(option)
            rule.onNode(option).performClick()
            rule.waitForIdle()
        }
        scrollTo(hasText("That clears the bar", substring = true))

        // Reflection: at least 150 characters.
        scrollTo(hasContentDescription("Reflection", substring = true))
        rule.onNode(hasContentDescription("Reflection", substring = true)).performTextInput(
            "My manager, Dana, sits sideways with one foot on the rung of her chair in every one-to-one. " +
                "I once read her stillness during a budget review as anger. She was doing arithmetic in her head.",
        )
        waitFor("Enough to count")

        // Master the level.
        scrollTo(hasText("Master this level"))
        rule.onNode(hasText("Master this level")).performClick()
        waitFor("Level mastered. The next one is open.")
        // The next written chapter after 1.1, whichever that is as the book grows.
        val c = TestContent.curriculum
        val next = c.levelsInOrder("foundations")[1]
        rule.onNode(hasText("Open ${c.reference(next)}", substring = true)).assertExists()
    }
}
