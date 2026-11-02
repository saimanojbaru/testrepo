package com.nonverbal.mastery.features.level

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
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
        Kind.Failure -> Column(column.padding(top = Space.sectionBreak + Space.x4)) {
            SectionLabel("How this goes wrong", color = colors.dontText)
            Spacer(Modifier.height(Space.x4))
            Prose(level.failureMode)
        }
        else -> Unit
    }
}
