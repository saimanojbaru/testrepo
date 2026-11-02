package com.nonverbal.mastery.features.level

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.nonverbal.mastery.data.models.Level
import com.nonverbal.mastery.data.repo.FieldNote
import com.nonverbal.mastery.design.components.ButtonKind
import com.nonverbal.mastery.design.components.DrillCard
import com.nonverbal.mastery.design.components.MasteredMark
import com.nonverbal.mastery.design.components.NvButton
import com.nonverbal.mastery.design.components.Prose
import com.nonverbal.mastery.design.components.SectionBreak
import com.nonverbal.mastery.design.components.SectionLabel
import com.nonverbal.mastery.design.components.inlineItalics
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.Shapes
import com.nonverbal.mastery.design.tokens.Space
import java.text.DateFormat
import java.util.Date

@Composable
fun DrillsItem(item: LevelItem, level: Level, state: LevelState, actions: LevelActions, column: Modifier) {
    val colors = Nv.colors
    when (item.kind) {
        Kind.PracticeIntro -> Column(column) {
            SectionBreak(vertical = Space.sectionBreak + Space.x4)
            SectionLabel("In the field")
            Spacer(Modifier.height(Space.x4))
            Prose(
                "Reading becomes skill only outside the page. Choose one of these assignments, do it this week, and log what you saw, what you guessed, and what would have shown you wrong.",
                style = Nv.type.bodySmall, color = colors.inkSecondary,
            )
        }
        Kind.Observe -> Row(column.padding(top = Space.x5)) {
            Text(
                "${item.i + 1}",
                style = Nv.type.numeral.copy(fontSize = Nv.type.body.fontSize * 0.85f),
                color = Nv.tint,
                modifier = Modifier.width(28.dp).padding(top = 3.dp),
            )
            Text(inlineItalics(level.observe[item.i].trim()), style = Nv.type.body, color = colors.ink)
        }
        Kind.FieldLog -> FieldMission(level, state, actions, column.padding(top = Space.x8))
        Kind.Drill -> {
            val done = (state.progress?.drillsDone ?: 0) and (1 shl item.i) != 0
            Column(column.padding(top = if (item.i == 0) Space.sectionBreak else Space.x3)) {
                if (item.i == 0) {
                    SectionLabel("Drills")
                    Spacer(Modifier.height(Space.x2))
                    Text(
                        "Each can be done alone, in under half an hour, in an ordinary place. Mark them as you finish.",
                        style = Nv.type.bodySmall, color = colors.inkSecondary,
                    )
                    Spacer(Modifier.height(Space.x4))
                }
                DrillCard(level.drills[item.i], item.i + 1, done, onToggle = { actions.toggleDrill(item.i, it) })
            }
        }
        else -> Unit
    }
}

/**
 * The field mission log: a hypothesis written before you check it. Four short fields, the
 * method of the whole book in miniature.
 */
@Composable
private fun FieldMission(level: Level, state: LevelState, actions: LevelActions, modifier: Modifier) {
    val colors = Nv.colors
    val note = state.fieldNote
    val logged = state.progress?.fieldLogged == true
    var editing by remember(logged) { mutableStateOf(!logged) }
    Column(
        modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(colors.surface)
            .border(Shapes.hairline, if (logged) colors.brass.copy(alpha = 0.5f) else colors.divider, Shapes.card)
            .padding(Space.x5),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                SectionLabel("Field mission")
                Spacer(Modifier.height(2.dp))
                Text(if (logged) "Logged" else "Log one assignment", style = Nv.type.claim, color = colors.ink)
            }
            if (logged) MasteredMark()
        }
        if (logged && !editing) {
            Spacer(Modifier.height(Space.x3))
            Text("Assignment ${note.assignment + 1}" + (note.loggedAt?.let { "  ·  " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(it)) } ?: ""),
                style = Nv.type.numeral, color = colors.inkTertiary)
            Spacer(Modifier.height(Space.x2))
            Text(note.observed, style = Nv.type.bodySmall, color = colors.ink)
            Spacer(Modifier.height(Space.x3))
            NvButton("Edit the note", { editing = true }, kind = ButtonKind.Quiet)
            return@Column
        }
        Spacer(Modifier.height(Space.x4))
        Text("Which assignment?", style = Nv.type.uiSmall, color = colors.inkSecondary)
        Spacer(Modifier.height(Space.x2))
        Row(horizontalArrangement = Arrangement.spacedBy(Space.x2)) {
            level.observe.indices.forEach { i ->
                val sel = note.assignment == i
                Text(
                    "${i + 1}",
                    style = Nv.type.numeral,
                    color = if (sel) colors.onAccent else colors.inkSecondary,
                    modifier = Modifier
                        .size(Space.touchTarget)
                        .clip(CircleShape)
                        .background(if (sel) Nv.tint else colors.surfaceSunken)
                        .selectable(sel, role = Role.RadioButton) { actions.editFieldNote(note.copy(assignment = i)) }
                        .padding(top = 15.dp)
                        .semantics { contentDescription = "Assignment ${i + 1}" },
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                )
            }
        }
        FieldInput("What did you see? Describe it plainly, with timing.", note.observed) { actions.editFieldNote(note.copy(observed = it)) }
        FieldInput("What did you guess it meant?", note.hypothesis) { actions.editFieldNote(note.copy(hypothesis = it)) }
        FieldInput("What would have shown you were wrong?", note.disconfirmer) { actions.editFieldNote(note.copy(disconfirmer = it)) }
        FieldInput("What happened? (optional)", note.outcome) { actions.editFieldNote(note.copy(outcome = it)) }
        Spacer(Modifier.height(Space.x4))
        NvButton(
            "Log this field note",
            { actions.logFieldNote(); editing = false },
            enabled = note.isComplete,
        )
        if (!note.isComplete) {
            Spacer(Modifier.height(Space.x2))
            Text(
                "A few words in each of the first three fields. The third matters most.",
                style = Nv.type.uiSmall, color = colors.inkTertiary,
            )
        }
    }
}

@Composable
private fun FieldInput(label: String, value: String, onChange: (String) -> Unit) {
    val colors = Nv.colors
    Spacer(Modifier.height(Space.x4))
    Text(label, style = Nv.type.uiSmall, color = colors.inkSecondary)
    Spacer(Modifier.height(Space.x2))
    BasicTextField(
        value = value,
        onValueChange = onChange,
        textStyle = Nv.type.bodySmall.copy(color = colors.ink),
        cursorBrush = SolidColor(Nv.tint),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = Space.touchTarget)
            .clip(Shapes.chip)
            .background(colors.background)
            .border(Shapes.hairline, colors.divider, Shapes.chip)
            .padding(Space.x3)
            .semantics { contentDescription = label },
    )
}

@Suppress("unused")
private fun FieldNote.summary(): String = observed.take(120)
