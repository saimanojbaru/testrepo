package com.nonverbal.mastery.features.level

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nonverbal.mastery.data.db.LevelStatus
import com.nonverbal.mastery.data.models.Level
import com.nonverbal.mastery.data.repo.Curriculum
import com.nonverbal.mastery.data.repo.MasteryCheck
import com.nonverbal.mastery.data.repo.ReviewScheduler
import com.nonverbal.mastery.design.components.MarginNote
import com.nonverbal.mastery.design.components.MasteryBadge
import com.nonverbal.mastery.design.components.NvButton
import com.nonverbal.mastery.design.components.Prose
import com.nonverbal.mastery.design.components.ReflectionEditor
import com.nonverbal.mastery.design.components.SectionBreak
import com.nonverbal.mastery.design.components.SectionLabel
import com.nonverbal.mastery.design.components.inlineItalics
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.Shapes
import com.nonverbal.mastery.design.tokens.Space
import java.text.DateFormat
import java.util.Date
import java.util.concurrent.TimeUnit

@Composable
fun ReflectionItem(
    item: LevelItem,
    curriculum: Curriculum,
    level: Level,
    state: LevelState,
    check: MasteryCheck,
    actions: LevelActions,
    column: Modifier,
) {
    val colors = Nv.colors
    when (item.kind) {
        Kind.ReflectIntro -> Column(column) {
            SectionBreak(vertical = Space.sectionBreak + Space.x4)
            SectionLabel("Reflect")
            Spacer(Modifier.height(Space.x4))
            Prose(level.reflection.prompt)
            Spacer(Modifier.height(Space.x5))
            MarginNote("How to write this", level.reflection.guidance)
        }
        Kind.Editor -> ReflectionEditor(
            value = state.reflection,
            onValueChange = actions.editReflection,
            placeholder = "Start with the moment itself: where you were, who was there, what you saw.",
            minCharacters = level.reflection.minCharacters,
            modifier = column.padding(top = Space.x6),
        )
        Kind.References -> Column(column) {
            if (level.watch.isNotEmpty()) {
                SectionBreak(vertical = Space.sectionBreak + Space.x4)
                SectionLabel("Watch it happen")
                Spacer(Modifier.height(Space.x2))
                Text(
                    "Real footage where this can be seen. Each link opens outside the app, in YouTube or your browser.",
                    style = Nv.type.uiSmall, color = colors.inkTertiary,
                )
                Spacer(Modifier.height(Space.x4))
                val uri = LocalUriHandler.current
                Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
                    level.watch.forEach { w ->
                        Column(
                            Modifier.fillMaxWidth()
                                .clickable(role = Role.Button, onClickLabel = "Open the video outside the app") { runCatching { uri.openUri(w.url) } },
                        ) {
                            Text("${w.title.trim()}  ↗", style = Nv.type.bodySmall, color = Nv.tint)
                            Spacer(Modifier.height(Space.x1))
                            Text(
                                w.source.trim() + (w.at?.let { "  ·  from $it" } ?: ""),
                                style = Nv.type.uiSmall, color = colors.inkTertiary,
                            )
                            Spacer(Modifier.height(Space.x1))
                            Text(inlineItalics(w.notice.trim()), style = Nv.type.uiSmall, color = colors.inkSecondary)
                        }
                    }
                }
            }
            SectionBreak(vertical = Space.sectionBreak + Space.x4)
            SectionLabel("Further study")
            Spacer(Modifier.height(Space.x4))
            Column(verticalArrangement = Arrangement.spacedBy(Space.x4)) {
                level.furtherStudy.forEach { ref ->
                    Column {
                        Text(
                            buildAnnotatedString {
                                withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(ref.work.trim()) }
                                append("  —  ${ref.author.trim()}")
                            },
                            style = Nv.type.bodySmall, color = colors.ink,
                        )
                        ref.note?.let {
                            Spacer(Modifier.height(Space.x1))
                            Text(inlineItalics(it.trim()), style = Nv.type.uiSmall, color = colors.inkTertiary)
                        }
                    }
                }
            }
        }
        Kind.ReviewPreview -> Column(column.padding(top = Space.sectionBreak)) {
            SectionLabel("When this chapter returns", color = colors.inkTertiary)
            Spacer(Modifier.height(Space.x3))
            Text(inlineItalics(level.reviewPrompt.trim()), style = Nv.type.marginalia, color = colors.inkSecondary)
            Spacer(Modifier.height(Space.x2))
            Text(
                "After mastery it comes back after ${ReviewScheduler.INTERVAL_DAYS.joinToString(", ")} days.",
                style = Nv.type.uiSmall, color = colors.inkTertiary,
            )
        }
        Kind.Mastery -> MasteryCard(curriculum, level, state, check, actions, column.padding(top = Space.sectionBreak + Space.x4))
        else -> Unit
    }
}

