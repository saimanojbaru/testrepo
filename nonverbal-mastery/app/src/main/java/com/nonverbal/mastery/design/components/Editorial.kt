package com.nonverbal.mastery.design.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.layout.FirstBaseline
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.NewsreaderCapHeight
import com.nonverbal.mastery.design.tokens.NewsreaderDisplay
import com.nonverbal.mastery.design.tokens.Space

/** The reading measure (~62 characters of body text) for the current reading size. */
val LocalReadingMeasure = compositionLocalOf { 520.dp }

private const val MEASURE_SAMPLE =
    "and the man in the chair had not moved for an hour, which was its"

/** Provides the measure, computed from the actual reading face at the reader's chosen size. */
@Composable
fun ProvideReadingMeasure(content: @Composable () -> Unit) {
    val measurer = rememberTextMeasurer()
    val style = Nv.type.body
    val density = LocalDensity.current
    val measure = remember(style, density) {
        val sample = MEASURE_SAMPLE.take(Space.MEASURE_CHARS)
        val px = measurer.measure(sample, style).size.width
        with(density) { px.toDp() } + 2.dp
    }
    CompositionLocalProvider(LocalReadingMeasure provides measure, content = content)
}

/** Centres content in the reading column with the reading gutter. */
fun Modifier.readingColumn(measure: Dp, gutter: Dp = Space.readingGutter): Modifier =
    this.fillMaxWidth()
        .padding(horizontal = gutter)
        .wrapContentWidth(Alignment.CenterHorizontally)
        .widthIn(max = measure)
        .fillMaxWidth()

@Composable
fun ReadingColumn(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(modifier.readingColumn(LocalReadingMeasure.current), content = content)
}

/** Uppercase sans label with +0.08em tracking. The quiet signpost of every section. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = Nv.tint) {
    Text(
        text.uppercase(),
        modifier = modifier.semantics { heading() },
        style = Nv.type.label,
        color = color,
    )
}

/**
 * Prose with a tiny markup: blank lines separate paragraphs, *asterisks* set italics
 * (titles of works, terms being defined). Nothing else is interpreted.
 */
fun proseParagraphs(text: String): List<String> =
    text.split(Regex("\\n\\s*\\n")).map { it.trim().replace(Regex("\\s*\\n\\s*"), " ") }.filter { it.isNotEmpty() }

fun inlineItalics(text: String): AnnotatedString = buildAnnotatedString {
    var italic = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        if (c == '*') {
            italic = !italic
        } else if (italic) {
            pushStyle(SpanStyle(fontStyle = FontStyle.Italic)); append(c); pop()
        } else append(c)
        i++
    }
}

@Composable
fun Prose(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Nv.type.body,
    color: Color = Nv.colors.ink,
    paragraphGap: Dp = Space.paragraph,
) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(paragraphGap)) {
        proseParagraphs(text).forEach { p -> Text(inlineItalics(p), style = style, color = color) }
    }
}

/**
 * The app's typographic signature: an opening paragraph whose first letter drops through
 * [lines] lines of text, cap-height aligned at the top and baseline-aligned at the bottom.
 */
