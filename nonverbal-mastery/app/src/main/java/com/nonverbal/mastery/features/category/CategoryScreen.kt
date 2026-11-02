package com.nonverbal.mastery.features.category

import android.graphics.BitmapFactory
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.nonverbal.mastery.data.db.LevelProgress
import com.nonverbal.mastery.data.db.LevelStatus
import com.nonverbal.mastery.data.models.Category
import com.nonverbal.mastery.data.models.Tier
import com.nonverbal.mastery.data.repo.ContentsEntry
import com.nonverbal.mastery.data.repo.Curriculum
import com.nonverbal.mastery.data.repo.Unlocks
import com.nonverbal.mastery.design.components.ButtonKind
import com.nonverbal.mastery.design.components.FigureStyle
import com.nonverbal.mastery.design.components.LocalPoseLibrary
import com.nonverbal.mastery.design.components.MarkerMode
import com.nonverbal.mastery.design.components.MasteredMark
import com.nonverbal.mastery.design.components.NvButton
import com.nonverbal.mastery.design.components.NvIcons
import com.nonverbal.mastery.design.components.PoseFigure
import com.nonverbal.mastery.design.components.ProgressRing
import com.nonverbal.mastery.design.components.Prose
import com.nonverbal.mastery.design.components.SectionLabel
import com.nonverbal.mastery.design.components.TopBar
import com.nonverbal.mastery.design.theme.CategoryTint
import com.nonverbal.mastery.design.theme.LocalCategoryTint
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.Shapes
import com.nonverbal.mastery.design.tokens.Space
import com.nonverbal.mastery.design.tokens.categoryAccent
import com.nonverbal.mastery.features.path.PathViewModel
import com.nonverbal.mastery.features.path.romanNumeral
import com.nonverbal.mastery.ui.nav.SharedKeys
import com.nonverbal.mastery.ui.nav.appViewModel
import com.nonverbal.mastery.ui.nav.sharedContainer
import com.nonverbal.mastery.ui.nav.sharedTitle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.CompositionLocalProvider

/** The category screen reads the same progress stream as the path. */
typealias CategoryViewModel = PathViewModel

fun tierLabel(t: Tier): String = when (t) {
    Tier.FOUNDATION -> "Foundation"
    Tier.INTERMEDIATE -> "Intermediate"
    Tier.ADVANCED -> "Advanced"
    Tier.EXPERT -> "Expert"
    Tier.MASTER -> "Master"
}

/** A pose that suits each part, for the drawn hero when no photograph is shipped. */
private fun heroPoseFor(categoryId: String): String = when (categoryId) {
    "foundations" -> "p_stand_neutral"
    "inner-world" -> "p_sit_closed_hunched"
    "social-world" -> "p_sit_lean_back_open"
    "modern-world" -> "p_stand_gesture_open"
    "overlooked-contexts" -> "p_stand_weight_shift"
    else -> "p_sit_neutral"
}

@Composable
fun CategoryRouteScreen(
    curriculum: Curriculum,
    categoryId: String,
    onBack: () -> Unit,
    onOpenLevel: (String) -> Unit,
    onCrisis: () -> Unit,
) {
    val vm = appViewModel { CategoryViewModel(it) }
    val progress by vm.progress.collectAsStateWithLifecycle()
    CategoryScreen(curriculum, categoryId, progress, onBack, onOpenLevel, onCrisis)
}

