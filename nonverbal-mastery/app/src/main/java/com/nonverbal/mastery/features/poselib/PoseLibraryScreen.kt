package com.nonverbal.mastery.features.poselib

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.nonverbal.mastery.AppContainer
import com.nonverbal.mastery.data.db.PoseBookmark
import com.nonverbal.mastery.data.models.Pose
import com.nonverbal.mastery.data.repo.Curriculum
import com.nonverbal.mastery.design.components.Diagram
import com.nonverbal.mastery.design.components.DiagramKind
import com.nonverbal.mastery.design.components.EmptyState
import com.nonverbal.mastery.design.components.MarkerMode
import com.nonverbal.mastery.design.components.NvIcons
import com.nonverbal.mastery.design.components.PoseFigure
import com.nonverbal.mastery.design.components.SectionLabel
import com.nonverbal.mastery.design.components.TopBar
import com.nonverbal.mastery.design.components.accessibleDescription
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.Shapes
import com.nonverbal.mastery.design.tokens.Space
import com.nonverbal.mastery.ui.nav.appViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PoseSearchViewModel(private val c: AppContainer) : ViewModel() {
    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()
    private val _filter = MutableStateFlow("all")
    val filter: StateFlow<String> = _filter.asStateFlow()
    val bookmarks: StateFlow<List<PoseBookmark>> = c.progress.bookmarks.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setQuery(q: String) { _query.value = q }
    fun setFilter(f: String) { _filter.value = f }
    fun toggleBookmark(poseId: String, on: Boolean) = viewModelScope.launch { c.progress.toggleBookmark(poseId, on) }
    fun saveNote(poseId: String, note: String) = viewModelScope.launch { c.progress.saveBookmarkNote(poseId, note) }
}

val ThemeNames = linkedMapOf(
    "stance" to "Stance",
    "posture" to "Posture",
    "arms" to "Arms",
    "self-touch" to "Self-touch",
    "head" to "Head & gaze",
    "gesture" to "Gesture",
    "seated" to "Seated",
    "walking" to "Walking",
    "breath" to "Breath",
    "grief" to "Grief",
    "professional" to "At work",
    "greeting" to "Greeting & culture",
    "together" to "Together",
)

fun filterPoses(poses: List<Pose>, query: String, filter: String, bookmarked: Set<String>): List<Pose> {
    val q = query.trim().lowercase()
    return poses.filter { p ->
        val inFilter = when (filter) {
            "all" -> true
            "saved" -> p.id in bookmarked
            else -> p.theme == filter
        }
        val inQuery = q.isEmpty() || listOf(p.title, p.desc, p.theme, p.id).any { it.lowercase().contains(q) } ||
            p.annotations.any { it.text.lowercase().contains(q) }
        inFilter && inQuery
    }
}

@Composable
fun PoseLibraryRouteScreen(curriculum: Curriculum, onBack: () -> Unit, onPose: (String) -> Unit) {
    val vm = appViewModel { PoseSearchViewModel(it) }
    val query by vm.query.collectAsStateWithLifecycle()
    val filter by vm.filter.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    PoseLibraryScreen(curriculum, query, filter, bookmarks.map { it.poseId }.toSet(), vm::setQuery, vm::setFilter, onBack, onPose)
}

