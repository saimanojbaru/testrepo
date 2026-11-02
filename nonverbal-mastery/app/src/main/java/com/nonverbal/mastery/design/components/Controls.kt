package com.nonverbal.mastery.design.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nonverbal.mastery.design.theme.Nv
import com.nonverbal.mastery.design.tokens.Shapes
import com.nonverbal.mastery.design.tokens.Space
import com.nonverbal.mastery.design.tokens.enterSpec

enum class ButtonKind { Primary, Secondary, Quiet }

@Composable
fun NvButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    kind: ButtonKind = ButtonKind.Primary,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    tint: Color = Nv.tint,
    floating: Boolean = false,
) {
    val colors = Nv.colors
    val bg = when (kind) {
        ButtonKind.Primary -> if (enabled) tint else colors.divider
        else -> Color.Transparent
    }
    val fg = when (kind) {
        ButtonKind.Primary -> if (enabled) colors.onAccent else colors.inkTertiary
        else -> if (enabled) tint else colors.inkTertiary
    }
    val shape = Shapes.chip
    Row(
        modifier
            .then(if (floating) Modifier.shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = 0.08f), spotColor = Color.Black.copy(alpha = 0.08f)) else Modifier)
            .defaultMinSize(minHeight = Space.touchTarget)
            .clip(shape)
            .background(bg)
            .then(if (kind == ButtonKind.Secondary) Modifier.border(Shapes.hairline, if (enabled) tint.copy(alpha = 0.45f) else colors.divider, shape) else Modifier)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = if (kind == ButtonKind.Quiet) Space.x2 else Space.x5, vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.x2))
        }
        Text(text, style = Nv.type.button, color = fg)
    }
}

/** A small rounded tag: durations, counts, tiers. */
@Composable
fun Pill(text: String, modifier: Modifier = Modifier, color: Color = Nv.colors.inkSecondary, background: Color = Nv.colors.surfaceSunken) {
    Box(
        modifier.clip(Shapes.pill).background(background).padding(horizontal = 10.dp, vertical = 4.dp),
        contentAlignment = Alignment.Center,
    ) { Text(text, style = Nv.type.numeral.copy(fontSize = Nv.type.uiSmall.fontSize * 0.92f), color = color) }
}

@Composable
fun IconAction(icon: ImageVector, description: String, onClick: () -> Unit, modifier: Modifier = Modifier, tint: Color = Nv.colors.ink) {
    Box(
        modifier
            .size(Space.touchTarget)
            .clip(CircleShape)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics { contentDescription = description },
        contentAlignment = Alignment.Center,
    ) { Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp)) }
}

