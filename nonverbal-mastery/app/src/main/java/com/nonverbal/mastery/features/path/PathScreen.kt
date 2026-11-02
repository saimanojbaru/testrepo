package com.nonverbal.mastery.features.path

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nonverbal.mastery.AppContainer
import com.nonverbal.mastery.data.db.LevelProgress
import com.nonverbal.mastery.data.db.LevelStatus
import com.nonverbal.mastery.data.models.Level
import com.nonverbal.mastery.data.repo.Curriculum
import com.nonverbal.mastery.data.repo.MasteryCheck
import com.nonverbal.mastery.data.repo.ReviewScheduler
import com.nonverbal.mastery.design.components.AppMark
import com.nonverbal.mastery.design.components.ButtonKind
import com.nonverbal.mastery.design.components.CategoryCard
import com.nonverbal.mastery.design.components.DiamondMark
import com.nonverbal.mastery.design.components.IconAction
import com.nonverbal.mastery.design.components.NvButton
import com.nonverbal.mastery.design.components.NvIcons
import com.nonverbal.mastery.design.components.SectionLabel
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.Shapes
import com.nonverbal.mastery.design.tokens.Space
import com.nonverbal.mastery.design.tokens.categoryAccent
import com.nonverbal.mastery.ui.nav.SharedKeys
import com.nonverbal.mastery.ui.nav.appViewModel
import com.nonverbal.mastery.ui.nav.sharedContainer
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class PathViewModel(c: AppContainer) : ViewModel() {
    val progress: StateFlow<Map<String, LevelProgress>> =
        c.progress.progress.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
}

fun romanNumeral(n: Int): String = listOf("I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X").getOrElse(n - 1) { n.toString() }

data class PathActions(
    val openCategory: (String) -> Unit,
    val openLevel: (String) -> Unit,
    val openReviews: () -> Unit,
    val openPoseLibrary: () -> Unit,
    val openProgress: () -> Unit,
    val openSettings: () -> Unit,
    val openEthics: () -> Unit,
    val openCrisis: () -> Unit,
)

@Composable
fun PathRouteScreen(curriculum: Curriculum, actions: PathActions) {
    val vm = appViewModel { PathViewModel(it) }
    val progress by vm.progress.collectAsStateWithLifecycle()
    PathScreen(curriculum, progress, System.currentTimeMillis(), actions)
}

