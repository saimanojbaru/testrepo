package com.nonverbal.mastery.features.poselib

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nonverbal.mastery.data.db.PoseBookmark
import com.nonverbal.mastery.data.models.Pose
import com.nonverbal.mastery.data.repo.Curriculum
import com.nonverbal.mastery.design.components.AnnotationLegendRow
import com.nonverbal.mastery.design.components.EmptyState
import com.nonverbal.mastery.design.components.IconAction
import com.nonverbal.mastery.design.components.MarkerMode
import com.nonverbal.mastery.design.components.NavRow
import com.nonverbal.mastery.design.components.NvIcons
import com.nonverbal.mastery.design.components.PoseFigure
import com.nonverbal.mastery.design.components.SectionLabel
import com.nonverbal.mastery.design.components.SegmentedTabs
import com.nonverbal.mastery.design.components.TabSpec
import com.nonverbal.mastery.design.components.TopBar
import com.nonverbal.mastery.design.components.accessibleDescription
import com.nonverbal.mastery.design.components.readingColumn
import com.nonverbal.mastery.design.pose.PoseRenderer
import com.nonverbal.mastery.design.pose.PoseSolver
import com.nonverbal.mastery.design.pose.yawFor
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.Shapes
import com.nonverbal.mastery.design.tokens.Space
import com.nonverbal.mastery.design.tokens.annotationColor
import com.nonverbal.mastery.design.tokens.enterSpec
import com.nonverbal.mastery.ui.nav.appViewModel

private val Views = listOf("front" to "Front", "three-quarter" to "¾", "side" to "Side", "back" to "Back")

@Composable
fun PoseDetailRouteScreen(
    curriculum: Curriculum,
    poseId: String,
    compareWith: String?,
    onBack: () -> Unit,
    onOpenLevel: (String) -> Unit,
) {
    val vm = appViewModel { PoseSearchViewModel(it) }
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    PoseDetailScreen(
        curriculum, poseId, compareWith, bookmarks.firstOrNull { it.poseId == poseId },
        onBack, onOpenLevel, { vm.toggleBookmark(poseId, it) }, { vm.saveNote(poseId, it) },
    )
}