@Composable
fun DropCapParagraph(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = Nv.type.hook,
    color: Color = Nv.colors.ink,
    capColor: Color = Nv.tint,
    lines: Int = 3,
) {
    val clean = text.trim()
    if (clean.isEmpty()) return
    // Opening punctuation travels with the letter.
    val capLen = if (!clean[0].isLetterOrDigit() && clean.length > 1) 2 else 1
    val cap = clean.take(capLen)
    val rest = clean.drop(capLen)
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()

    BoxWithConstraints(modifier.clearAndSetSemantics { this.text = AnnotatedString(clean) }) {
        val lineHeightPx = with(density) { style.lineHeight.toPx() }
        val bodyCapPx = with(density) { style.fontSize.toPx() } * NewsreaderCapHeight
        val capFontPx = ((lines - 1) * lineHeightPx + bodyCapPx) / NewsreaderCapHeight
        val capStyle = TextStyle(
            fontFamily = NewsreaderDisplay,
            fontWeight = FontWeight.Medium,
            fontSize = with(density) { capFontPx.toSp() },
            lineHeight = with(density) { (capFontPx * 1.0f).toSp() },
            platformStyle = style.platformStyle,
            lineHeightStyle = style.lineHeightStyle,
        )
        val gapPx = with(density) { 6.dp.toPx() }
        val capWidth = remember(cap, capStyle) { measurer.measure(cap, capStyle).size.width } + gapPx
        val maxW = constraints.maxWidth
        val split = remember(rest, style, maxW, capWidth) {
            val layout = measurer.measure(
                rest.trimStart(), style,
                constraints = Constraints(maxWidth = (maxW - capWidth).toInt().coerceAtLeast(1)),
            )
            if (layout.lineCount <= lines) rest.trimStart().length
            else layout.getLineEnd(lines - 1, visibleEnd = false)
        }
        val part1 = rest.trimStart().take(split)
        val part2 = rest.trimStart().drop(split).trimStart()

        Layout(
            content = {
                Text(cap, style = capStyle, color = capColor)
                Text(part1, style = style, color = color)
                if (part2.isNotEmpty()) Text(part2, style = style, color = color)
            },
        ) { measurables, c ->
            val capP = measurables[0].measure(Constraints())
            val p1 = measurables[1].measure(Constraints(maxWidth = (c.maxWidth - capWidth).toInt().coerceAtLeast(1)))
            val p2 = measurables.getOrNull(2)?.measure(Constraints(maxWidth = c.maxWidth))
            val p1First = p1[FirstBaseline].takeIf { it != androidx.compose.ui.layout.AlignmentLine.Unspecified } ?: 0
            val targetBaseline = p1First + ((lines - 1) * lineHeightPx).toInt()
            val capBaseline = capP[FirstBaseline].takeIf { it != androidx.compose.ui.layout.AlignmentLine.Unspecified } ?: capP.height
            val capY = targetBaseline - capBaseline
            // Below the cap only its baseline matters; its descent area is empty for capitals.
            val capBottom = capY + capBaseline + (lineHeightPx * 0.32f).toInt()
            val textBlockH = p1.height.coerceAtLeast(capBottom)
            val height = textBlockH + (p2?.height ?: 0)
            layout(c.maxWidth, height) {
                capP.place(0, capY)
                p1.place(capWidth.toInt(), 0)
                p2?.place(0, textBlockH)
            }
        }
    }
}

/** A small brass diamond: the mark above every pull quote. */
@Composable
fun DiamondMark(modifier: Modifier = Modifier, color: Color = Nv.colors.brass, size: Dp = 8.dp) {
    Canvas(modifier.size(size)) {
        val w = this.size.width
        val p = Path().apply { moveTo(w / 2, 0f); lineTo(w, w / 2); lineTo(w / 2, w); lineTo(0f, w / 2); close() }
        drawPath(p, color)
    }
}

/** The level's thesis, lifted out of the column: centred italic display serif. */
@Composable
fun PullQuote(
    text: String,
    modifier: Modifier = Modifier,
    breathing: Dp = Space.aroundPullQuote,
) {
    Column(
        modifier.fillMaxWidth().padding(vertical = breathing),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        DiamondMark()
        Spacer(Modifier.height(Space.x5))
        Text(
            text.trim(),
            style = Nv.type.pullQuote,
            color = Nv.colors.ink,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(horizontal = Space.x2),
        )
    }
}

/** Centred 32dp hairline that separates major sections. */
@Composable
fun SectionBreak(modifier: Modifier = Modifier, vertical: Dp = Space.sectionBreak) {
    val color = Nv.colors.divider
    Box(modifier.fillMaxWidth().padding(vertical = vertical), contentAlignment = Alignment.Center) {
        Canvas(Modifier.width(32.dp).height(1.dp)) {
            drawLine(color, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.height)
        }
    }
}

/**
 * A marginal note set against a thin rule — used for each claim's disconfirming condition.
 * The label is in sans; the note itself in italic serif, as a teacher's hand in the margin.
 */
@Composable
fun MarginNote(
    label: String,
    text: String,
    modifier: Modifier = Modifier,
    ruleColor: Color = Nv.tint,
) {
    Column(
        modifier
            .fillMaxWidth()
            .drawBehind {
                val w = 2.dp.toPx()
                drawLine(ruleColor, Offset(w / 2, 0f), Offset(w / 2, size.height), strokeWidth = w)
            }
            .padding(start = Space.x4, top = Space.x1, bottom = Space.x1),
    ) {
        Text(label.uppercase(), style = Nv.type.label, color = Nv.colors.inkTertiary)
        Spacer(Modifier.height(Space.x2))
        Text(inlineItalics(text.trim()), style = Nv.type.marginalia, color = Nv.colors.inkSecondary)
    }
}
