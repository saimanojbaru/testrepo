package com.nonverbal.mastery.snapshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import app.cash.paparazzi.DeviceConfig
import app.cash.paparazzi.Paparazzi
import com.android.resources.Density
import com.nonverbal.mastery.content.TestContent
import com.nonverbal.mastery.data.db.LevelProgress
import com.nonverbal.mastery.data.db.LevelStatus
import com.nonverbal.mastery.data.db.PoseBookmark
import com.nonverbal.mastery.data.repo.Codec
import com.nonverbal.mastery.data.repo.DesignPrefs
import com.nonverbal.mastery.data.repo.FieldNote
import com.nonverbal.mastery.data.repo.QuizAttempt
import com.nonverbal.mastery.data.repo.ReviewScheduler
import com.nonverbal.mastery.design.components.EmptyState
import com.nonverbal.mastery.design.components.ErrorState
import com.nonverbal.mastery.design.components.LocalPoseLibrary
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.theme.NonverbalTheme
import com.nonverbal.mastery.features.category.CategoryScreen
import com.nonverbal.mastery.features.launch.LaunchScreen
import com.nonverbal.mastery.features.level.Kind
import com.nonverbal.mastery.features.level.LevelActions
import com.nonverbal.mastery.features.level.LevelScreen
import com.nonverbal.mastery.features.level.LevelState
import com.nonverbal.mastery.features.level.planLevel
import com.nonverbal.mastery.features.path.PathActions
import com.nonverbal.mastery.features.path.PathScreen
import com.nonverbal.mastery.features.poselib.PoseDetailScreen
import com.nonverbal.mastery.features.poselib.PoseLibraryScreen
import com.nonverbal.mastery.features.progress.ProgressScreen
import com.nonverbal.mastery.features.progress.ReflectionsScreen
import com.nonverbal.mastery.features.review.ReviewScreen
import com.nonverbal.mastery.features.settings.CrisisScreen
import com.nonverbal.mastery.features.settings.EthicsScreen
import com.nonverbal.mastery.features.settings.SettingsScreen
import org.junit.Rule
import org.junit.Test
import java.util.concurrent.TimeUnit

/** Renders every screen at 360dp in both palettes, so the design can be reviewed as images. */
class ScreensTest {
    @get:Rule
    val paparazzi = Paparazzi(
        deviceConfig = DeviceConfig.PIXEL_5.copy(screenWidth = 540, screenHeight = 1170, density = Density.HIGH, softButtons = false),
        maxPercentDifference = 0.1,
    )

    private val c = TestContent.curriculum
    private val day = TimeUnit.DAYS.toMillis(1)
    private val now = 1_790_000_000_000L
    private val l1 = c.levels.getValue("foundations-01")
    private val l11 = c.levels.getValue("foundations-11")

    private val progress: Map<String, LevelProgress> = mapOf(
        l1.id to ReviewScheduler.onMastered(
            LevelProgress(l1.id, l1.categoryId, LevelStatus.MASTERED, masteredAt = now - 2 * day,
                reflection = "My father folds his arms at every meal. I used to think it was aimed at me. It was his back, and probably always had been. What changed the evening was his hand at his neck when my sister mentioned moving away.",
                drillsDone = 31, fieldLogged = true),
            now - 2 * day,
        ),
        l11.id to LevelProgress(
            l11.id, l11.categoryId, LevelStatus.IN_PROGRESS, lastOpenedAt = now - 3600_000,
            drillsDone = 0b00101,
            quizAnswersJson = Codec.encodeAttempts(listOf(QuizAttempt(now, listOf(2, 1, 0, null, null, null)))),
            reflection = "On Thursday, in the budget meeting, my right hand went to my jaw three times",
            fieldNote = Codec.encodeFieldNote(FieldNote(0, observed = "Minister's hand to throat right after the question on costs.")),
        ),
    )

    private val pathActions = PathActions({}, {}, {}, {}, {}, {}, {}, {})
    private val levelActions = LevelActions({}, { _, _ -> }, {}, {}, {}, {})

    private fun shot(name: String, dark: Boolean = false, content: @Composable () -> Unit) {
        paparazzi.snapshot(name + if (dark) "_ink" else "_paper") {
            NonverbalTheme(darkTheme = dark, reducedMotion = true) {
                CompositionLocalProvider(LocalPoseLibrary provides c.poses) {
                    Box(Modifier.fillMaxSize().background(Nv.colors.background)) { content() }
                }
            }
        }
    }

    @Composable
    private fun level(levelId: String, kind: Kind, i: Int = 0, p: Map<String, LevelProgress> = progress, justMastered: Boolean = false, scrollPx: Int = 0) {
        val lv = c.levels.getValue(levelId)
        val plan = planLevel(lv)
        val index = plan.indexOfFirst { it.kind == kind && it.i == i }.let { if (it < 0) 0 else it + 2 }
        val pr = p[levelId]
        LevelScreen(
            c, lv,
            LevelState(pr, pr?.reflection.orEmpty(), Codec.fieldNote(pr), justMastered),
            levelActions,
            listState = rememberLazyListState(initialFirstVisibleItemIndex = if (kind == Kind.Hook) 0 else index, initialFirstVisibleItemScrollOffset = scrollPx),
        )
    }

    @Test fun launch() { shot("01_launch") { LaunchScreen() }; shot("01_launch", true) { LaunchScreen() } }

    @Test fun path() {
        val withDue = progress + (l1.id to progress.getValue(l1.id).copy(nextReviewAt = now - day))
        shot("02_path") { PathScreen(c, withDue, now, pathActions) }
        shot("02_path", true) { PathScreen(c, withDue, now, pathActions) }
        shot("02_path_fresh") { PathScreen(c, emptyMap(), now, pathActions) }
    }

