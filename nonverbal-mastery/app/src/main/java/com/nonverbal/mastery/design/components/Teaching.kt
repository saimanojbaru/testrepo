package com.nonverbal.mastery.design.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nonverbal.mastery.data.models.Drill
import com.nonverbal.mastery.data.models.Pose
import com.nonverbal.mastery.data.models.PosePair
import com.nonverbal.mastery.data.models.Question
import com.nonverbal.mastery.data.models.TeachBlock
import com.nonverbal.mastery.design.pose.PoseRenderer
import com.nonverbal.mastery.design.pose.PoseSolver
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.Shapes
import com.nonverbal.mastery.design.tokens.Space
import com.nonverbal.mastery.design.tokens.enterSpec

// ---- PosePair ------------------------------------------------------------------------------

/**
 * Two panels, DON'T on the left (dimmed figure, don't-red wash) and DO on the right
 * (full figure, do-green wash), a caption under each and the shared annotation below.
 */
@Composable
fun PosePairView(
    pair: PosePair,
    modifier: Modifier = Modifier,
    onPoseClick: (poseId: String, counterpartId: String) -> Unit = { _, _ -> },
) {
    val library = LocalPoseLibrary.current
    val dont = library[pair.dontPoseId]
    val doPose = library[pair.doPoseId]
    val colors = Nv.colors
    if (dont == null || doPose == null) return
    val frame = remember(dont, doPose) {
        PoseRenderer.frameOf(PoseSolver.solveScene(dont)).union(PoseRenderer.frameOf(PoseSolver.solveScene(doPose)))
    }

    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.x3)) {
            PairPanel(
                label = "Don't", caption = pair.dontCaption, pose = dont, dimmed = true,
                accent = colors.dontRed, labelColor = colors.dontText, frame = frame, castSeed = doPose.id,
                modifier = Modifier.weight(1f),
                onClick = { onPoseClick(dont.id, doPose.id) },
            )
            PairPanel(
                label = "Do", caption = pair.doCaption, pose = doPose, dimmed = false,
                accent = colors.doGreen, labelColor = colors.doText, frame = frame, castSeed = doPose.id,
                modifier = Modifier.weight(1f),
                onClick = { onPoseClick(doPose.id, dont.id) },
            )
        }
        Spacer(Modifier.height(Space.x4))
        Row(Modifier.fillMaxWidth()) {
            Text("↳", style = Nv.type.bodySmall, color = Nv.tint, modifier = Modifier.padding(end = Space.x2))
            Text(inlineItalics(pair.annotation.trim()), style = Nv.type.bodySmall, color = colors.inkSecondary)
        }
    }
}

@Composable
private fun PairPanel(
    label: String,
    caption: String,
    pose: Pose,
    dimmed: Boolean,
    accent: Color,
    labelColor: Color,
    frame: com.nonverbal.mastery.design.pose.FigureFrame,
    castSeed: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val colors = Nv.colors
    val description = remember(pose, caption, label) { "$label. ${caption.trim()} Figure: ${pose.accessibleDescription()}" }
    Column(modifier) {
        Text(label.uppercase(), style = Nv.type.labelSmall, color = labelColor)
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(0.8f)
                .clip(Shapes.card)
                .background(accent.copy(alpha = 0.06f).compositeOver(colors.background))
                .border(Shapes.hairline, accent.copy(alpha = 0.4f), Shapes.card)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { contentDescription = description },
        ) {
            PoseFigure(pose, Modifier.fillMaxSize(), dimmed = dimmed, markers = MarkerMode.Dots, frame = frame, decorative = true, castSeed = castSeed)
        }
        Spacer(Modifier.height(Space.x2))
        Text(inlineItalics(caption.trim()), style = Nv.type.caption, color = colors.inkSecondary)
    }
}

