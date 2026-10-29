package com.sortora.app

import androidx.annotation.StringRes
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.sortora.app.di.AppEntryPoint
import com.sortora.app.domain.model.AnalysisResult
import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.DetectedLabel
import com.sortora.app.domain.model.MediaFile
import com.sortora.app.domain.model.MediaKind
import com.sortora.app.domain.model.MediaMetadata
import com.sortora.app.domain.model.SessionStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Launches the real app (Hilt graph, Room, DataStore, WorkManager, navigation, Compose) on the JVM
 * and walks the main flow: onboarding → home → settings → history → preview of an analysed session.
 * ML Kit itself isn't exercised here (it needs a device); the analysis result is injected.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun str(@StringRes id: Int) = compose.activity.getString(id)

    private fun waitForText(text: String) = compose.waitUntilAtLeastOneExists(hasText(text, substring = true), 10_000)

    @Test
    fun mainFlowRendersWithoutCrashing() {
        // Onboarding → Home
        waitForText(str(R.string.onboarding_1_title))
        compose.onNodeWithText(str(R.string.action_skip)).performClick()
        waitForText(str(R.string.privacy_banner_title))
        compose.onNodeWithText(str(R.string.home_pick_files)).assertExists()

        // Settings shows the live naming example from the spec.
        compose.onNodeWithContentDescription(str(R.string.settings_title)).performClick()
        waitForText("2025-10-12_talkinghead_person_desk.mp4")
        compose.onNodeWithContentDescription(str(R.string.action_back)).performClick()

        // History starts empty.
        compose.onNodeWithContentDescription(str(R.string.history_title)).performClick()
        waitForText(str(R.string.history_empty_title))
        compose.onNodeWithContentDescription(str(R.string.action_back)).performClick()

        // Inject an analysed session and open it from the "continue" card.
        val sessions = EntryPointAccessors.fromApplication(compose.activity.applicationContext, AppEntryPoint::class.java).sessions()
        runBlocking {
            val id = sessions.createSession(
                "Test",
                listOf(MediaFile("content://test/video/1", "VID_20251012_143015.mp4", "video/mp4", 1_000, null, MediaKind.VIDEO)),
            )
            val date = LocalDateTime.of(2025, 10, 12, 14, 30, 15).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
            sessions.saveAnalysis(
                sessions.getItems(id).single().id,
                AnalysisResult(
                    category = ContentCategory.TALKING_HEAD,
                    confidence = 0.9f,
                    labels = listOf(DetectedLabel("Desk", 0.8f)),
                    metadata = MediaMetadata(captureTimeMillis = date),
                    tags = listOf("person", "desk"),
                ),
            )
            sessions.setStatus(id, SessionStatus.ANALYZED)
        }
        waitForText(str(R.string.home_resume_title))
        compose.onNodeWithText(str(R.string.action_resume)).performClick()

        // Preview: suggested name and folder, then the edit sheet.
        waitForText("2025-10-12_talkinghead_person_desk.mp4")
        compose.onNodeWithText("2025-10-12_talkinghead_person_desk.mp4").performClick()
        waitForText(str(R.string.preview_new_name))
        compose.onNodeWithText(str(R.string.action_cancel)).performClick()

        // Applying without an output folder asks for one instead of doing anything.
        compose.onNodeWithText("Copy 1 file").performClick()
        waitForText(str(R.string.preview_need_destination))
    }
}
