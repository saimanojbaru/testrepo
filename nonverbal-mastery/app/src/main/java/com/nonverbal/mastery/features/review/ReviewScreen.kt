package com.nonverbal.mastery.features.review

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nonverbal.mastery.AppContainer
import com.nonverbal.mastery.data.db.LevelProgress
import com.nonverbal.mastery.data.models.Level
import com.nonverbal.mastery.data.repo.Curriculum
import com.nonverbal.mastery.data.repo.ReviewScheduler
import com.nonverbal.mastery.design.components.ButtonKind
import com.nonverbal.mastery.design.components.EmptyState
import com.nonverbal.mastery.design.components.NvButton
import com.nonverbal.mastery.design.components.SectionLabel
import com.nonverbal.mastery.design.components.TopBar
import com.nonverbal.mastery.design.components.inlineItalics
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.Shapes
import com.nonverbal.mastery.design.tokens.Space
import com.nonverbal.mastery.design.tokens.categoryAccent
import com.nonverbal.mastery.design.tokens.enterSpec
import com.nonverbal.mastery.ui.nav.appViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.concurrent.TimeUnit

class ReviewViewModel(private val c: AppContainer) : ViewModel() {
    val progress: StateFlow<Map<String, LevelProgress>> =
        c.progress.progress.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    fun complete(level: Level, durationMs: Long) = viewModelScope.launch { c.progress.completeReview(level, durationMs) }
}

@Composable
fun ReviewRouteScreen(curriculum: Curriculum, onBack: () -> Unit, onOpenLevel: (String) -> Unit) {
    val vm = appViewModel { ReviewViewModel(it) }
    val progress by vm.progress.collectAsStateWithLifecycle()
    ReviewScreen(curriculum, progress, System.currentTimeMillis(), onBack, onOpenLevel) { level, ms -> vm.complete(level, ms) }
}

@Composable
fun ReviewScreen(
    curriculum: Curriculum,
    progress: Map<String, LevelProgress>,
    now: Long,
    onBack: () -> Unit,
    onOpenLevel: (String) -> Unit,
    onDone: (Level, Long) -> Unit,
) {
    val colors = Nv.colors
    val due = progress.values
        .filter { ReviewScheduler.isDue(it, now) && curriculum.levels.containsKey(it.levelId) }
        .sortedBy { it.nextReviewAt }
    val sitting = due.take(ReviewScheduler.SITTING_SIZE)
    val waiting = due.drop(ReviewScheduler.SITTING_SIZE)
    val upcoming = progress.values
        .filter { it.nextReviewAt != null && !ReviewScheduler.isDue(it, now) && curriculum.levels.containsKey(it.levelId) }
        .sortedBy { it.nextReviewAt }
        .take(6)
    val openedAt = remember { mutableStateMapOf<String, Long>() }
    var expanded by remember { mutableStateOf(sitting.firstOrNull()?.levelId) }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        TopBar(title = "Returning chapters", onBack = onBack)
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Space.listGutter, end = Space.listGutter, bottom = Space.x16)) {
            item("intro") {
                Column(Modifier.padding(top = Space.x4, bottom = Space.x6)) {
                    Text("One sitting", style = Nv.type.display, color = colors.ink)
                    Spacer(Modifier.height(Space.x3))
                    Text(
                        "About ten minutes. Each returning chapter asks for one real task, done in the world, not a quiz. " +
                            "Nothing is lost if you leave one for another day; it simply waits here.",
                        style = Nv.type.bodySmall, color = colors.inkSecondary,
                    )
                }
            }
            if (due.isEmpty()) item("empty") {
                EmptyState("Nothing is due. The chapters you master will come back on their own, when they're worth revisiting.", action = "Back to the book", onAction = onBack)
            }
            if (sitting.isNotEmpty()) item("sitting-h") { SectionLabel("This sitting", Modifier.padding(bottom = Space.x3)) }
            items(sitting, key = { it.levelId }) { p ->
                val level = curriculum.levels.getValue(p.levelId)
                ReviewCard(
                    curriculum, level, p, now,
                    expanded = expanded == p.levelId,
                    onToggle = {
                        expanded = if (expanded == p.levelId) null else p.levelId
                        openedAt.putIfAbsent(p.levelId, System.currentTimeMillis())
                    },
                    onDone = {
                        val start = openedAt[p.levelId] ?: System.currentTimeMillis()
                        onDone(level, System.currentTimeMillis() - start)
                        expanded = sitting.firstOrNull { it.levelId != p.levelId }?.levelId
                    },
                    onReread = { onOpenLevel(level.id) },
                )
                Spacer(Modifier.height(Space.x3))
            }
            if (waiting.isNotEmpty()) {
                item("waiting-h") { SectionLabel("Also waiting, for another sitting", Modifier.padding(top = Space.x6, bottom = Space.x3), color = colors.inkTertiary) }
                items(waiting, key = { it.levelId }) { p ->
                    val level = curriculum.levels.getValue(p.levelId)
                    Text("${curriculum.reference(level)}  ${level.title}", style = Nv.type.ui, color = colors.inkSecondary, modifier = Modifier.padding(vertical = Space.x2))
                }
            }
            if (upcoming.isNotEmpty()) {
                item("upcoming-h") { SectionLabel("Coming back later", Modifier.padding(top = Space.x8, bottom = Space.x3), color = colors.inkTertiary) }
                items(upcoming, key = { "u-" + it.levelId }) { p ->
                    val level = curriculum.levels.getValue(p.levelId)
                    val days = TimeUnit.MILLISECONDS.toDays((p.nextReviewAt ?: now) - now).coerceAtLeast(0) + 1
                    Row(Modifier.fillMaxWidth().padding(vertical = Space.x2)) {
                        Text("${curriculum.reference(level)}  ${level.title}", style = Nv.type.ui, color = colors.inkSecondary, modifier = Modifier.weight(1f))
                        Text("in $days ${if (days == 1L) "day" else "days"}", style = Nv.type.numeral, color = colors.inkTertiary)
                    }
                }
            }
        }
    }
}

