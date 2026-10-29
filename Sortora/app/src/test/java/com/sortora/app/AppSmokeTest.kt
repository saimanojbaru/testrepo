package com.sortora.app

import androidx.annotation.StringRes
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isDialog
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.work.Configuration
import androidx.work.testing.SynchronousExecutor
import androidx.work.testing.WorkManagerTestInitHelper
import com.sortora.app.di.AppEntryPoint
import com.sortora.app.domain.model.AnalysisResult
import com.sortora.app.domain.model.ApplyStatus
import com.sortora.app.domain.model.OperationLogEntry
import com.sortora.app.domain.model.OperationMode
import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.DetectedLabel
import com.sortora.app.domain.model.MediaFile
import com.sortora.app.domain.model.MediaKind
import com.sortora.app.domain.model.MediaMetadata
import com.sortora.app.domain.model.SessionStatus
import dagger.hilt.android.EntryPointAccessors
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * Launches the real app (Hilt graph, Room, DataStore, WorkManager, navigation, Compose) on the JVM
 * and walks the main flow: onboarding → home → settings → history → preview of an analysed session
 * → smart filter → batch rename → rename-mode confirmation → undo from history.
 * ML Kit itself isn't exercised here (it needs a device); the analysis result is injected.
 */
@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [36], qualifiers = "w411dp-h891dp-xxhdpi")
class AppSmokeTest {

    @get:Rule
    val compose = createAndroidComposeRule<MainActivity>()

    private fun str(@StringRes id: Int) = compose.activity.getString(id)

    private fun waitForText(text: String, timeoutMs: Long = 10_000) =
        compose.waitUntilAtLeastOneExists(hasText(text, substring = true), timeoutMs)

    /**
     * Robolectric can't report a process name, so real WorkManager thinks it is in a secondary
     * process and never runs jobs. Use WorkManager's test setup with the app's Hilt worker factory.
     */
    @Before
    fun setUpWorkManager() {
        val app = ApplicationProvider.getApplicationContext<SortoraApp>()
        WorkManagerTestInitHelper.initializeTestWorkManager(
            app,
            Configuration.Builder().setWorkerFactory(app.workerFactory).setExecutor(SynchronousExecutor()).build(),
        )
    }

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

        // Smart filter + batch rename with a pattern (default "{date}_{label}_{counter}").
        compose.onNodeWithText("${str(R.string.filter_video)} (1)").performClick()
        compose.onNodeWithContentDescription(str(R.string.action_more)).performClick()
        compose.onNodeWithText(str(R.string.bulk_rename)).performClick()
        compose.onNodeWithText(str(R.string.action_apply)).performClick()
        waitForText("2025-10-12_person_001.mp4")

        // Rename-in-place mode needs no output folder but asks for confirmation.
        compose.onNodeWithText(str(R.string.mode_rename)).performClick()
        waitForText("Rename 1 file")
        compose.onNodeWithText("Rename 1 file").performClick()
        waitForText(str(R.string.preview_rename_confirm_action))
        compose.onNodeWithText(str(R.string.action_cancel)).performClick()

        // History → Undo runs through the real WorkManager worker and marks the entry undone.
        compose.onNodeWithContentDescription(str(R.string.action_back)).performClick()
        waitForText(str(R.string.privacy_banner_title))
        compose.onNodeWithContentDescription(str(R.string.history_title)).performClick()
        runBlocking {
            deps().history().record(
                OperationLogEntry(
                    batchId = 99, timestamp = System.currentTimeMillis(), mode = OperationMode.COPY,
                    originalName = "IMG_1.jpg", originalUri = "content://test/src", newName = "2025-10-12_dog.jpg",
                    targetFolder = "Pets", targetUri = "content://test/copy", status = ApplyStatus.SUCCESS, message = null,
                ),
            )
        }
        waitForText(str(R.string.action_undo))
        compose.onAllNodesWithText(str(R.string.action_undo)).onFirst().performClick()
        waitForText(str(R.string.undo_confirm_copy))
        compose.onNode(hasText(str(R.string.action_undo)) and hasAnyAncestor(isDialog())).performClick()
        waitForText("1 undone", timeoutMs = 30_000)
    }

    private fun deps() = EntryPointAccessors.fromApplication(compose.activity.applicationContext, AppEntryPoint::class.java)
}