    @Test fun category() {
        shot("03_category") { CategoryScreen(c, "foundations", progress, {}, {}, {}) }
        shot("03_category", true) { CategoryScreen(c, "foundations", progress, {}, {}, {}) }
        shot("03_category_inner") { CategoryScreen(c, "inner-world", progress, {}, {}, {}) }
    }

    @Test fun levelReading() {
        shot("04a_level_open") { level(l11.id, Kind.Hook) }
        shot("04a_level_open", true) { level(l11.id, Kind.Hook) }
        shot("04b_level_teach") { level(l1.id, Kind.Teach, 0) }
        shot("04c_level_teach_figure") { level(l1.id, Kind.Teach, 4, scrollPx = 1500) }
        shot("04d_level_pair") { level(l11.id, Kind.Pair, 1) }
        shot("04d_level_pair", true) { level(l11.id, Kind.Pair, 1) }
        shot("04e_level_case") { level(l1.id, Kind.Case) }
    }

    @Test fun levelPractice() {
        shot("04f_level_field") { level(l11.id, Kind.FieldLog) }
        shot("04g_level_drill") { level(l11.id, Kind.Drill, 0) }
        shot("04h_level_quiz") { level(l11.id, Kind.Question, 0) }
        shot("04h_level_quiz", true) { level(l11.id, Kind.Question, 0) }
        shot("04i_level_reflect") { level(l11.id, Kind.ReflectIntro) }
        shot("04j_level_mastery_remaining") { level(l11.id, Kind.Mastery) }
        shot("04k_level_mastered") { level(l1.id, Kind.Mastery, justMastered = true) }
        shot("04l_level_further") { level(l1.id, Kind.References) }
    }

    @Test fun discipline() {
        shot("03_category_discipline") { CategoryScreen(c, "discipline", progress, {}, {}, {}) }
        shot("04m_discipline_open", true) { level("discipline-15", Kind.Hook) }
        shot("04n_discipline_diagram") { level("discipline-10", Kind.Teach, 1, scrollPx = 2450) }
        shot("04o_discipline_quiz") { level("discipline-06", Kind.Question, 2) }
        shot("04p_face_plate") { level("foundations-07", Kind.Teach, 2, scrollPx = 2300) }
        shot("04q_face_plate_ink", true) { level("foundations-02", Kind.Teach, 0, scrollPx = 2000) }
    }

    @Test fun poseLibrary() {
        shot("05_poselib") { PoseLibraryScreen(c, "", "all", setOf("p_sit_neck_touch"), {}, {}, {}, {}) }
        shot("05_poselib", true) { PoseLibraryScreen(c, "", "all", emptySet(), {}, {}, {}, {}) }
        shot("06_pose_detail") { PoseDetailScreen(c, "p_stand_arms_crossed_tight", "p_stand_arms_crossed_loose", PoseBookmark("p_stand_arms_crossed_tight", "", 0), {}, {}, {}, {}) }
        shot("06_pose_detail_side", true) { PoseDetailScreen(c, "p_stand_slumped_side", null, null, {}, {}, {}, {}) }
        shot("06_pose_detail_pair") { PoseDetailScreen(c, "p_pair_handshake", null, null, {}, {}, {}, {}) }
        shot("06_pose_detail_lectern") { PoseDetailScreen(c, "p_pro_lectern", null, null, {}, {}, {}, {}) }
        shot("05_poselib_filtered") { PoseLibraryScreen(c, "", "together", emptySet(), {}, {}, {}, {}) }
    }

    @Test fun reviews() {
        val due = progress + (l1.id to progress.getValue(l1.id).copy(nextReviewAt = now - 3 * day))
        shot("07_reviews") { ReviewScreen(c, due, now, {}, {}, { _, _ -> }) }
        shot("07_reviews_empty") { ReviewScreen(c, emptyMap(), now, {}, {}, { _, _ -> }) }
    }

    @Test fun progressAndJournal() {
        shot("08_progress") { ProgressScreen(c, progress, emptyList(), {}, {}, {}, {}) }
        shot("08_progress_empty", true) { ProgressScreen(c, emptyMap(), emptyList(), {}, {}, {}, {}) }
        shot("09_reflections") { ReflectionsScreen(c, progress, {}, { _, _ -> }, {}) }
    }

    @Test fun settingsEthicsCrisis() {
        shot("10_settings") {
            SettingsScreen(c, DesignPrefs(readingSize = 18), false, null, null, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {}, {})
        }
        shot("11_ethics") { EthicsScreen(c.pack.ethics, {}) }
        shot("11_ethics", true) { EthicsScreen(c.pack.ethics, {}) }
        shot("12_crisis") { CrisisScreen(c.pack.crisisResources, null, null, {}, {}) }
        shot("12_crisis", true) { CrisisScreen(c.pack.crisisResources, null, null, {}, {}) }
        // The gate in front of 2.13, exactly as AppNav builds it.
        val gated = c.levels.getValue("inner-world-13")
        val label = "Continue to ${c.reference(gated)}  ${gated.title}"
        shot("12_gate_2_13") { CrisisScreen(gated.safetyGate, label, {}, {}, {}) }
        shot("12_gate_2_13", true) { CrisisScreen(gated.safetyGate, label, {}, {}, {}) }
    }

    @Test fun states() {
        shot("13_empty") { EmptyState("Nothing mastered yet. The first chapter takes about an hour, spread over a few days.", action = "Open 1.1") }
        shot("13_error", true) { ErrorState() }
    }
}
