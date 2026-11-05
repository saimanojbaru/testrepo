package com.nonverbal.mastery.features.level

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nonverbal.mastery.design.components.inlineItalics
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nonverbal.mastery.data.models.Level
import com.nonverbal.mastery.data.repo.Curriculum
import com.nonverbal.mastery.data.repo.MasteryCheck
import com.nonverbal.mastery.design.components.Diagram
import com.nonverbal.mastery.design.components.DiagramKind
import com.nonverbal.mastery.design.components.DropCapParagraph
import com.nonverbal.mastery.design.components.LocalReadingMeasure
import com.nonverbal.mastery.design.components.PosePairView
import com.nonverbal.mastery.design.components.Prose
import com.nonverbal.mastery.design.components.SectionBreak
import com.nonverbal.mastery.design.components.SectionLabel
import com.nonverbal.mastery.design.components.TeachBlockView
import com.nonverbal.mastery.design.components.readingColumn
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.Space

/** Dispatches one planned item of the chapter to its section's renderer. */
@Composable
fun LevelItemView(
    item: LevelItem,
    curriculum: Curriculum,
    level: Level,
    state: LevelState,
    check: MasteryCheck,
    actions: LevelActions,
) {
    val column = Modifier.readingColumn(LocalReadingMeasure.current)
    when (item.section) {
        0 -> ReadingItem(item, curriculum, level, actions, column)
        1 -> DrillsItem(item, level, state, actions, column)
        2 -> AssessmentItem(item, level, state, actions, column)
        else -> ReflectionItem(item, curriculum, level, state, check, actions, column)
    }
}

@Composable
private fun ReadingItem(item: LevelItem, curriculum: Curriculum, level: Level, actions: LevelActions, column: Modifier) {
    val colors = Nv.colors
    when (item.kind) {
        Kind.Hook -> DropCapParagraph(level.hook, column.padding(top = Space.x6))
        Kind.Why -> Column(column.padding(top = Space.sectionBreak)) {
            SectionLabel("Why this matters")
            Spacer(Modifier.height(Space.x4))
            Prose(level.why)
        }
        Kind.Teach -> {
            val block = level.teach[item.i]
            // Figures are numbered by chapter: Fig. 1.11.3
            val figNumber = level.teach.take(item.i + 1).count { it.poseId != null }
            TeachBlockView(
                number = item.i + 1,
                block = block,
                figureLabel = "Fig. ${curriculum.reference(level)}.$figNumber",
                modifier = column.padding(top = Space.sectionBreak + Space.x2),
                onPoseClick = { actions.onPose(it, null) },
                diagram = DiagramKind.of(block.diagram)?.let { kind -> { Diagram(kind) } },
            )
        }
        Kind.Pair -> Column(column.padding(top = Space.sectionBreak)) {
            SectionLabel("Compare", color = colors.inkTertiary)
            Spacer(Modifier.height(Space.x3))
            PosePairView(level.poseGallery[item.i], onPoseClick = { id, other -> actions.onPose(id, other) })
        }
        Kind.Case -> Column(column) {
            SectionBreak(vertical = Space.sectionBreak + Space.x4)
            SectionLabel("Case study")
            Spacer(Modifier.height(Space.x4))
            Prose(level.caseStudy)
        }
        Kind.MoreCase -> {
            val c = level.moreCases[item.i]
            Column(column.padding(top = Space.sectionBreak)) {
                SectionLabel("Case study ${item.i + 2}", color = colors.inkTertiary)
                Spacer(Modifier.height(Space.x2))
                Text(inlineItalics(c.title), style = Nv.type.claim, color = colors.ink, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(Space.x4))
                Prose(c.scene)
                Spacer(Modifier.height(Space.x5))
                SectionLabel("The misreading", color = colors.dontText)
                Spacer(Modifier.height(Space.x2))
                Prose(c.misread)
                Spacer(Modifier.height(Space.x5))
                SectionLabel("A better reading", color = colors.doText)
                Spacer(Modifier.height(Space.x2))
                Prose(c.betterRead)
            }
        }
        Kind.Worked -> level.workedReading?.let { r ->
            Column(column) {
                SectionBreak(vertical = Space.sectionBreak + Space.x4)
                SectionLabel("A worked reading")
                Spacer(Modifier.height(Space.x2))
                Text(inlineItalics(r.title), style = Nv.type.claim, color = colors.ink, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(Space.x4))
                Prose(r.scene)
                r.steps.forEachIndexed { n, step ->
                    Row(Modifier.padding(top = Space.x6)) {
                        Text(
                            "${n + 1}",
                            style = Nv.type.numeral.copy(fontSize = Nv.type.body.fontSize * 0.85f),
                            color = Nv.tint,
                            modifier = Modifier.width(28.dp).padding(top = 1.dp),
                        )
                        Column(Modifier.weight(1f)) {
                            Text(step.label.uppercase(), style = Nv.type.label, color = Nv.tint)
                            Spacer(Modifier.height(Space.x2))
                            Prose(step.text)
                        }
                    }
                }
            }
        }
        Kind.Failure -> Column(column.padding(top = Space.sectionBreak + Space.x4)) {
            SectionLabel("How this goes wrong", color = colors.dontText)
            Spacer(Modifier.height(Space.x4))
            Prose(level.failureMode)
        }
        Kind.MoreFailure -> {
            val f = level.moreFailures[item.i]
            Column(column.padding(top = Space.x8)) {
                Text(inlineItalics(f.title), style = Nv.type.claim, color = colors.ink, modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(Space.x3))
                Prose(f.body)
            }
        }
        Kind.ReaderQ -> {
            val q = level.questions[item.i]
            Column(column.padding(top = if (item.i == 0) 0.dp else Space.x8)) {
                if (item.i == 0) {
                    SectionBreak(vertical = Space.sectionBreak + Space.x4)
                    SectionLabel("Questions readers ask")
                    Spacer(Modifier.height(Space.x5))
                }
                Text(inlineItalics(q.q), style = Nv.type.marginalia.copy(fontSize = Nv.type.body.fontSize), color = colors.ink,
                    modifier = Modifier.semantics { heading() })
                Spacer(Modifier.height(Space.x3))
                Prose(q.a)
            }
        }
        else -> Unit
    }
}