private fun Color.compositeOver(bg: Color): Color {
    val a = alpha
    return Color(red * a + bg.red * (1 - a), green * a + bg.green * (1 - a), blue * a + bg.blue * (1 - a), 1f)
}

// ---- Figure plate ---------------------------------------------------------------------------

/** A single figure set like a book plate, with "Fig." caption. Tapping opens the pose. */
@Composable
fun FigurePlate(
    pose: Pose,
    figureLabel: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit = {},
) {
    val colors = Nv.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(vertical = Space.x3),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        PoseFigure(pose, Modifier.width(180.dp).height(220.dp), markers = MarkerMode.Numbered)
        Spacer(Modifier.height(Space.x3))
        Text(
            "$figureLabel  ·  ${pose.title}",
            style = Nv.type.caption,
            color = colors.inkSecondary,
            textAlign = TextAlign.Center,
        )
        if (pose.annotations.isNotEmpty()) {
            Spacer(Modifier.height(Space.x2))
            Column(Modifier.widthIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(Space.x1)) {
                pose.annotations.forEachIndexed { i, a ->
                    AnnotationLegendRow(i + 1, a.text, com.nonverbal.mastery.design.tokens.annotationColor(a.color, colors))
                }
            }
        }
    }
}

@Composable
fun AnnotationLegendRow(number: Int, text: String, color: Color, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
        Box(Modifier.padding(top = 2.dp).size(18.dp).clip(CircleShape).background(color), contentAlignment = Alignment.Center) {
            Text(number.toString(), style = Nv.type.numeral.copy(fontSize = Nv.type.labelSmall.fontSize), color = Nv.colors.surface)
        }
        Spacer(Modifier.width(Space.x2))
        Text(text, style = Nv.type.uiSmall, color = Nv.colors.inkSecondary)
    }
}

// ---- TeachBlock -----------------------------------------------------------------------------

/**
 * One claim and its argument. The heading is a claim, set in sans; the argument in serif.
 * A pull quote may break the column; the figure plate follows; the disconfirming condition
 * closes the block as a marginal note.
 */
@Composable
fun TeachBlockView(
    number: Int,
    block: TeachBlock,
    figureLabel: String,
    modifier: Modifier = Modifier,
    onPoseClick: (String) -> Unit = {},
    diagram: (@Composable () -> Unit)? = null,
) {
    val pose = block.poseId?.let { LocalPoseLibrary.current[it] }
    Column(modifier.fillMaxWidth()) {
        Text(number.toString().padStart(2, '0'), style = Nv.type.numeral, color = Nv.tint)
        Spacer(Modifier.height(Space.x2))
        Text(block.h.trim(), style = Nv.type.claim.copy(fontSize = Nv.type.claim.fontSize * (Nv.type.bodySize / 17f)), color = Nv.colors.ink)
        Spacer(Modifier.height(Space.x4))
        val paragraphs = proseParagraphs(block.p)
        val firstHalf = paragraphs.take((paragraphs.size + 1) / 2)
        val secondHalf = paragraphs.drop(firstHalf.size)
        Prose(firstHalf.joinToString("\n\n"))
        if (pose != null) {
            Spacer(Modifier.height(Space.x6))
            FigurePlate(pose, figureLabel, onClick = { onPoseClick(pose.id) })
        }
        if (secondHalf.isNotEmpty()) {
            Spacer(Modifier.height(Space.x6))
            Prose(secondHalf.joinToString("\n\n"))
        }
        block.pullQuote?.let { PullQuote(it, breathing = Space.x10) }
        if (diagram != null) {
            Spacer(Modifier.height(Space.x6))
            diagram()
        }
        block.disconfirm?.let {
            Spacer(Modifier.height(if (block.pullQuote != null) 0.dp else Space.x6))
            MarginNote("What would prove this wrong", it)
        }
    }
}

// ---- DrillCard ------------------------------------------------------------------------------

