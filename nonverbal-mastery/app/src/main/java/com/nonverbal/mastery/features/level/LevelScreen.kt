@file:OptIn(ExperimentalFoundationApi::class)

package com.nonverbal.mastery.features.level

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nonverbal.mastery.data.db.LevelProgress
import com.nonverbal.mastery.data.db.LevelStatus
import com.nonverbal.mastery.data.models.Level
import com.nonverbal.mastery.data.repo.Codec
import com.nonverbal.mastery.data.repo.Curriculum
import com.nonverbal.mastery.data.repo.FieldNote
import com.nonverbal.mastery.data.repo.MasteryCheck
import com.nonverbal.mastery.data.repo.Unlocks
import com.nonverbal.mastery.design.components.EmptyState
import com.nonverbal.mastery.design.components.IconAction
import com.nonverbal.mastery.design.components.NvIcons
import com.nonverbal.mastery.design.components.ProgressLine
import com.nonverbal.mastery.design.components.ProvideReadingMeasure
import com.nonverbal.mastery.design.components.SectionLabel
import com.nonverbal.mastery.design.components.SegmentedTabs
import com.nonverbal.mastery.design.components.TabSpec
import com.nonverbal.mastery.design.components.TopBar
import com.nonverbal.mastery.design.components.LocalReadingMeasure
import com.nonverbal.mastery.design.components.readingColumn
import com.nonverbal.mastery.design.theme.CategoryTint
import com.nonverbal.mastery.design.theme.LocalCategoryTint
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.Space
import com.nonverbal.mastery.design.tokens.categoryAccent
import com.nonverbal.mastery.features.category.tierLabel
import com.nonverbal.mastery.ui.nav.SharedKeys
import com.nonverbal.mastery.ui.nav.appViewModel
import com.nonverbal.mastery.ui.nav.sharedContainer
import com.nonverbal.mastery.ui.nav.sharedTitle
import kotlinx.coroutines.launch

/** The five sections of a chapter, in reading order. */
val SectionNames = listOf("Read", "Drills", "Assess", "Reflect", "Further")

enum class Kind { Hook, Why, Teach, Pair, Case, MoreCase, Worked, Failure, MoreFailure, ReaderQ, PracticeIntro, Observe, FieldLog, Drill, AssessIntro, Question, QuizResult, ReflectIntro, Editor, References, ReviewPreview, Mastery }

data class LevelItem(val kind: Kind, val section: Int, val i: Int = 0) {
    val key: String get() = "$kind-$i"
}

/** The chapter, flattened into one scroll. Pose pairs interleave with the teaching. */
fun planLevel(level: Level): List<LevelItem> = buildList {
    add(LevelItem(Kind.Hook, 0)); add(LevelItem(Kind.Why, 0))
    level.teach.indices.forEach { i ->
        add(LevelItem(Kind.Teach, 0, i))
        if (i < level.poseGallery.size) add(LevelItem(Kind.Pair, 0, i))
    }
    for (i in level.teach.size until level.poseGallery.size) add(LevelItem(Kind.Pair, 0, i))
    add(LevelItem(Kind.Case, 0))
    level.moreCases.indices.forEach { add(LevelItem(Kind.MoreCase, 0, it)) }
    if (level.workedReading != null) add(LevelItem(Kind.Worked, 0))
    add(LevelItem(Kind.Failure, 0))
    level.moreFailures.indices.forEach { add(LevelItem(Kind.MoreFailure, 0, it)) }
    level.questions.indices.forEach { add(LevelItem(Kind.ReaderQ, 0, it)) }
    add(LevelItem(Kind.PracticeIntro, 1))
    level.observe.indices.forEach { add(LevelItem(Kind.Observe, 1, it)) }
    add(LevelItem(Kind.FieldLog, 1))
    level.drills.indices.forEach { add(LevelItem(Kind.Drill, 1, it)) }
    add(LevelItem(Kind.AssessIntro, 2))
    level.quiz.indices.forEach { add(LevelItem(Kind.Question, 2, it)) }
    add(LevelItem(Kind.QuizResult, 2))
    add(LevelItem(Kind.ReflectIntro, 3)); add(LevelItem(Kind.Editor, 3))
    add(LevelItem(Kind.References, 4)); add(LevelItem(Kind.ReviewPreview, 4)); add(LevelItem(Kind.Mastery, 4))
}

/** Items before the plan: the header (0) and the sticky tabs (1). */
private const val PLAN_OFFSET = 2

data class LevelState(
    val progress: LevelProgress?,
    val reflection: String,
    val fieldNote: FieldNote,
    val justMastered: Boolean,
)

data class LevelActions(
    val onBack: () -> Unit,
    val onPose: (String, String?) -> Unit,
    val onEthics: () -> Unit,
    val onPoseLibrary: () -> Unit,
    val onSettings: () -> Unit,
    val onOpenLevel: (String) -> Unit,
    val toggleDrill: (Int, Boolean) -> Unit = { _, _ -> },
    val answer: (Int, Int) -> Unit = { _, _ -> },
    val retake: () -> Unit = {},
    val editReflection: (String) -> Unit = {},
    val editFieldNote: (FieldNote) -> Unit = {},
    val logFieldNote: () -> Unit = {},
    val master: () -> Unit = {},
)