@Composable
fun CategoryScreen(
    curriculum: Curriculum,
    categoryId: String,
    progress: Map<String, LevelProgress>,
    onBack: () -> Unit,
    onOpenLevel: (String) -> Unit,
    onCrisis: () -> Unit,
) {
    val category = curriculum.categoryById[categoryId] ?: return
    val colors = Nv.colors
    val accent = categoryAccent(category.accent, colors)
    val number = curriculum.categoryNumber(categoryId)
    val contents = curriculum.contents(categoryId)
    val unlocked = Unlocks.unlockedIds(curriculum.levelsInOrder(categoryId), progress)
    val mastered = contents.count { it.level != null && progress[it.level.id]?.status == LevelStatus.MASTERED }
    val listState = rememberLazyListState()
    val barAlpha by remember { derivedStateOf { if (listState.firstVisibleItemIndex > 0) 1f else 0f } }

    CompositionLocalProvider(LocalCategoryTint provides CategoryTint(accent)) {
        Box(Modifier.fillMaxSize().background(colors.background)) {
            LazyColumn(state = listState, modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = Space.x16)) {
                item("hero") { CategoryHero(category, number, accent) }
                if (category.introduction.isNotBlank()) item("intro") {
                    Prose(category.introduction, Modifier.padding(horizontal = Space.readingGutter).padding(top = Space.x8))
                }
                item("progress") {
                    Row(
                        Modifier.padding(horizontal = Space.listGutter).padding(top = Space.x8, bottom = Space.x4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        val total = curriculum.plannedPerCategory[categoryId] ?: 15
                        ProgressRing(if (total == 0) 0f else mastered / total.toFloat(), size = 52.dp, color = accent) {
                            Text("$mastered", style = Nv.type.numeral, color = colors.ink)
                        }
                        Spacer(Modifier.width(Space.x4))
                        Column {
                            Text("$mastered of $total mastered", style = Nv.type.ui, color = colors.ink)
                            Text("${category.levels.size} of $total written", style = Nv.type.uiSmall, color = colors.inkTertiary)
                        }
                    }
                }
                if (categoryId == "inner-world") item("care") {
                    NvButton(
                        "If you're struggling right now, start here",
                        onCrisis,
                        kind = ButtonKind.Quiet,
                        icon = NvIcons.Heart,
                        modifier = Modifier.padding(horizontal = Space.listGutter - Space.x2),
                    )
                }
                contents.groupBy { it.tier }.forEach { (tier, entries) ->
                    item("tier-$tier") {
                        val range = "${entries.first().index}–${entries.last().index}"
                        SectionLabel(
                            "${tierLabel(tier)}  ·  $range",
                            Modifier.padding(horizontal = Space.listGutter).padding(top = Space.x8, bottom = Space.x2),
                            color = colors.inkTertiary,
                        )
                    }
                    entries.forEach { entry ->
                        item("entry-${entry.index}") {
                            val previous = contents.lastOrNull { it.index < entry.index && it.level != null }
                            ContentsRow(
                                number = number,
                                entry = entry,
                                unlocked = entry.level != null && entry.level.id in unlocked,
                                status = entry.level?.let { progress[it.id]?.status },
                                previousRef = previous?.let { "$number.${it.index}" },
                                onOpen = { entry.level?.let { onOpenLevel(it.id) } },
                            )
                        }
                    }
                }
                item("bottom") { Spacer(Modifier.windowInsetsPadding(WindowInsets.navigationBars)) }
            }
            TopBar(
                title = category.title,
                titleAlpha = barAlpha,
                onBack = onBack,
                background = colors.background.copy(alpha = barAlpha),
                divider = barAlpha > 0f,
            )
        }
    }
}