@Composable
fun PathScreen(curriculum: Curriculum, progress: Map<String, LevelProgress>, now: Long, actions: PathActions) {
    val colors = Nv.colors
    val mastered = progress.values.count { it.status == LevelStatus.MASTERED && curriculum.levels.containsKey(it.levelId) }
    val total = curriculum.plannedTotal
    val due = progress.values.filter { ReviewScheduler.isDue(it, now) && curriculum.levels.containsKey(it.levelId) }
    val continueLevel: Pair<Level, LevelProgress>? = progress.values
        .filter { it.lastOpenedAt != null && it.status != LevelStatus.MASTERED }
        .maxByOrNull { it.lastOpenedAt ?: 0L }
        ?.let { p -> curriculum.levels[p.levelId]?.let { it to p } }

    val wide = LocalConfiguration.current.screenWidthDp >= 600
    LazyColumn(
        Modifier.fillMaxSize().background(colors.background),
        contentPadding = PaddingValues(bottom = Space.x12),
    ) {
        item("bar") {
            Row(
                Modifier.fillMaxWidth().windowInsetsPadding(WindowInsets.statusBars).padding(start = Space.listGutter, end = Space.x1, top = Space.x2),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AppMark(size = 28.dp)
                Spacer(Modifier.width(Space.x2))
                Text("Nonverbal Mastery", style = Nv.type.titleSmall.copy(fontSize = Nv.type.ui.fontSize * 1.1f), color = colors.ink, modifier = Modifier.weight(1f))
                IconAction(NvIcons.Figure, "Pose library", actions.openPoseLibrary, tint = colors.inkSecondary)
                IconAction(NvIcons.Progress, "Progress", actions.openProgress, tint = colors.inkSecondary)
                IconAction(NvIcons.Settings, "Settings", actions.openSettings, tint = colors.inkSecondary)
            }
        }
        item("header") {
            Column(Modifier.padding(horizontal = Space.listGutter).padding(top = Space.x10)) {
                Text(
                    buildAnnotatedString {
                        append(mastered.toString())
                        withStyle(SpanStyle(color = colors.inkFaint)) { append(" / $total") }
                    },
                    style = Nv.type.numeralLarge,
                    color = colors.ink,
                    modifier = Modifier.semantics { heading() },
                )
                Spacer(Modifier.height(Space.x2))
                Text(
                    "chapters mastered  ·  ${if (total == 0) 0 else mastered * 100 / total}%",
                    style = Nv.type.numeral, color = colors.inkSecondary,
                )
                Spacer(Modifier.height(Space.x1))
                Text(
                    "${curriculum.writtenTotal} of $total chapters written so far",
                    style = Nv.type.uiSmall, color = colors.inkTertiary,
                )
            }
        }
        item("epigraph") {
            Column(Modifier.padding(horizontal = Space.listGutter).padding(top = Space.x10, bottom = Space.x8)) {
                DiamondMark()
                Spacer(Modifier.height(Space.x4))
                Text(
                    "There is no dictionary of gestures. There is only a disciplined method for generating and testing hypotheses about people, including yourself.",
                    style = Nv.type.marginalia.copy(fontSize = Nv.type.hook.fontSize * 0.92f, lineHeight = Nv.type.hook.lineHeight * 0.92f),
                    color = colors.ink,
                )
            }
        }
        if (due.isNotEmpty()) item("reviews") {
            val sitting = minOf(due.size, ReviewScheduler.SITTING_SIZE)
            Column(
                Modifier
                    .padding(horizontal = Space.listGutter)
                    .padding(bottom = Space.x4)
                    .fillMaxWidth()
                    .clip(Shapes.large)
                    .background(colors.brass.copy(alpha = if (colors.isDark) 0.12f else 0.1f))
                    .clickable(role = Role.Button, onClick = actions.openReviews)
                    .padding(Space.x5),
            ) {
                SectionLabel("Returning chapters", color = colors.brassText)
                Spacer(Modifier.height(Space.x2))
                Text(
                    if (due.size == 1) "One chapter is back for review." else "${due.size} chapters are back for review.",
                    style = Nv.type.titleSmall, color = colors.ink,
                )
                Spacer(Modifier.height(Space.x1))
                Text("One sitting, about ${sitting * 2} minutes.", style = Nv.type.uiSmall, color = colors.inkSecondary)
                Spacer(Modifier.height(Space.x3))
                NvButton("Begin", actions.openReviews, tint = colors.brassText)
            }
        }
        if (continueLevel != null) item("continue") {
            val (level, p) = continueLevel
            val check = MasteryCheck.of(level, p)
            val category = curriculum.categoryById[level.categoryId]
            val accent = categoryAccent(category?.accent ?: "#2A3A5C", colors)
            Column(
                Modifier
                    .padding(horizontal = Space.listGutter)
                    .padding(bottom = Space.x8)
                    .fillMaxWidth()
                    .clip(Shapes.large)
                    .clickable(role = Role.Button) { actions.openLevel(level.id) }
                    .padding(vertical = Space.x3),
            ) {
                SectionLabel("Continue reading", color = accent)
                Spacer(Modifier.height(Space.x2))
                Text("${curriculum.reference(level)}  ${level.title}", style = Nv.type.titleSmall, color = colors.ink)
                Spacer(Modifier.height(Space.x1))
                Text(
                    "${check.drillsDone} of ${check.drillsTotal} drills  ·  quiz ${check.quizAnswered}/${check.quizTotal}  ·  " +
                        if (check.fieldLogged) "field note logged" else "no field note yet",
                    style = Nv.type.numeral, color = colors.inkTertiary,
                )
            }
        }
        item("contents-label") {
            SectionLabel("Contents", modifier = Modifier.padding(horizontal = Space.listGutter).padding(bottom = Space.x4), color = colors.inkTertiary)
        }
        // Phones: one column. Tablets and landscape: the parts sit two abreast, like facing pages.
        val rows = curriculum.categories.withIndex().chunked(if (wide) 2 else 1)
        rows.forEach { row ->
            item(row.first().value.id) {
                Row(
                    Modifier.padding(horizontal = Space.listGutter, vertical = Space.x2 - 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(Space.x3),
                ) {
                    row.forEach { (i, category) ->
                        val levels = curriculum.levelsInOrder(category.id)
                        val masteredHere = levels.count { progress[it.id]?.status == LevelStatus.MASTERED }
                        CategoryCard(
                            numeral = romanNumeral(i + 1),
                            title = category.title,
                            tagline = category.tagline,
                            mastered = masteredHere,
                            total = curriculum.plannedPerCategory[category.id] ?: 15,
                            written = levels.size,
                            accent = categoryAccent(category.accent, colors),
                            onClick = { actions.openCategory(category.id) },
                            modifier = Modifier.weight(1f).sharedContainer(SharedKeys.categoryCard(category.id)),
                        )
                    }
                }
            }
        }
        item("footer") {
            Column(
                Modifier.padding(horizontal = Space.listGutter).padding(top = Space.x10).windowInsetsPadding(WindowInsets.navigationBars),
                verticalArrangement = Arrangement.spacedBy(Space.x1),
            ) {
                NvButton("Read the ethics statement", actions.openEthics, kind = ButtonKind.Quiet, icon = NvIcons.Book, tint = colors.inkSecondary)
                NvButton("If you're struggling right now", actions.openCrisis, kind = ButtonKind.Quiet, icon = NvIcons.Heart, tint = colors.inkSecondary)
            }
        }
    }
}
