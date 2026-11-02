package com.nonverbal.mastery.features.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import com.nonverbal.mastery.data.models.Essay
import com.nonverbal.mastery.design.components.DiamondMark
import com.nonverbal.mastery.design.components.DropCapParagraph
import com.nonverbal.mastery.design.components.EmptyState
import com.nonverbal.mastery.design.components.LocalReadingMeasure
import com.nonverbal.mastery.design.components.Prose
import com.nonverbal.mastery.design.components.ProvideReadingMeasure
import com.nonverbal.mastery.design.components.SectionLabel
import com.nonverbal.mastery.design.components.TopBar
import com.nonverbal.mastery.design.components.inlineItalics
import com.nonverbal.mastery.design.components.readingColumn
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.Space

/** The app's conscience, set as an essay. Reachable from Settings and every chapter's menu. */
@Composable
fun EthicsScreen(essay: Essay?, onBack: () -> Unit) {
    val colors = Nv.colors
    Column(Modifier.fillMaxSize().background(colors.background)) {
        TopBar(title = essay?.title ?: "Ethics", onBack = onBack)
        if (essay == null) {
            EmptyState("The ethics statement is missing from this edition. That is a bug; please report it.")
            return
        }
        ProvideReadingMeasure {
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
                Column(Modifier.readingColumn(LocalReadingMeasure.current).padding(top = Space.x8, bottom = Space.x20)) {
                    SectionLabel("A statement of method")
                    Spacer(Modifier.height(Space.x3))
                    Text(essay.title, style = Nv.type.displayLarge, color = colors.ink, modifier = Modifier.semantics { heading() })
                    if (essay.subtitle.isNotBlank()) {
                        Spacer(Modifier.height(Space.x3))
                        Text(essay.subtitle, style = Nv.type.marginalia.copy(fontSize = Nv.type.body.fontSize * 1.08f), color = colors.inkSecondary)
                    }
                    Spacer(Modifier.height(Space.x10))
                    essay.sections.forEachIndexed { s, section ->
                        if (section.heading != null) {
                            Spacer(Modifier.height(Space.aboveHeading + Space.x4))
                            DiamondMark()
                            Spacer(Modifier.height(Space.x4))
                            Text(section.heading, style = Nv.type.title, color = colors.ink, modifier = Modifier.semantics { heading() })
                            Spacer(Modifier.height(Space.x4))
                        }
                        section.paragraphs.forEachIndexed { i, p ->
                            if (s == 0 && i == 0) DropCapParagraph(p, style = Nv.type.body)
                            else Text(inlineItalics(p.trim()), style = Nv.type.body, color = colors.ink)
                            Spacer(Modifier.height(Space.paragraph))
                        }
                    }
                    Spacer(Modifier.height(Space.x8))
                    Prose("Written for the readers of this book, and for the people they will look at.", style = Nv.type.marginalia, color = colors.inkTertiary)
                }
            }
        }
    }
}