@Composable
fun PoseLibraryScreen(
    curriculum: Curriculum,
    query: String,
    filter: String,
    bookmarked: Set<String>,
    onQuery: (String) -> Unit,
    onFilter: (String) -> Unit,
    onBack: () -> Unit,
    onPose: (String) -> Unit,
) {
    val colors = Nv.colors
    val all = curriculum.pack.poses
    val shown = filterPoses(all, query, filter, bookmarked)
    val grouped = if (filter == "all" && query.isBlank()) {
        // Library order follows the theme list, not the order poses were authored in.
        val order = ThemeNames.keys.toList()
        shown.groupBy { it.theme }.toList().sortedBy { (t, _) -> order.indexOf(t).let { if (it < 0) Int.MAX_VALUE else it } }.toMap()
    } else mapOf("" to shown)

    Column(Modifier.fillMaxSize().background(colors.background)) {
        TopBar(title = "Pose library", onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Adaptive(150.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Space.listGutter, end = Space.listGutter, bottom = Space.x16),
            horizontalArrangement = Arrangement.spacedBy(Space.x3),
            verticalArrangement = Arrangement.spacedBy(Space.x4),
        ) {
            item("intro", span = { GridItemSpan(maxLineSpan) }) {
                Column(Modifier.padding(top = Space.x4)) {
                    Text("Plates", style = Nv.type.display, color = colors.ink)
                    Spacer(Modifier.height(Space.x3))
                    Text(
                        "Every figure in the book, gathered like anatomy plates. Left and right are always the figure's own. A pose is a shape a body makes, never a verdict about the person making it.",
                        style = Nv.type.bodySmall, color = colors.inkSecondary,
                    )
                    Spacer(Modifier.height(Space.x5))
                    SearchField(query, onQuery)
                    Spacer(Modifier.height(Space.x3))
                    FilterChips(filter, onFilter, bookmarked.isNotEmpty())
                }
            }
            if (shown.isEmpty()) item("empty", span = { GridItemSpan(maxLineSpan) }) {
                EmptyState(
                    if (filter == "saved") "No plates saved yet. Tap the bookmark on any plate to keep it here." else "No plate matches that. Try a body part, like “hand” or “shoulder”.",
                )
            }
            grouped.forEach { (theme, poses) ->
                if (theme.isNotEmpty()) item("h-$theme", span = { GridItemSpan(maxLineSpan) }) {
                    SectionLabel(ThemeNames[theme] ?: theme, Modifier.padding(top = Space.x4), color = colors.inkTertiary)
                }
                items(poses, key = { it.id }) { pose ->
                    PlateCard(pose, pose.id in bookmarked, curriculum.poseUsage[pose.id]?.size ?: 0) { onPose(pose.id) }
                }
            }
            if (filter == "all" && query.isBlank()) {
                item("diagrams-h", span = { GridItemSpan(maxLineSpan) }) {
                    Column(Modifier.padding(top = Space.x8)) {
                        Text("Diagrams", style = Nv.type.title, color = colors.ink)
                        Spacer(Modifier.height(Space.x2))
                        Text("The drawn plates that are not bodies: time, distance, attention, breath.", style = Nv.type.bodySmall, color = colors.inkSecondary)
                    }
                }
                DiagramKind.entries.forEach { kind ->
                    item("d-${kind.id}", span = { GridItemSpan(maxLineSpan) }) { Diagram(kind, Modifier.padding(top = Space.x4)) }
                }
            }
        }
    }
}

@Composable
private fun SearchField(query: String, onQuery: (String) -> Unit) {
    val colors = Nv.colors
    Row(
        Modifier.fillMaxWidth().heightIn(min = Space.touchTarget).clip(Shapes.chip).background(colors.surfaceSunken).padding(horizontal = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(NvIcons.Search, null, tint = colors.inkTertiary, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.x2))
        BasicTextField(
            value = query,
            onValueChange = onQuery,
            singleLine = true,
            textStyle = Nv.type.ui.copy(color = colors.ink),
            cursorBrush = SolidColor(Nv.tint),
            modifier = Modifier.weight(1f).semantics { contentDescription = "Search plates" },
            decorationBox = { inner ->
                Box {
                    if (query.isEmpty()) Text("Search: shoulder, neck, seated…", style = Nv.type.ui, color = colors.inkTertiary)
                    inner()
                }
            },
        )
    }
}

@Composable
private fun FilterChips(filter: String, onFilter: (String) -> Unit, hasSaved: Boolean) {
    val colors = Nv.colors
    val options = buildList {
        add("all" to "All")
        if (hasSaved) add("saved" to "Saved")
        ThemeNames.forEach { (k, v) -> add(k to v) }
    }
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
        options.forEach { (key, label) ->
            val sel = key == filter
            Text(
                label,
                style = Nv.type.uiSmall,
                color = if (sel) colors.onAccent else colors.inkSecondary,
                modifier = Modifier
                    .clip(Shapes.pill)
                    .background(if (sel) colors.accent else colors.surfaceSunken)
                    .selectable(sel, role = Role.Tab) { onFilter(key) }
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 14.dp, vertical = 11.dp),
            )
        }
    }
}

@Composable
private fun PlateCard(pose: Pose, saved: Boolean, usedIn: Int, onClick: () -> Unit) {
    val colors = Nv.colors
    Column(
        Modifier
            .clip(Shapes.card)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics(mergeDescendants = true) { contentDescription = "${pose.title}. ${pose.accessibleDescription()}" },
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(0.78f).clip(Shapes.card).background(colors.surface)) {
            PoseFigure(pose, Modifier.fillMaxSize(), markers = MarkerMode.Dots, decorative = true)
            if (saved) Icon(NvIcons.BookmarkFilled, null, tint = colors.brass, modifier = Modifier.align(Alignment.TopEnd).padding(Space.x2).size(18.dp))
        }
        Spacer(Modifier.height(Space.x2))
        Text(pose.title, style = Nv.type.titleSmall.copy(fontSize = Nv.type.ui.fontSize * 1.06f), color = colors.ink)
        Text(
            if (usedIn == 0) "Not yet used in a chapter" else "Used in $usedIn ${if (usedIn == 1) "chapter" else "chapters"}",
            style = Nv.type.uiSmall, color = colors.inkTertiary,
        )
    }
}