/**
 * The chapter's last page. Not a floating button: a card that closes the reading and says,
 * specifically, what remains.
 */
@Composable
private fun MasteryCard(
    curriculum: Curriculum,
    level: Level,
    state: LevelState,
    check: MasteryCheck,
    actions: LevelActions,
    modifier: Modifier,
) {
    val colors = Nv.colors
    val p = state.progress
    val mastered = p?.status == LevelStatus.MASTERED
    val next = curriculum.levelsInOrder(level.categoryId).firstOrNull { it.index > level.index }
    Column(
        modifier
            .fillMaxWidth()
            .clip(Shapes.large)
            .background(if (mastered) colors.brass.copy(alpha = 0.08f) else colors.surface)
            .border(Shapes.hairline, if (mastered || check.ready) colors.brass.copy(alpha = 0.5f) else colors.divider, Shapes.large)
            .padding(Space.x6)
            .semantics { liveRegion = LiveRegionMode.Polite },
        horizontalAlignment = Alignment.Start,
    ) {
        when {
            mastered -> {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MasteryBadge(size = 52.dp, animate = state.justMastered)
                    Spacer(Modifier.width(Space.x4))
                    Column {
                        SectionLabel("Mastered", color = colors.brassText)
                        p?.masteredAt?.let {
                            Text(DateFormat.getDateInstance(DateFormat.LONG).format(Date(it)), style = Nv.type.numeral, color = colors.inkTertiary)
                        }
                    }
                }
                Spacer(Modifier.height(Space.x5))
                Text(
                    if (state.justMastered) "Level mastered. The next one is open." else masteredLine(p?.nextReviewAt),
                    style = Nv.type.marginalia.copy(fontSize = Nv.type.body.fontSize),
                    color = colors.ink,
                )
                if (next != null) {
                    Spacer(Modifier.height(Space.x5))
                    NvButton("Open ${curriculum.reference(next)}  ${next.title}", { actions.onOpenLevel(next.id) }, tint = colors.brassText)
                }
            }
            check.ready -> {
                SectionLabel("Ready")
                Spacer(Modifier.height(Space.x3))
                Text("Everything this chapter asks of you is done.", style = Nv.type.body, color = colors.ink)
                Spacer(Modifier.height(Space.x5))
                NvButton("Master this level", actions.master, floating = true)
            }
            else -> {
                SectionLabel("Mastery")
                Spacer(Modifier.height(Space.x3))
                Text("You still need to:", style = Nv.type.body, color = colors.ink)
                Spacer(Modifier.height(Space.x2))
                check.remaining().forEach { line ->
                    Row(Modifier.padding(vertical = Space.x1)) {
                        Text("–", style = Nv.type.body, color = Nv.tint, modifier = Modifier.width(20.dp))
                        Text(line, style = Nv.type.body, color = colors.inkSecondary)
                    }
                }
            }
        }
    }
}

private fun masteredLine(nextReviewAt: Long?): String {
    if (nextReviewAt == null) return "Retained. This chapter no longer returns for review; it is yours."
    val days = TimeUnit.MILLISECONDS.toDays(nextReviewAt - System.currentTimeMillis()).coerceAtLeast(0)
    return when (days) {
        0L -> "It's back for review today."
        1L -> "It returns for review tomorrow."
        else -> "It returns for review in $days days."
    }
}