@Composable
fun LevelRouteScreen(curriculum: Curriculum, levelId: String, initialSection: Int, actions: LevelActions) {
    val level = curriculum.levels[levelId]
    if (level == null) {
        Column(Modifier.fillMaxSize().background(Nv.colors.background)) {
            TopBar(onBack = actions.onBack)
            EmptyState("This chapter is not in this edition of the book.")
        }
        return
    }
    val vm = appViewModel(key = "level-$levelId") { LevelViewModel(it, level) }
    val progress by vm.progress.collectAsStateWithLifecycle()
    val reflection by vm.reflection.collectAsStateWithLifecycle()
    val field by vm.fieldNote.collectAsStateWithLifecycle()
    val justMastered by vm.justMastered.collectAsStateWithLifecycle()
    val allProgress by appViewModel { com.nonverbal.mastery.features.path.PathViewModel(it) }.progress.collectAsStateWithLifecycle()
    val unlocked = level.id in Unlocks.unlockedIds(curriculum.levelsInOrder(level.categoryId), allProgress)
    LaunchedEffect(unlocked) { if (unlocked) vm.markOpened() }

    val wired = actions.copy(
        toggleDrill = { i, d -> vm.toggleDrill(i, d) },
        answer = { q, c -> vm.answer(q, c) },
        retake = { vm.retake() },
        editReflection = vm::editReflection,
        editFieldNote = vm::editFieldNote,
        logFieldNote = vm::logFieldNote,
        master = { vm.master() },
    )
    if (!unlocked) {
        LockedLevel(curriculum, level, actions)
        return
    }
    LevelScreen(
        curriculum, level,
        LevelState(progress, reflection ?: progress?.reflection.orEmpty(), field ?: Codec.fieldNote(progress), justMastered),
        wired, initialSection,
    )
}

@Composable
private fun LockedLevel(curriculum: Curriculum, level: Level, actions: LevelActions) {
    val previous = curriculum.levelsInOrder(level.categoryId).lastOrNull { it.index < level.index }
    Column(Modifier.fillMaxSize().background(Nv.colors.background)) {
        TopBar(title = level.title, onBack = actions.onBack)
        EmptyState(
            "This chapter opens when you master the one before it" +
                (previous?.let { ": ${curriculum.reference(it)} ${it.title}." } ?: "."),
            action = previous?.let { "Open ${curriculum.reference(it)}" },
            onAction = { previous?.let { actions.onOpenLevel(it.id) } },
        )
    }
}

@Composable
fun LevelScreen(
    curriculum: Curriculum,
    level: Level,
    state: LevelState,
    actions: LevelActions,
    initialSection: Int = 0,
    listState: LazyListState = rememberLazyListState(),
) {
    val colors = Nv.colors
    val category = curriculum.categoryById[level.categoryId]
    val accent = categoryAccent(category?.accent ?: "#2A3A5C", colors)
    val plan = remember(level) { planLevel(level) }
    val sectionStarts = remember(plan) { (0 until 5).map { s -> PLAN_OFFSET + plan.indexOfFirst { it.section == s } } }
    val readRange = remember(plan) { PLAN_OFFSET until PLAN_OFFSET + plan.count { it.section == 0 } }
    // The card and tabs follow the reflection as it is typed, not the debounced saved copy.
    val effective = (state.progress ?: LevelProgress(level.id, level.categoryId, LevelStatus.OPEN)).copy(reflection = state.reflection)
    val check = MasteryCheck.of(level, effective)
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val tabBarPx = with(density) { 56.dp.toPx() }

    LaunchedEffect(level.id, initialSection) {
        if (initialSection in 1..4) listState.scrollToItem(sectionStarts[initialSection], -tabBarPx.toInt())
    }

    val activeSection by remember(plan) {
        derivedStateOf {
            val info = listState.layoutInfo.visibleItemsInfo
            val firstReal = info.firstOrNull { it.index >= PLAN_OFFSET && it.offset + it.size > tabBarPx + 1 }
            val idx = firstReal?.index ?: listState.firstVisibleItemIndex
            if (idx < PLAN_OFFSET) 0 else plan.getOrNull(idx - PLAN_OFFSET)?.section ?: 4
        }
    }
    val readProgress by remember(plan) {
        derivedStateOf {
            val first = listState.firstVisibleItemIndex
            when {
                first < readRange.first -> 0f
                first > readRange.last -> 1f
                else -> {
                    val size = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.index == first }?.size ?: 1
                    val frac = listState.firstVisibleItemScrollOffset / size.toFloat().coerceAtLeast(1f)
                    ((first - readRange.first) + frac) / (readRange.last - readRange.first + 1).toFloat()
                }
            }
        }
    }
    val titleAlpha by remember { derivedStateOf { if (listState.firstVisibleItemIndex > 0) 1f else 0f } }

    val drillsTab = TabSpec("Drills", badge = "${check.drillsDone}/${check.drillsTotal}", done = check.drillsMet)
    val quizTab = TabSpec("Assess", badge = "${check.quizAnswered}/${check.quizTotal}", done = check.quizMet)
    val reflectTab = TabSpec("Reflect", done = check.reflectionMet)
    val tabs = listOf(TabSpec("Read"), drillsTab, quizTab, reflectTab, TabSpec("Further"))
    var menuOpen by remember { mutableStateOf(false) }

    CompositionLocalProvider(LocalCategoryTint provides CategoryTint(accent)) {
        ProvideReadingMeasure {
            Column(Modifier.fillMaxSize().background(colors.background).imePadding()) {
                Box {
                    TopBar(title = level.title, titleAlpha = titleAlpha, onBack = actions.onBack) {
                        Box {
                            IconAction(NvIcons.More, "More", { menuOpen = true }, tint = colors.inkSecondary)
                            DropdownMenu(menuOpen, { menuOpen = false }, containerColor = colors.surface) {
                                DropdownMenuItem({ Text("Read the ethics statement", style = Nv.type.ui) }, { menuOpen = false; actions.onEthics() })
                                DropdownMenuItem({ Text("Pose library", style = Nv.type.ui) }, { menuOpen = false; actions.onPoseLibrary() })
                                DropdownMenuItem({ Text("Text size and appearance", style = Nv.type.ui) }, { menuOpen = false; actions.onSettings() })
                            }
                        }
                    }
                    ProgressLine(readProgress, Modifier.align(Alignment.BottomStart), color = colors.brass, track = colors.divider.copy(alpha = 0f))
                }
                LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.x16)) {
                    item("header") { LevelHeader(curriculum, level, accent) }
                    stickyHeader("tabs") {
                        SegmentedTabs(tabs, activeSection, onSelect = { s ->
                            scope.launch { listState.animateScrollToItem(sectionStarts[s], -tabBarPx.toInt()) }
                        })
                    }
                    items(plan, key = { it.key }) { item ->
                        LevelItemView(item, curriculum, level, state, check, actions)
                    }
                    item("end") { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
                }
            }
        }
    }
}