/** A flat top bar. The title can fade in as the screen scrolls (titleAlpha). */
@Composable
fun TopBar(
    modifier: Modifier = Modifier,
    title: String = "",
    titleAlpha: Float = 1f,
    onBack: (() -> Unit)? = null,
    background: Color = Nv.colors.background,
    divider: Boolean = false,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val dividerColor = Nv.colors.divider
    Row(
        modifier
            .fillMaxWidth()
            .background(background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .height(56.dp)
            .drawBehind {
                if (divider) drawLine(dividerColor, Offset(0f, size.height - 0.5f), Offset(size.width, size.height - 0.5f), 1f)
            }
            .padding(horizontal = Space.x1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) IconAction(NvIcons.Back, "Back", onBack) else Spacer(Modifier.width(Space.x4))
        Text(
            title,
            modifier = Modifier.weight(1f).alpha(titleAlpha).padding(horizontal = Space.x1).semantics { heading() },
            style = Nv.type.titleSmall,
            color = Nv.colors.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        actions()
    }
}

data class TabSpec(val label: String, val badge: String? = null, val done: Boolean = false)

/**
 * The level's segmented control: Read | Drills | Assess | Reflect | Further.
 * Tabs carry inline mastery state ("3/6", a check) so the requirement is legible in the flow.
 */
@Composable
fun SegmentedTabs(
    tabs: List<TabSpec>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Nv.tint,
) {
    val colors = Nv.colors
    Row(
        modifier
            .fillMaxWidth()
            .background(colors.background)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = Space.listGutter, vertical = Space.x2),
        horizontalArrangement = Arrangement.spacedBy(Space.x1),
    ) {
        tabs.forEachIndexed { i, tab ->
            val isSel = i == selected
            val bg by animateColorAsState(if (isSel) tint.copy(alpha = 0.12f) else Color.Transparent, enterSpec(), label = "tabBg")
            val fg = if (isSel) tint else colors.inkSecondary
            Row(
                Modifier
                    .clip(Shapes.pill)
                    .background(bg)
                    .selectable(selected = isSel, role = Role.Tab, onClick = { onSelect(i) })
                    .heightIn(min = 40.dp)
                    .padding(horizontal = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(tab.label, style = Nv.type.button.copy(fontSize = Nv.type.uiSmall.fontSize * 1.08f), color = fg)
                if (tab.done) {
                    Spacer(Modifier.width(6.dp))
                    Icon(NvIcons.Check, contentDescription = "complete", tint = colors.brass, modifier = Modifier.size(14.dp))
                } else if (tab.badge != null) {
                    Spacer(Modifier.width(6.dp))
                    Text(tab.badge, style = Nv.type.numeral.copy(fontSize = Nv.type.label.fontSize), color = colors.inkTertiary)
                }
            }
        }
    }
}

/** A round check used by drills and checklists. Brass when done. */
@Composable
fun CheckCircle(checked: Boolean, modifier: Modifier = Modifier, size: Dp = 24.dp) {
    val colors = Nv.colors
    val progress by animateFloatAsState(if (checked) 1f else 0f, enterSpec(), label = "check")
    Canvas(modifier.size(size)) {
        val r = this.size.minDimension / 2f
        val stroke = 1.5.dp.toPx()
        drawCircle(colors.brass.copy(alpha = progress), radius = r)
        drawCircle(if (checked) colors.brass else colors.inkFaint, radius = r - stroke / 2, style = androidx.compose.ui.graphics.drawscope.Stroke(stroke))
        if (progress > 0f) {
            val p1 = Offset(r * 0.55f, r * 1.02f)
            val p2 = Offset(r * 0.88f, r * 1.34f)
            val p3 = Offset(r * 1.48f, r * 0.68f)
            val t = progress
            val first = (t * 2f).coerceAtMost(1f)
            drawLine(colors.onAccent, p1, Offset(p1.x + (p2.x - p1.x) * first, p1.y + (p2.y - p1.y) * first), stroke * 1.2f, StrokeCap.Round)
            if (t > 0.5f) {
                val second = (t - 0.5f) * 2f
                drawLine(colors.onAccent, p2, Offset(p2.x + (p3.x - p2.x) * second, p2.y + (p3.y - p2.y) * second), stroke * 1.2f, StrokeCap.Round)
            }
        }
    }
}

/** A full-width toggle row, ≥48dp, with the check on the leading edge. */
@Composable
fun CheckRow(
    checked: Boolean,
    onToggle: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable RowScope.() -> Unit,
) {
    Row(
        modifier
            .fillMaxWidth()
            .clip(Shapes.chip)
            .toggleable(value = checked, role = Role.Checkbox, onValueChange = onToggle)
            .heightIn(min = Space.touchTarget)
            .padding(vertical = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CheckCircle(checked)
        Spacer(Modifier.width(Space.x3))
        content()
    }
}

/** A radio row for settings choices. */
@Composable
fun ChoiceRow(label: String, selected: Boolean, onSelect: () -> Unit, modifier: Modifier = Modifier, detail: String? = null) {
    val colors = Nv.colors
    Row(
        modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onSelect)
            .heightIn(min = Space.touchTarget)
            .padding(vertical = Space.x2),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = Nv.type.ui, color = colors.ink)
            if (detail != null) Text(detail, style = Nv.type.uiSmall, color = colors.inkTertiary)
        }
        Canvas(Modifier.size(20.dp)) {
            val r = size.minDimension / 2
            drawCircle(if (selected) colors.accent else colors.inkFaint, r - 1.dp.toPx(), style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()))
            if (selected) drawCircle(colors.accent, r * 0.5f)
        }
    }
}

/** A tappable list row with a trailing chevron. */
@Composable
fun NavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    detail: String? = null,
    leading: ImageVector? = null,
    trailing: String? = null,
    titleColor: Color = Nv.colors.ink,
) {
    val colors = Nv.colors
    Row(
        modifier
            .fillMaxWidth()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(),
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = 56.dp)
            .padding(vertical = Space.x3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = colors.inkSecondary, modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(Space.x4))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = Nv.type.ui, color = titleColor)
            if (detail != null) {
                Spacer(Modifier.height(2.dp))
                Text(detail, style = Nv.type.uiSmall, color = colors.inkTertiary)
            }
        }
        if (trailing != null) {
            Text(trailing, style = Nv.type.numeral, color = colors.inkTertiary)
            Spacer(Modifier.width(Space.x2))
        }
        Icon(NvIcons.Forward, contentDescription = null, tint = colors.inkFaint, modifier = Modifier.size(18.dp))
    }
}

@Composable
fun Hairline(modifier: Modifier = Modifier, color: Color = Nv.colors.divider) {
    Box(modifier.fillMaxWidth().height(1.dp).background(color))
}

/** A thin horizontal progress line (2dp) used for category progress and reading progress. */
@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier, color: Color = Nv.colors.brass, track: Color = Nv.colors.divider, thickness: Dp = 2.dp) {
    Canvas(modifier.fillMaxWidth().height(thickness)) {
        drawLine(track, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), size.height)
        val p = progress.coerceIn(0f, 1f)
        if (p > 0f) drawLine(color, Offset(0f, size.height / 2), Offset(size.width * p, size.height / 2), size.height)
    }
}

/** Padding helper for list screens. */
val ListPadding = PaddingValues(horizontal = Space.listGutter)

/** Applies a fade + rise used for staggered entrances; static when motion is reduced. */
fun Modifier.entrance(progress: Float): Modifier = this.graphicsLayer {
    alpha = progress
    translationY = (1f - progress) * 12.dp.toPx()
}
