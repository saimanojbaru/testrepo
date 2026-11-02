package com.nonverbal.mastery.features.level

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.nonverbal.mastery.data.models.Level
import com.nonverbal.mastery.data.repo.Codec
import com.nonverbal.mastery.data.repo.MASTERY_QUIZ_THRESHOLD
import com.nonverbal.mastery.design.components.ButtonKind
import com.nonverbal.mastery.design.components.NvButton
import com.nonverbal.mastery.design.components.QuizQuestionView
import com.nonverbal.mastery.design.components.SectionBreak
import com.nonverbal.mastery.design.components.SectionLabel
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.Space

@Composable
fun AssessmentItem(item: LevelItem, level: Level, state: LevelState, actions: LevelActions, column: Modifier) {
    val colors = Nv.colors
    val attempts = Codec.attempts(state.progress)
    val current = attempts.lastOrNull()
    when (item.kind) {
        Kind.AssessIntro -> Column(column) {
            SectionBreak(vertical = Space.sectionBreak + Space.x4)
            SectionLabel("Assess")
            Spacer(Modifier.height(Space.x4))
            Text(
                "${level.quiz.size} questions. Your first answer to each is the one that counts, and the note in the margin explains why. " +
                    "Mastery asks for $MASTERY_QUIZ_THRESHOLD%. You can retake as often as you like; every attempt is kept.",
                style = Nv.type.bodySmall, color = colors.inkSecondary,
            )
        }
        Kind.Question -> QuizQuestionView(
            question = level.quiz[item.i],
            number = item.i + 1,
            total = level.quiz.size,
            selected = current?.answers?.getOrNull(item.i),
            onSelect = { actions.answer(item.i, it) },
            modifier = column.padding(top = Space.x10),
        )
        Kind.QuizResult -> Column(column.padding(top = Space.x8)) {
            val done = current?.completedAt != null
            val score = current?.score
            if (done && score != null) {
                val right = level.quiz.indices.count { current.answers.getOrNull(it) == level.quiz[it].answer }
                Text(
                    "$score%  ·  $right of ${level.quiz.size}",
                    style = Nv.type.title.copy(fontFeatureSettings = "tnum"),
                    color = if (score >= MASTERY_QUIZ_THRESHOLD) colors.brassText else colors.ink,
                )
                Spacer(Modifier.height(Space.x2))
                Text(
                    if (score >= MASTERY_QUIZ_THRESHOLD) "That clears the bar. The explanations are still worth a second read."
                    else "Mastery asks for $MASTERY_QUIZ_THRESHOLD%. Reread the notes on the ones you missed, then retake when you're ready.",
                    style = Nv.type.bodySmall, color = colors.inkSecondary,
                )
                Spacer(Modifier.height(Space.x4))
                NvButton("Retake the quiz", actions.retake, kind = ButtonKind.Secondary)
                if (attempts.size > 1) {
                    Spacer(Modifier.height(Space.x2))
                    Text(
                        "Attempts so far: " + attempts.mapNotNull { it.score }.joinToString("  ·  ") { "$it%" },
                        style = Nv.type.numeral, color = colors.inkTertiary,
                    )
                }
            } else {
                val answered = current?.answers?.count { it != null } ?: 0
                Text("$answered of ${level.quiz.size} answered", style = Nv.type.numeral, color = colors.inkTertiary)
            }
        }
        else -> Unit
    }
}
