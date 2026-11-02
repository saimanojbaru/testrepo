package com.nonverbal.mastery.smoke

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.printToString
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.nonverbal.mastery.MainActivity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * Launches the real activity (Room, DataStore, the assembled content pack, type-safe navigation)
 * and walks the main routes. This is the stand-in for running on a device in CI.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [26, 34], qualifiers = "w360dp-h780dp-xhdpi")
class AppSmokeTest {
    @get:Rule
    val rule = createAndroidComposeRule<MainActivity>()

    private fun waitFor(text: String) {
        try {
            rule.waitUntilAtLeastOneExists(hasText(text, substring = true), timeoutMillis = 10_000)
        } catch (e: Throwable) {
            println("UI TREE when waiting for '$text':\n" + rule.onRoot(useUnmergedTree = true).printToString())
            throw e
        }
    }

    @Test
    fun launchesAndWalksTheBook() {
        // Path: content loaded, header and contents present.
        waitFor("chapters mastered")
        rule.onAllNodes(hasText("Foundations")).onFirst().performClick()

        // Category: the written chapter is open; others are listed.
        waitFor("Reading the body as a language")
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("The Baseline"))
        rule.onAllNodes(hasText("The Baseline")).onFirst().performClick()

        // Level: the chapter opens on its hook and its tabs show mastery state.
        waitFor("A gesture means nothing")
        rule.onAllNodes(hasText("Assess", substring = true)).onFirst().performClick()
        waitFor("Question 1 of")

        // Overflow menu reaches the ethics statement from inside a reading view.
        rule.onNode(hasContentDescription("More")).performClick()
        rule.onNode(hasText("Read the ethics statement")).performClick()
        waitFor("On Reading People")
        rule.onAllNodes(hasContentDescription("Back")).onFirst().performClick()
        waitFor("Question 1 of")
        rule.onAllNodes(hasContentDescription("Back")).onFirst().performClick()
        rule.onAllNodes(hasContentDescription("Back")).onFirst().performClick()

        // Home again: pose library, settings, crisis resources.
        waitFor("chapters mastered")
        rule.onNode(hasContentDescription("Pose library")).performClick()
        waitFor("Plates")
        rule.onAllNodes(hasContentDescription("Back")).onFirst().performClick()
        rule.onNode(hasContentDescription("Settings")).performClick()
        waitFor("Match the system")
        rule.onAllNodes(hasContentDescription("Back")).onFirst().performClick()
        rule.onNode(hasScrollToNodeAction()).performScrollToNode(hasText("If you're struggling right now"))
        rule.onNode(hasText("If you're struggling right now")).performClick()
        waitFor("988")
    }
}