@Composable
private fun CategoryHero(category: Category, number: Int, accent: Color) {
    val colors = Nv.colors
    val context = LocalContext.current
    var bitmap by remember(category.heroAsset) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(category.heroAsset) {
        val name = category.heroAsset ?: return@LaunchedEffect
        bitmap = withContext(Dispatchers.IO) {
            runCatching {
                context.assets.open("heroes/$name").use { BitmapFactory.decodeStream(it)?.asImageBitmap() }
            }.getOrNull()
        }
    }
    val pose = LocalPoseLibrary.current[heroPoseFor(category.id)]
    Box(
        Modifier
            .fillMaxWidth()
            .height(340.dp)
            .sharedContainer(SharedKeys.categoryCard(category.id))
            .background(accent.copy(alpha = if (colors.isDark) 0.14f else 0.09f)),
    ) {
        val image = bitmap
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color.Transparent, colors.background.copy(alpha = 0.92f)), startY = 200f)))
        } else {
            // Drawn fallback: a large numeral and a quiet line figure, in the part's colour.
            Text(
                romanNumeral(number),
                style = Nv.type.numeralLarge.copy(fontSize = Nv.type.numeralLarge.fontSize * 3.2f, lineHeight = Nv.type.numeralLarge.lineHeight * 3.2f),
                color = accent.copy(alpha = 0.13f),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = Space.x12, end = Space.x4),
            )
            if (pose != null) PoseFigure(
                pose,
                Modifier.align(Alignment.CenterEnd).padding(end = Space.x6, top = Space.x10).size(width = 150.dp, height = 230.dp),
                style = FigureStyle.Outline,
                markers = MarkerMode.None,
                decorative = true,
                tint = accent.copy(alpha = 0.55f),
            )
            Canvas(Modifier.fillMaxSize()) {
                drawRect(Brush.verticalGradient(listOf(Color.Transparent, colors.background), startY = size.height * 0.55f))
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(horizontal = Space.listGutter, vertical = Space.x4)) {
            SectionLabel("Part ${romanNumeral(number)}", color = accent)
            Spacer(Modifier.height(Space.x2))
            Text(
                category.title,
                style = Nv.type.displayLarge,
                color = colors.ink,
                modifier = Modifier.sharedTitle(SharedKeys.categoryTitle(category.id)).semantics { heading() },
            )
            Spacer(Modifier.height(Space.x2))
            Text(category.tagline, style = Nv.type.marginalia.copy(fontSize = Nv.type.body.fontSize), color = colors.inkSecondary)
        }
    }
}

@Composable
private fun ContentsRow(
    number: Int,
    entry: ContentsEntry,
    unlocked: Boolean,
    status: String?,
    previousRef: String?,
    onOpen: () -> Unit,
) {
    val colors = Nv.colors
    val level = entry.level
    val written = level != null
    val mastered = status == LevelStatus.MASTERED
    val canOpen = written && unlocked
    val detail = when {
        !written -> "Not yet written"
        mastered -> "Mastered"
        !unlocked -> "Opens after ${previousRef ?: "the chapter before"}"
        status == LevelStatus.IN_PROGRESS -> "In progress  ·  ${level!!.estimatedMinutes} min"
        else -> "${level!!.estimatedMinutes} min"
    }
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (canOpen) Modifier.sharedContainer(SharedKeys.levelCard(level!!.id)) else Modifier)
            .clip(Shapes.card)
            .then(if (canOpen) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
            .heightIn(min = 64.dp)
            .padding(horizontal = Space.listGutter, vertical = Space.x3)
            .semantics(mergeDescendants = true) {
                contentDescription = "Chapter $number.${entry.index}, ${entry.title}. $detail"
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            entry.index.toString().padStart(2, '0'),
            style = Nv.type.numeral.copy(fontSize = Nv.type.body.fontSize * 0.9f),
            color = if (canOpen) Nv.tint else colors.inkFaint,
            modifier = Modifier.width(36.dp),
        )
        Column(Modifier.weight(1f)) {
            Text(
                entry.title,
                style = Nv.type.titleSmall.copy(fontStyle = if (written) FontStyle.Normal else FontStyle.Italic),
                color = if (canOpen || mastered) colors.ink else colors.inkTertiary,
                modifier = if (canOpen) Modifier.sharedTitle(SharedKeys.levelTitle(level!!.id)) else Modifier,
            )
            Spacer(Modifier.height(2.dp))
            Text(detail, style = Nv.type.uiSmall, color = colors.inkTertiary)
        }
        Spacer(Modifier.width(Space.x3))
        when {
            mastered -> MasteredMark()
            written && !unlocked -> Icon(NvIcons.Lock, null, tint = colors.inkFaint, modifier = Modifier.size(18.dp))
            canOpen -> Icon(NvIcons.Forward, null, tint = colors.inkFaint, modifier = Modifier.size(18.dp))
        }
    }
}