@Composable
fun DrillCard(
    drill: Drill,
    number: Int,
    done: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Nv.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(Shapes.card)
            .background(colors.surface)
            .border(Shapes.hairline, if (done) colors.brass.copy(alpha = 0.5f) else colors.divider, Shapes.card)
            .padding(horizontal = Space.x4, vertical = Space.x2),
    ) {
        CheckRow(checked = done, onToggle = onToggle, modifier = Modifier.semantics {
            stateDescription = if (done) "Drill complete" else "Not yet done"
        }) {
            Column(Modifier.weight(1f)) {
                Text("Drill ${number}", style = Nv.type.label, color = Nv.tint)
                Spacer(Modifier.height(2.dp))
                Text(drill.name, style = Nv.type.claim, color = colors.ink)
            }
            Spacer(Modifier.width(Space.x2))
            Pill("${drill.minutes} min")
        }
        Spacer(Modifier.height(Space.x2))
        Column(Modifier.padding(start = 36.dp, bottom = Space.x3), verticalArrangement = Arrangement.spacedBy(Space.x3)) {
            drill.steps.forEach { step ->
                Row {
                    Text("→", style = Nv.type.bodySmall, color = Nv.tint, modifier = Modifier.padding(end = Space.x2))
                    Text(inlineItalics(step.trim()), style = Nv.type.bodySmall, color = colors.ink)
                }
            }
            if (drill.variations.isNotEmpty()) {
                Spacer(Modifier.height(Space.x1))
                Text("VARIATIONS", style = Nv.type.labelSmall, color = colors.inkTertiary)
                drill.variations.forEach { v ->
                    Row {
                        Text("◦", style = Nv.type.bodySmall, color = colors.inkTertiary, modifier = Modifier.padding(end = Space.x2))
                        Text(inlineItalics(v.trim()), style = Nv.type.bodySmall, color = colors.inkSecondary)
                    }
                }
            }
        }
    }
}

// ---- QuizQuestion ---------------------------------------------------------------------------

@Composable
fun QuizQuestionView(
    question: Question,
    number: Int,
    total: Int,
    selected: Int?,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Nv.colors
    val answered = selected != null
    Column(modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Question $number of $total", style = Nv.type.label, color = colors.inkTertiary)
            if (question.isMisconceptionTest) { Spacer(Modifier.width(Space.x2)); Pill("Misconception check") }
            if (question.isScenario) { Spacer(Modifier.width(Space.x2)); Pill("Scenario") }
        }
        Spacer(Modifier.height(Space.x3))
        Text(inlineItalics(question.q.trim()), style = Nv.type.body, color = colors.ink)
        Spacer(Modifier.height(Space.x4))
        Column(verticalArrangement = Arrangement.spacedBy(Space.x2)) {
            question.options.forEachIndexed { i, option ->
                val isCorrect = i == question.answer
                val isChosen = i == selected
                val border = when {
                    answered && isCorrect -> colors.doGreen
                    answered && isChosen -> colors.dontRed
                    else -> colors.divider
                }
                val fade = if (answered && !isCorrect && !isChosen) 0.7f else 1f
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clip(Shapes.chip)
                        .background(if (answered && isCorrect) colors.doGreen.copy(alpha = 0.07f) else colors.surface)
                        .border(Shapes.hairline, border, Shapes.chip)
                        .clickable(enabled = !answered, role = Role.RadioButton) { onSelect(i) }
                        .heightIn(min = Space.touchTarget)
                        .padding(horizontal = Space.x3, vertical = Space.x3)
                        .alpha(fade)
                        .semantics {
                            if (answered) stateDescription = when {
                                isCorrect -> "Correct answer"
                                isChosen -> "Your answer, incorrect"
                                else -> ""
                            }
                        },
                    verticalAlignment = Alignment.Top,
                ) {
                    val letter = ('A' + i).toString()
                    Box(
                        Modifier.size(22.dp).clip(CircleShape).background(
                            when {
                                answered && isCorrect -> colors.doGreen
                                answered && isChosen -> colors.dontRed
                                else -> colors.surfaceSunken
                            },
                        ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (answered && isCorrect) Icon(NvIcons.Check, null, tint = colors.surface, modifier = Modifier.size(14.dp))
                        else Text(letter, style = Nv.type.numeral.copy(fontSize = Nv.type.label.fontSize),
                            color = if (answered && isChosen) colors.surface else colors.inkSecondary)
                    }
                    Spacer(Modifier.width(Space.x3))
                    Text(option.trim(), style = Nv.type.ui, color = colors.ink, modifier = Modifier.weight(1f))
                }
            }
        }
        AnimatedVisibility(answered, enter = fadeIn(enterSpec()) + expandVertically(enterSpec())) {
            Column {
                Spacer(Modifier.height(Space.x4))
                val right = selected == question.answer
                MarginNote(
                    if (right) "Yes" else "Not quite",
                    question.explain,
                    ruleColor = if (right) colors.doGreen else colors.dontRed,
                )
            }
        }
    }
}

