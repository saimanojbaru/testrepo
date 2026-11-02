package com.nonverbal.mastery.features.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
import com.nonverbal.mastery.data.db.ReviewLog
import com.nonverbal.mastery.data.repo.Curriculum
import com.nonverbal.mastery.data.repo.ReviewScheduler
import com.nonverbal.mastery.design.components.EmptyState
import com.nonverbal.mastery.design.components.MasteredMark
import com.nonverbal.mastery.design.components.NavRow
import com.nonverbal.mastery.design.components.NvIcons
import com.nonverbal.mastery.design.components.ProgressLine
import com.nonverbal.mastery.design.components.SectionLabel
import com.nonverbal.mastery.design.components.TopBar
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.Shapes
import com.nonverbal.mastery.design.tokens.Space
import com.nonverbal.mastery.design.tokens.categoryAccent
import com.nonverbal.mastery.features.path.romanNumeral
import com.nonverbal.mastery.ui.nav.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import java.text.DateFormat
import java.util.Date

class ProgressViewModel(c: AppContainer) : ViewModel() {
    val progress: StateFlow<Map<String, LevelProgress>> =
        c.progress.progress.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val reviews: StateFlow<List<ReviewLog>> =
        c.progress.reviews.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
}

@Composable
fun ProgressRouteScreen(curriculum: Curriculum, onBack: () -> Unit, onOpenLevel: (String) -> Unit, onReflections: () -> Unit, onCategory: (String) -> Unit) {
    val vm = appViewModel { ProgressViewModel(it) }
    val progress by vm.progress.collectAsStateWithLifecycle()
    val reviews by vm.reviews.collectAsStateWithLifecycle()
    ProgressScreen(curriculum, progress, reviews, onBack, onOpenLevel, onReflections, onCategory)
}

@Composable
fun ProgressScreen(
    curriculum: Curriculum,
    progress: Map<String, LevelProgress>,
    reviews: List<ReviewLog>,
    onBack: () -> Unit,
    onOpenLevel: (String) -> Unit,
    onReflections: () -> Unit,
    onCategory: (String) -> Unit,
) {
    val colors = Nv.colors
    val known = progress.values.filter { curriculum.levels.containsKey(it.levelId) }
    val mastered = known.filter { it.status == LevelStatus.MASTERED }.sortedByDescending { it.masteredAt }
    val total = curriculum.plannedTotal
    val reflections = known.count { it.reflection.isNotBlank() }
    val retained = known.count { ReviewScheduler.isRetained(it) }
    val firstLevel = curriculum.categories.firstOrNull()?.let { curriculum.levelsInOrder(it.id).firstOrNull() }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        TopBar(title = "Progress", onBack = onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Space.listGutter, end = Space.listGutter, bottom = Space.x16)) {
            item("big") {
                Column(Modifier.padding(top = Space.x6)) {
                    Text(
                        buildAnnotatedString {
                            append("${mastered.size}")
                            withStyle(SpanStyle(color = colors.inkFaint)) { append(" / $total") }
                        },
                        style = Nv.type.numeralLarge, color = colors.ink, modifier = Modifier.semantics { heading() },
                    )
                    Spacer(Modifier.height(Space.x2))
                    Text("mastered  ·  ${if (total == 0) 0 else mastered.size * 100 / total}%", style = Nv.type.numeral, color = colors.inkSecondary)
                    Spacer(Modifier.height(Space.x6))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.x6)) {
                        Stat("${reviews.size}", "returns done")
                        Stat("$retained", "retained")
                        Stat("$reflections", "reflections")
                    }
                }
            }
            item("cats-h") { SectionLabel("By part", Modifier.padding(top = Space.x10, bottom = Space.x2), color = colors.inkTertiary) }
            curriculum.categories.forEachIndexed { i, cat ->
                item("cat-${cat.id}") {
                    val accent = categoryAccent(cat.accent, colors)
                    val levels = curriculum.levelsInOrder(cat.id)
                    val m = levels.count { progress[it.id]?.status == LevelStatus.MASTERED }
                    val planned = curriculum.plannedPerCategory[cat.id] ?: 15
                    Column(
                        Modifier.fillMaxWidth().clip(Shapes.card).clickable(role = Role.Button) { onCategory(cat.id) }.padding(vertical = Space.x3),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(romanNumeral(i + 1), style = Nv.type.numeral, color = accent, modifier = Modifier.width(32.dp))
                            Text(cat.title, style = Nv.type.ui, color = colors.ink, modifier = Modifier.weight(1f))
                            Text("$m / $planned", style = Nv.type.numeral, color = colors.inkSecondary)
                        }
                        Spacer(Modifier.height(Space.x2))
                        ProgressLine(if (planned == 0) 0f else m / planned.toFloat(), Modifier.padding(start = 32.dp), color = accent, track = accent.copy(alpha = 0.16f))
                    }
                }
            }
            item("recent-h") { SectionLabel("Recently mastered", Modifier.padding(top = Space.x10, bottom = Space.x2), color = colors.inkTertiary) }
            if (mastered.isEmpty()) item("empty") {
                EmptyState(
                    "Nothing mastered yet. The first chapter takes about an hour, spread over a few days.",
                    action = firstLevel?.let { "Open ${curriculum.reference(it)}" },
                    onAction = { firstLevel?.let { onOpenLevel(it.id) } },
                )
            }
            mastered.take(8).forEach { p ->
                item("m-${p.levelId}") {
                    val level = curriculum.levels.getValue(p.levelId)
                    Row(
                        Modifier.fillMaxWidth().clip(Shapes.card).clickable(role = Role.Button) { onOpenLevel(level.id) }.padding(vertical = Space.x3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MasteredMark()
                        Spacer(Modifier.width(Space.x3))
                        Column(Modifier.weight(1f)) {
                            Text("${curriculum.reference(level)}  ${level.title}", style = Nv.type.ui, color = colors.ink)
                            p.masteredAt?.let { Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)), style = Nv.type.uiSmall, color = colors.inkTertiary) }
                        }
                    }
                }
            }
            item("journal") {
                Column(Modifier.padding(top = Space.x8)) {
                    SectionLabel("Journal", color = colors.inkTertiary)
                    NavRow("Your reflections", onReflections, detail = "Everything you've written, searchable and exportable", leading = NvIcons.Pen, trailing = "$reflections")
                }
            }
        }
    }
}

@Composable
private fun Stat(value: String, label: String) {
    Column {
        Text(value, style = Nv.type.title.copy(fontFeatureSettings = "tnum"), color = Nv.colors.ink)
        Text(label, style = Nv.type.uiSmall, color = Nv.colors.inkTertiary)
    }
}