@Composable
private fun ReviewCard(
    curriculum: Curriculum,
    level: Level,
    p: LevelProgress,
    now: Long,
    expanded: Boolean,
    onToggle: () -> Unit,
    onDone: () -> Unit,
    onReread: () -> Unit,
) {
    val colors = Nv.colors
    val accent = categoryAccent(curriculum.categoryById[level.categoryId]?.accent ?: "#2A3A5C", colors)
    val waitingDays = ReviewScheduler.daysWaiting(p, now)
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(colors.surface)
            .border(Shapes.hairline, if (expanded) accent.copy(alpha = 0.5f) else colors.divider, Shapes.card),
    ) {
        Row(
            Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onToggle).padding(Space.x4)
                .semantics { stateDescription = if (expanded) "Expanded" else "Collapsed" },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(curriculum.reference(level), style = Nv.type.numeral, color = accent)
                Text(level.title, style = Nv.type.titleSmall, color = colors.ink)
            }
            Spacer(Modifier.width(Space.x2))
            Text(
                when (waitingDays) { 0 -> "due today"; 1 -> "waiting a day"; else -> "waiting $waitingDays days" },
                style = Nv.type.uiSmall, color = colors.inkTertiary,
            )
        }
        AnimatedVisibility(expanded, enter = fadeIn(enterSpec()) + expandVertically(enterSpec()), exit = fadeOut() + shrinkVertically()) {
            Column(Modifier.padding(start = Space.x4, end = Space.x4, bottom = Space.x4)) {
                SectionLabel("Your task", color = colors.inkTertiary)
                Spacer(Modifier.height(Space.x2))
                Text(inlineItalics(level.reviewPrompt.trim()), style = Nv.type.body, color = colors.ink)
                Spacer(Modifier.height(Space.x2))
                Text(
                    "Return ${p.reviewStreak + 1} of ${ReviewScheduler.INTERVAL_DAYS.size}.",
                    style = Nv.type.uiSmall, color = colors.inkTertiary,
                )
                Spacer(Modifier.height(Space.x4))
                Row {
                    NvButton("I did this", onDone, tint = accent)
                    Spacer(Modifier.width(Space.x2))
                    NvButton("Reread the chapter", onReread, kind = ButtonKind.Quiet)
                }
            }
        }
    }
}