// ---- ReflectionEditor ---------------------------------------------------------------------

@Composable
fun ReflectionEditor(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    minCharacters: Int,
    modifier: Modifier = Modifier,
) {
    val colors = Nv.colors
    val count = value.trim().length
    val met = count >= minCharacters
    Column(modifier.fillMaxWidth()) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            textStyle = Nv.type.body.copy(color = colors.ink),
            cursorBrush = SolidColor(Nv.tint),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 200.dp)
                .clip(Shapes.card)
                .background(colors.surface)
                .border(Shapes.hairline, colors.divider, Shapes.card)
                .padding(Space.x4)
                .semantics { contentDescription = "Reflection. $placeholder" },
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) Text(placeholder, style = Nv.type.body, color = colors.inkTertiary)
                    inner()
                }
            },
        )
        Spacer(Modifier.height(Space.x2))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "$count / $minCharacters",
                style = Nv.type.numeral,
                color = if (met) colors.brassText else colors.inkTertiary,
            )
            Spacer(Modifier.width(Space.x2))
            Text(
                if (met) "Enough to count. Keep going if there is more." else "characters. Saved as you write.",
                style = Nv.type.uiSmall,
                color = colors.inkTertiary,
            )
        }
    }
}

// ---- CategoryCard -------------------------------------------------------------------------

/**
 * A category as a printed colour block: roman numeral, title, tagline, and a tabular
 * mastery fraction above a thin accent line. No border, no shadow.
 */
@Composable
fun CategoryCard(
    numeral: String,
    title: String,
    tagline: String,
    mastered: Int,
    total: Int,
    written: Int,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = Nv.colors
    Column(
        modifier
            .fillMaxWidth()
            .clip(Shapes.large)
            .background(accent.copy(alpha = if (colors.isDark) 0.12f else 0.07f).compositeOver(colors.background))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.x6, vertical = Space.x6)
            .semantics(mergeDescendants = true) {},
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(numeral, style = Nv.type.titleSmall, color = accent)
            Spacer(Modifier.weight(1f))
            Text("$mastered / $total", style = Nv.type.numeral, color = colors.inkSecondary)
        }
        Spacer(Modifier.height(Space.x5))
        Text(title, style = Nv.type.title, color = colors.ink)
        Spacer(Modifier.height(Space.x2))
        Text(tagline, style = Nv.type.marginalia, color = colors.inkSecondary)
        Spacer(Modifier.height(Space.x5))
        ProgressLine(
            if (total == 0) 0f else mastered / total.toFloat(),
            color = accent,
            track = accent.copy(alpha = 0.18f),
        )
        Spacer(Modifier.height(Space.x2))
        Text(
            if (written < total) "$written of $total chapters written" else "$total chapters",
            style = Nv.type.uiSmall,
            color = colors.inkTertiary,
        )
    }
}