@Composable
fun PoseDetailScreen(
    curriculum: Curriculum,
    poseId: String,
    compareWith: String?,
    bookmark: PoseBookmark?,
    onBack: () -> Unit,
    onOpenLevel: (String) -> Unit,
    onBookmark: (Boolean) -> Unit,
    onNote: (String) -> Unit,
) {
    val colors = Nv.colors
    val pose = curriculum.poses[poseId]
    if (pose == null) {
        Column(Modifier.fillMaxSize().background(colors.background)) {
            TopBar(onBack = onBack)
            EmptyState("That plate isn't in this edition.")
        }
        return
    }
    val other: Pose? = compareWith?.let { curriculum.poses[it] }
    val baseView = pose.params.view
    var viewIndex by remember(poseId) { mutableIntStateOf(Views.indexOfFirst { it.first == baseView }.coerceAtLeast(0)) }
    val yaw by animateFloatAsState(yawFor(Views[viewIndex].first), enterSpec(420), label = "yaw")
    var morph by remember(poseId, compareWith) { mutableFloatStateOf(0f) }

    val a = remember(pose) { PoseSolver.solveScene(pose) }
    val b = remember(other) { other?.let { PoseSolver.solveScene(it) } }
    val scene = (if (b != null && morph > 0f) a.lerp(b, morph) else a).copy(yaw = yaw)
    val frame = remember(a, b) {
        // One frame for every angle, so the figure does not jump in size as it turns.
        val angles = listOf(0f, 35f, 90f, 180f)
        angles.map { y -> PoseRenderer.frameOf(a.copy(yaw = y)) }
            .plus(if (b != null) angles.map { y -> PoseRenderer.frameOf(b.copy(yaw = y)) } else emptyList())
            .reduce { x, y -> x.union(y) }
    }
    val saved = bookmark != null

    Column(Modifier.fillMaxSize().background(colors.background)) {
        TopBar(title = pose.title, onBack = onBack) {
            IconAction(
                if (saved) NvIcons.BookmarkFilled else NvIcons.Bookmark,
                if (saved) "Remove from saved plates" else "Save this plate",
                { onBookmark(!saved) },
                tint = if (saved) colors.brass else colors.inkSecondary,
            )
        }
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            Box(
                Modifier.padding(horizontal = Space.listGutter).fillMaxWidth().height(400.dp).clip(Shapes.large).background(colors.surface)
                    .semantics { contentDescription = pose.accessibleDescription() },
            ) {
                PoseFigure(pose, Modifier.fillMaxSize(), markers = if (morph < 0.5f) MarkerMode.Numbered else MarkerMode.None,
                    scene = scene, frame = frame, decorative = true)
            }
            SegmentedTabs(Views.map { TabSpec(it.second) }, viewIndex, { viewIndex = it }, Modifier.padding(top = Space.x2))
            if (other != null && b != null) {
                Column(Modifier.padding(horizontal = Space.listGutter).padding(top = Space.x3)) {
                    SectionLabel("Morph between the pair", color = colors.inkTertiary)
                    Slider(
                        value = morph,
                        onValueChange = { morph = it },
                        modifier = Modifier.semantics { stateDescription = "${(morph * 100).toInt()} percent toward ${other.title}" },
                        colors = SliderDefaults.colors(thumbColor = colors.brass, activeTrackColor = colors.brass, inactiveTrackColor = colors.divider),
                    )
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(pose.title, style = Nv.type.uiSmall, color = colors.inkSecondary)
                        Text(other.title, style = Nv.type.uiSmall, color = colors.inkSecondary)
                    }
                }
            }
            Column(Modifier.readingColumn(560.dp).padding(top = Space.x6, bottom = Space.x16)) {
                SectionLabel(ThemeNames[pose.theme] ?: pose.theme, color = Nv.tint)
                Spacer(Modifier.height(Space.x2))
                Text(pose.title, style = Nv.type.display, color = colors.ink, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(Space.x4))
                Text(pose.desc, style = Nv.type.body, color = colors.ink)
                if (pose.annotations.isNotEmpty()) {
                    Spacer(Modifier.height(Space.x6))
                    SectionLabel("What to notice", color = colors.inkTertiary)
                    Spacer(Modifier.height(Space.x3))
                    Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
                        pose.annotations.forEachIndexed { i, ann ->
                            AnnotationLegendRow(i + 1, ann.text, annotationColor(ann.color, colors))
                        }
                    }
                }
                val uses = curriculum.poseUsage[pose.id].orEmpty()
                Spacer(Modifier.height(Space.x8))
                SectionLabel("Used in", color = colors.inkTertiary)
                if (uses.isEmpty()) {
                    Spacer(Modifier.height(Space.x2))
                    Text("No chapter uses this plate yet.", style = Nv.type.uiSmall, color = colors.inkTertiary)
                }
                uses.forEach { use ->
                    val level = curriculum.levels[use.levelId] ?: return@forEach
                    NavRow(
                        "${curriculum.reference(level)}  ${level.title}",
                        { onOpenLevel(level.id) },
                        detail = if (use.asPair) "In a do / don't comparison" else "As a figure in the teaching",
                    )
                }
                if (saved) {
                    Spacer(Modifier.height(Space.x6))
                    SectionLabel("Your note", color = colors.inkTertiary)
                    Spacer(Modifier.height(Space.x2))
                    var note by remember(poseId) { mutableStateOf(bookmark?.note.orEmpty()) }
                    LaunchedEffect(note) { if (note != bookmark?.note) { kotlinx.coroutines.delay(500); onNote(note) } }
                    BasicTextField(
                        value = note,
                        onValueChange = { note = it },
                        textStyle = Nv.type.bodySmall.copy(color = colors.ink),
                        cursorBrush = SolidColor(Nv.tint),
                        modifier = Modifier.fillMaxWidth().heightIn(min = 96.dp).clip(Shapes.card).background(colors.surface)
                            .border(1.dp, colors.divider, Shapes.card).padding(Space.x4)
                            .semantics { contentDescription = "Your note on this plate" },
                        decorationBox = { inner ->
                            Box {
                                if (note.isEmpty()) Text("Where did you last see this?", style = Nv.type.bodySmall, color = colors.inkTertiary)
                                inner()
                            }
                        },
                    )
                }
            }
        }
    }
}