@Composable
private fun LevelHeader(curriculum: Curriculum, level: Level, accent: androidx.compose.ui.graphics.Color) {
    val colors = Nv.colors
    val category = curriculum.categoryById[level.categoryId]
    val words = remember(level) { levelWordCount(level) }
    Box(
        Modifier
            .fillMaxWidth()
            .sharedContainer(SharedKeys.levelCard(level.id))
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = if (colors.isDark) 0.14f else 0.08f), colors.background))),
    ) {
        Column(Modifier.readingColumn(LocalReadingMeasure.current).padding(top = Space.x8, bottom = Space.x6)) {
            SectionLabel("${category?.title ?: ""}  ·  ${curriculum.reference(level)}")
            Spacer(Modifier.height(Space.x3))
            Text(
                level.title,
                style = Nv.type.display,
                color = colors.ink,
                modifier = Modifier.sharedTitle(SharedKeys.levelTitle(level.id)).semantics { heading() },
            )
            Spacer(Modifier.height(Space.x4))
            Text(
                "${tierLabel(level.tier)} tier  ·  ${level.estimatedMinutes} min  ·  ${pagesFor(words)} pages",
                style = Nv.type.numeral,
                color = colors.inkTertiary,
            )
        }
    }
}

/** Words of authored text in a level, by the content budget's definition. */
fun levelWordCount(level: Level): Int {
    fun w(s: String?) = s?.split(Regex("\\s+"))?.count { it.isNotBlank() } ?: 0
    var n = w(level.hook) + w(level.why) + w(level.caseStudy) + w(level.failureMode) + w(level.reviewPrompt)
    level.teach.forEach { n += w(it.h) + w(it.p) + w(it.pullQuote) + w(it.disconfirm) }
    level.poseGallery.forEach { n += w(it.doCaption) + w(it.dontCaption) + w(it.annotation) }
    level.observe.forEach { n += w(it) }
    level.drills.forEach { d -> n += w(d.name) + d.steps.sumOf { w(it) } + d.variations.sumOf { w(it) } }
    level.moreCases.forEach { c -> n += w(c.title) + w(c.scene) + w(c.misread) + w(c.betterRead) }
    level.workedReading?.let { r -> n += w(r.title) + w(r.scene) + r.steps.sumOf { w(it.label) + w(it.text) } }
    level.moreFailures.forEach { f -> n += w(f.title) + w(f.body) }
    level.questions.forEach { q -> n += w(q.q) + w(q.a) }
    level.quiz.forEach { q -> n += w(q.q) + q.options.sumOf { w(it) } + w(q.explain) }
    n += w(level.reflection.prompt) + w(level.reflection.guidance)
    level.furtherStudy.forEach { n += w(it.work) + w(it.author) + w(it.note) }
    return n
}

/** One page is one screenful: about 450 authored words. */
fun pagesFor(words: Int): Int = maxOf(1, Math.round(words / 450f))
