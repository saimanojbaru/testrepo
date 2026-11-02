package com.nonverbal.mastery.features.progress

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nonverbal.mastery.data.db.LevelProgress
import com.nonverbal.mastery.data.repo.Codec
import com.nonverbal.mastery.data.repo.Curriculum
import com.nonverbal.mastery.design.components.ButtonKind
import com.nonverbal.mastery.design.components.EmptyState
import com.nonverbal.mastery.design.components.IconAction
import com.nonverbal.mastery.design.components.NvButton
import com.nonverbal.mastery.design.components.NvIcons
import com.nonverbal.mastery.design.components.SectionLabel
import com.nonverbal.mastery.design.components.TopBar
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.Shapes
import com.nonverbal.mastery.design.tokens.Space
import com.nonverbal.mastery.design.tokens.categoryAccent
import com.nonverbal.mastery.ui.nav.appViewModel
import java.text.DateFormat
import java.util.Date

@Composable
fun ReflectionsRouteScreen(curriculum: Curriculum, onBack: () -> Unit, onOpenLevel: (String, Int) -> Unit, onExport: () -> Unit) {
    val vm = appViewModel { ProgressViewModel(it) }
    val progress by vm.progress.collectAsStateWithLifecycle()
    ReflectionsScreen(curriculum, progress, onBack, onOpenLevel, onExport)
}

@Composable
fun ReflectionsScreen(
    curriculum: Curriculum,
    progress: Map<String, LevelProgress>,
    onBack: () -> Unit,
    onOpenLevel: (String, Int) -> Unit,
    onExport: () -> Unit,
) {
    val colors = Nv.colors
    var query by rememberSaveable { mutableStateOf("") }
    val entries = progress.values
        .filter { curriculum.levels.containsKey(it.levelId) && (it.reflection.isNotBlank() || it.fieldNote.isNotBlank()) }
        .sortedByDescending { it.lastOpenedAt ?: it.masteredAt ?: 0L }
        .filter { p ->
            val q = query.trim().lowercase()
            q.isEmpty() || p.reflection.lowercase().contains(q) || curriculum.levels.getValue(p.levelId).title.lowercase().contains(q) ||
                Codec.fieldNote(p).let { listOf(it.observed, it.hypothesis, it.disconfirmer, it.outcome).any { s -> s.lowercase().contains(q) } }
        }

    Column(Modifier.fillMaxSize().background(colors.background)) {
        TopBar(title = "Reflections", onBack = onBack) {
            IconAction(NvIcons.Share, "Export your journal", onExport, tint = colors.inkSecondary)
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = Space.listGutter, end = Space.listGutter, bottom = Space.x16)) {
            item("intro") {
                Column(Modifier.padding(top = Space.x4, bottom = Space.x5)) {
                    Text("Your journal", style = Nv.type.display, color = colors.ink)
                    Spacer(Modifier.height(Space.x3))
                    Text(
                        "Everything you've written lives only on this phone. Export it whenever you like; nothing leaves unless you send it.",
                        style = Nv.type.bodySmall, color = colors.inkSecondary,
                    )
                    Spacer(Modifier.height(Space.x5))
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Space.touchTarget).clip(Shapes.chip).background(colors.surfaceSunken).padding(horizontal = Space.x3),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(NvIcons.Search, null, tint = colors.inkTertiary, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(Space.x2))
                        BasicTextField(
                            query, { query = it }, singleLine = true,
                            textStyle = Nv.type.ui.copy(color = colors.ink), cursorBrush = SolidColor(Nv.tint),
                            modifier = Modifier.weight(1f).semantics { contentDescription = "Search your reflections" },
                            decorationBox = { inner -> Box { if (query.isEmpty()) Text("Search what you've written", style = Nv.type.ui, color = colors.inkTertiary); inner() } },
                        )
                    }
                }
            }
            if (entries.isEmpty()) item("empty") {
                EmptyState(
                    if (query.isBlank()) "You haven't written a reflection yet. Each chapter ends with one, and it's the part people remember."
                    else "Nothing you've written matches that.",
                )
            }
            items(entries, key = { it.levelId }) { p -> ReflectionEntry(curriculum, p, onOpenLevel) }
            if (entries.isNotEmpty()) item("export") {
                NvButton("Export as a file", onExport, kind = ButtonKind.Secondary, icon = NvIcons.Share, modifier = Modifier.padding(top = Space.x6))
            }
        }
    }
}

@Composable
private fun ReflectionEntry(curriculum: Curriculum, p: LevelProgress, onOpenLevel: (String, Int) -> Unit) {
    val colors = Nv.colors
    val level = curriculum.levels.getValue(p.levelId)
    val accent = categoryAccent(curriculum.categoryById[level.categoryId]?.accent ?: "#2A3A5C", colors)
    var expanded by remember { mutableStateOf(false) }
    val note = Codec.fieldNote(p)
    Column(Modifier.fillMaxWidth().padding(vertical = Space.x4)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(curriculum.reference(level), style = Nv.type.numeral, color = accent)
            Spacer(Modifier.width(Space.x2))
            Text(level.title, style = Nv.type.titleSmall, color = colors.ink, modifier = Modifier.weight(1f))
        }
        (p.lastOpenedAt ?: p.masteredAt)?.let {
            Text(DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)), style = Nv.type.uiSmall, color = colors.inkTertiary)
        }
        if (p.reflection.isNotBlank()) {
            Spacer(Modifier.height(Space.x3))
            Text(
                p.reflection.trim(),
                style = Nv.type.body,
                color = colors.ink,
                maxLines = if (expanded) Int.MAX_VALUE else 5,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.clip(Shapes.chip).clickable(role = Role.Button) { expanded = !expanded },
            )
        }
        if (note.observed.isNotBlank()) {
            Spacer(Modifier.height(Space.x3))
            SectionLabel("Field note", color = colors.inkTertiary)
            Spacer(Modifier.height(Space.x1))
            Text("Saw: ${note.observed.trim()}", style = Nv.type.bodySmall, color = colors.inkSecondary)
            if (note.hypothesis.isNotBlank()) Text("Guessed: ${note.hypothesis.trim()}", style = Nv.type.bodySmall, color = colors.inkSecondary)
            if (note.disconfirmer.isNotBlank()) Text("Would have been wrong if: ${note.disconfirmer.trim()}", style = Nv.type.bodySmall, color = colors.inkSecondary)
        }
        NvButton("Open in the chapter", { onOpenLevel(level.id, 3) }, kind = ButtonKind.Quiet, tint = accent)
    }
}
