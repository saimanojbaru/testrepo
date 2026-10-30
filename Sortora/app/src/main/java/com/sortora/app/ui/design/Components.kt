package com.sortora.app.ui.design

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import dev.chrisbanes.haze.hazeEffect

/** The haze source of the current screen; glass bars blur whatever it draws. */
val LocalHazeState = staticCompositionLocalOf<HazeState?> { null }

// ---------------------------------------------------------------------------------------------
// Surfaces
// ---------------------------------------------------------------------------------------------

@Composable
fun SoftCard(
    modifier: Modifier = Modifier,
    shape: Shape = Radius.Lg,
    color: Color = Sortora.colors.surface,
    depth: Depth = Depth.Low,
    border: Boolean = true,
    onClick: (() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(Space.lg),
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Sortora.colors
    Column(
        modifier
            .then(if (onClick != null) Modifier.pressable(onClick, pressedScale = 0.985f) else Modifier)
            .softShadow(shape, depth, c)
            .clip(shape)
            .background(color)
            .then(if (border) Modifier.hairline(shape, c.hairline) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

/**
 * Frosted glass: blurs the screen's haze source behind it (Android 12+), with a warm tint, a faint
 * grain and a hairline edge. Older phones get the same tint without blur.
 */
fun Modifier.glass(state: HazeState?, colors: SortoraColors, shape: Shape, opaque: Boolean = false): Modifier {
    val tint = if (opaque) colors.canvas.copy(alpha = 0.9f) else colors.glass
    val base = this.clip(shape)
    // Without real blur (Android 11 and older), a see-through bar would show text behind it.
    val surface = if (state != null && GlassBlurSupported) {
        base.hazeEffect(
            state,
            HazeStyle(
                backgroundColor = colors.canvas,
                tint = HazeTint(tint),
                blurRadius = 28.dp,
                noiseFactor = if (colors.dark) 0.05f else 0.03f,
                fallbackTint = HazeTint(colors.canvas.copy(alpha = 0.96f)),
            ),
        )
    } else {
        base.background(colors.canvas)
    }
    return surface
}

/**
 * Top bar floating over the content. The title fades in only after the page's own large title has
 * scrolled away, and the bottom hairline appears with it.
 */
@Composable
fun GlassTopBar(
    modifier: Modifier = Modifier,
    title: String? = null,
    showTitle: Boolean = true,
    scrolled: Boolean = false,
    navigation: (@Composable () -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    val c = Sortora.colors
    val titleAlpha by animateFloatAsState(if (showTitle) 1f else 0f, Motion.fade(), label = "title")
    val lineAlpha by animateFloatAsState(if (scrolled) 1f else 0f, Motion.fade(), label = "line")
    Box(
        modifier
            .fillMaxWidth()
            .glass(LocalHazeState.current.takeIf { scrolled }, c, androidx.compose.ui.graphics.RectangleShape, opaque = !scrolled)
            .drawBehind {
                drawLine(c.hairline.copy(alpha = c.hairline.alpha * lineAlpha), Offset(0f, size.height), Offset(size.width, size.height), 1f)
            }
            .statusBarsPadding(),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .padding(horizontal = Space.sm),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (navigation != null) navigation() else Spacer(Modifier.width(Space.xs))
            Box(Modifier.weight(1f).padding(horizontal = Space.xs)) {
                if (title != null) {
                    Text(
                        title,
                        style = MaterialTheme.typography.titleMedium,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.graphicsLayer {
                            alpha = titleAlpha
                            translationY = (1f - titleAlpha) * 6.dp.toPx()
                        },
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xxs), verticalAlignment = Alignment.CenterVertically, content = actions)
        }
    }
}

/** Bottom dock in glass, holding the screen's main action. */
@Composable
fun GlassDock(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val c = Sortora.colors
    Column(
        modifier
            .fillMaxWidth()
            .glass(LocalHazeState.current, c, androidx.compose.ui.graphics.RectangleShape)
            .drawBehind { drawLine(c.hairline, Offset(0f, 0f), Offset(size.width, 0f), 1f) }
            .navigationBarsPadding()
            .padding(horizontal = Space.gutter, vertical = Space.md),
        content = content,
    )
}

/** The large serif title at the top of a page (it scrolls with the content). */
@Composable
fun PageTitle(title: String, modifier: Modifier = Modifier, eyebrow: String? = null, subtitle: String? = null) {
    val c = Sortora.colors
    Column(modifier.padding(horizontal = Space.gutter).padding(top = Space.xs, bottom = Space.lg)) {
        if (eyebrow != null) {
            Eyebrow(eyebrow)
            Spacer(Modifier.height(Space.xs))
        }
        Text(title, style = MaterialTheme.typography.displaySmall, color = c.ink)
        if (subtitle != null) {
            Spacer(Modifier.height(Space.xs))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary)
        }
    }
}

/** Small uppercase label above groups. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier, color: Color = Sortora.colors.inkTertiary) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelSmall, color = color, modifier = modifier)
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Eyebrow(text, modifier.padding(start = Space.gutter + Space.xxs, end = Space.gutter, top = Space.section, bottom = Space.xs))
}

/** Inset grouped list, iOS-style: one card, rows split by inset hairlines. */
@Composable
fun SettingsGroup(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    SoftCard(modifier.padding(horizontal = Space.md), contentPadding = PaddingValues(0.dp), content = content)
}

@Composable
fun GroupDivider(inset: Dp = Space.md) {
    HorizontalDivider(Modifier.padding(start = inset), thickness = Dp.Hairline, color = Sortora.colors.hairline)
}

// ---------------------------------------------------------------------------------------------
// Buttons
// ---------------------------------------------------------------------------------------------

@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    leading: ImageVector? = null,
    danger: Boolean = false,
) {
    val c = Sortora.colors
    val tone = if (danger) c.danger else c.accent
    val bg by animateColorAsState(if (enabled) tone else c.sunken, Motion.fade(), label = "bg")
    val fg by animateColorAsState(
        when {
            !enabled -> c.inkTertiary
            danger -> if (c.dark) Color(0xFF2A0F0C) else Color.White
            else -> c.onAccent
        },
        Motion.fade(), label = "fg",
    )
    val glowAlpha by animateFloatAsState(if (enabled) (if (c.dark) 0.28f else 0.32f) else 0f, Motion.fade(), label = "glow")
    Box(
        modifier
            .heightIn(min = 54.dp)
            .pressable(onClick, enabled = enabled && !loading, haptic = HapticFeedbackType.Confirm, pressedScale = 0.97f)
            .glow(Radius.Pill, tone, alpha = glowAlpha)
            .clip(Radius.Pill)
            .background(Brush.verticalGradient(listOf(lerp(bg, Color.White, if (enabled) 0.07f else 0f), bg)))
            .padding(horizontal = Space.xl, vertical = Space.sm),
        contentAlignment = Alignment.Center,
    ) {
        AnimatedContent(loading, transitionSpec = { fadeIn(Motion.fade()) + scaleIn(initialScale = 0.9f) togetherWith fadeOut(Motion.fade(160)) }, label = "loading") { busy ->
            if (busy) {
                FluidSpinner(size = 22.dp, color = fg, track = fg.copy(alpha = 0.2f))
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (leading != null) {
                        Icon(leading, contentDescription = null, tint = fg, modifier = Modifier.size(19.dp))
                        Spacer(Modifier.width(Space.xs))
                    }
                    AnimatedContent(text, transitionSpec = { fadeIn(Motion.fade()) togetherWith fadeOut(Motion.fade(140)) using SizeTransform(clip = false) }, label = "label") { label ->
                        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    leading: ImageVector? = null,
    compact: Boolean = false,
) {
    val c = Sortora.colors
    Row(
        modifier
            .heightIn(min = if (compact) 40.dp else 50.dp)
            .pressable(onClick, enabled = enabled)
            .clip(Radius.Pill)
            .background(c.sunken)
            .hairline(Radius.Pill, c.hairline)
            .padding(horizontal = if (compact) Space.md else Space.lg),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val fg = if (enabled) c.ink else c.inkTertiary
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(Space.xs))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** Text-only action. */
@Composable
fun GhostButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Sortora.colors.accent,
    leading: ImageVector? = null,
) {
    Row(
        modifier
            .heightIn(min = 40.dp)
            .pressable(onClick, enabled = enabled, pressedScale = 0.95f)
            .clip(Radius.Pill)
            .padding(horizontal = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val fg = if (enabled) color else Sortora.colors.inkTertiary
        if (leading != null) {
            Icon(leading, contentDescription = null, tint = fg, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/** Round icon button for bars. */
@Composable
fun IconBubble(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    filled: Boolean = true,
    tint: Color = Sortora.colors.ink,
    enabled: Boolean = true,
) {
    val c = Sortora.colors
    Box(
        modifier
            .size(42.dp)
            .pressable(onClick, enabled = enabled, pressedScale = 0.9f)
            .clip(CircleShape)
            .then(if (filled) Modifier.background(c.sunken.copy(alpha = if (c.dark) 0.9f else 0.75f)).hairline(CircleShape, c.hairline) else Modifier)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) tint else c.inkTertiary, modifier = Modifier.size(20.dp))
    }
}

// ---------------------------------------------------------------------------------------------
// Selection
// ---------------------------------------------------------------------------------------------

/** Filter pill. Selected pills invert to ink so the accent stays reserved for actions. */
@Composable
fun Pill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    count: Int? = null,
    leading: (@Composable (Color) -> Unit)? = null,
) {
    val c = Sortora.colors
    val bg by animateColorAsState(if (selected) c.ink else c.surface, Motion.fade(220), label = "pill")
    val fg by animateColorAsState(if (selected) c.canvas else c.ink, Motion.fade(220), label = "pillText")
    Row(
        modifier
            .height(38.dp)
            .semantics { this.selected = selected }
            .pressable(onClick, haptic = HapticFeedbackType.SegmentTick, pressedScale = 0.95f, role = Role.Tab)
            .clip(Radius.Pill)
            .background(bg)
            .hairline(Radius.Pill, if (selected) Color.Transparent else c.hairline)
            .padding(start = if (leading != null) Space.sm else Space.md, end = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            leading(fg)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
        if (count != null) {
            Spacer(Modifier.width(6.dp))
            Text("$count", style = MaterialTheme.typography.labelMedium, color = fg.copy(alpha = 0.55f), maxLines = 1)
        }
    }
}

/**
 * Segmented control with a thumb that glides between options on a spring. Used for the
 * copy / move / rename choice and other small exclusive settings.
 */
@Composable
fun <T> SegmentedControl(
    options: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: @Composable (T) -> String,
    modifier: Modifier = Modifier,
    icon: ((T) -> ImageVector)? = null,
) {
    val c = Sortora.colors
    val haptics = LocalHapticFeedback.current
    val index = options.indexOf(selected).coerceAtLeast(0)
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .height(46.dp)
            .clip(Radius.Pill)
            .background(c.sunken)
            .hairline(Radius.Pill, c.hairline)
            .padding(4.dp),
    ) {
        val segment = maxWidth / options.size
        val offset by animateDpAsState(segment * index, Motion.gentle(), label = "thumb")
        Box(
            Modifier
                .offset { IntOffset(offset.roundToPx(), 0) }
                .width(segment)
                .fillMaxHeight()
                .softShadow(Radius.Pill, Depth.Low, c)
                .clip(Radius.Pill)
                .background(if (c.dark) c.raised else c.surface),
        )
        Row(Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, option ->
                val active = i == index
                val fg by animateColorAsState(if (active) c.ink else c.inkSecondary, Motion.fade(), label = "seg")
                Row(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clip(Radius.Pill)
                        .selectable(selected = active, role = Role.RadioButton) {
                            if (!active) {
                                haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                onSelect(option)
                            }
                        },
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (icon != null) {
                        Icon(icon(option), contentDescription = null, tint = fg, modifier = Modifier.size(17.dp))
                        Spacer(Modifier.width(6.dp))
                    }
                    Text(label(option), style = MaterialTheme.typography.labelMedium, color = fg, maxLines = 1)
                }
            }
        }
    }
}

/** Round check used to include or skip a file. The tick draws itself in. */
@Composable
fun CheckOrb(checked: Boolean, onToggle: () -> Unit, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val c = Sortora.colors
    val haptics = LocalHapticFeedback.current
    val fill by animateFloatAsState(if (checked) 1f else 0f, Motion.snappy(), label = "fill")
    val tick by animateFloatAsState(if (checked) 1f else 0f, Motion.gentle(), label = "tick")
    Box(
        modifier
            .size(44.dp)
            .toggleable(value = checked, role = Role.Checkbox) {
                haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onToggle()
            }
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.Canvas(Modifier.size(24.dp)) {
            val r = size.minDimension / 2
            drawCircle(c.inkTertiary.copy(alpha = 0.55f * (1f - fill)), r - 1.dp.toPx(), style = Stroke(1.5.dp.toPx()))
            drawCircle(c.accent, r * (0.4f + 0.6f * fill), alpha = fill)
            if (tick > 0.01f) {
                val path = Path().apply {
                    moveTo(size.width * 0.28f, size.height * 0.52f)
                    lineTo(size.width * 0.44f, size.height * 0.67f)
                    lineTo(size.width * 0.73f, size.height * 0.36f)
                }
                val measure = PathMeasure().apply { setPath(path, false) }
                val partial = Path()
                measure.getSegment(0f, measure.length * tick.coerceIn(0f, 1f), partial, true)
                drawPath(partial, c.onAccent, style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    val c = Sortora.colors
    val haptics = LocalHapticFeedback.current
    Row(
        Modifier
            .fillMaxWidth()
            .toggleable(value = checked, role = Role.Switch) {
                haptics.performHapticFeedback(if (it) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
                onChange(it)
            }
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.ink)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.inkSecondary)
        }
        Spacer(Modifier.width(Space.sm))
        Switch(
            checked = checked,
            onCheckedChange = null,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = c.accent,
                checkedBorderColor = Color.Transparent,
                uncheckedThumbColor = if (c.dark) c.inkSecondary else Color.White,
                uncheckedTrackColor = c.sunken,
                uncheckedBorderColor = c.hairline,
            ),
        )
    }
}

/** Tappable row inside a [SettingsGroup]. */
@Composable
fun ListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: ImageVector? = null,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    titleStyle: TextStyle = MaterialTheme.typography.bodyLarge,
) {
    val c = Sortora.colors
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.pressable(onClick, pressedScale = 0.99f) else Modifier)
            .padding(horizontal = Space.md, vertical = Space.sm)
            .heightIn(min = 32.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(Modifier.size(32.dp).clip(Radius.Xs).background(c.sunken), contentAlignment = Alignment.Center) {
                Icon(leading, contentDescription = null, tint = c.inkSecondary, modifier = Modifier.size(18.dp))
            }
            Spacer(Modifier.width(Space.sm))
        }
        Column(Modifier.weight(1f)) {
            Text(title, style = titleStyle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = c.inkSecondary, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) {
            Spacer(Modifier.width(Space.xs))
            trailing()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Feedback
// ---------------------------------------------------------------------------------------------

/** A number that rolls to its new value. */
@Composable
fun AnimatedCount(value: Int, style: TextStyle, color: Color, modifier: Modifier = Modifier, suffix: String = "") {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val up = targetState > initialState
            (slideInVertically(Motion.gentle()) { if (up) it / 2 else -it / 2 } + fadeIn(Motion.fade(200)))
                .togetherWith(slideOutVertically(Motion.gentle()) { if (up) -it / 2 else it / 2 } + fadeOut(Motion.fade(140)))
                .using(SizeTransform(clip = false))
        },
        modifier = modifier,
        label = "count",
    ) { n -> Text("$n$suffix", style = style, color = color, maxLines = 1) }
}

@Composable
fun SortoraSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data -> SortoraSnackbar(data) }
}

@Composable
private fun SortoraSnackbar(data: SnackbarData) {
    val c = Sortora.colors
    Row(
        Modifier
            .padding(horizontal = Space.gutter, vertical = Space.xs)
            .fillMaxWidth()
            .softShadow(Radius.Lg, Depth.Mid, c)
            .clip(Radius.Lg)
            .background(if (c.dark) c.raised else c.ink)
            .padding(start = Space.lg, end = Space.xs, top = Space.xs, bottom = Space.xs)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            data.visuals.message,
            style = MaterialTheme.typography.bodyMedium,
            color = if (c.dark) c.ink else c.canvas,
            modifier = Modifier.weight(1f).padding(vertical = Space.xs),
        )
        data.visuals.actionLabel?.let { label ->
            GhostButton(label, onClick = data::performAction, color = if (c.dark) c.accent else lerp(c.accent, Color.White, 0.35f))
        }
    }
}

/** Gentle notice card (warnings, info). */
@Composable
fun NoticeCard(
    icon: ImageVector,
    text: String,
    modifier: Modifier = Modifier,
    tone: Color = Sortora.colors.accent,
    action: String? = null,
    onAction: () -> Unit = {},
) {
    val c = Sortora.colors
    Row(
        modifier
            .fillMaxWidth()
            .clip(Radius.Md)
            .background(lerp(c.surface, tone, if (c.dark) 0.12f else 0.07f))
            .hairline(Radius.Md, tone.copy(alpha = 0.18f))
            .padding(start = Space.md, end = Space.xs, top = Space.xs, bottom = Space.xs)
            .heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tone, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(Space.sm))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = c.ink, modifier = Modifier.weight(1f).padding(vertical = Space.xs))
        if (action != null) GhostButton(action, onAction, color = tone)
    }
}

/** Fades content in once, rising a few dp: for elements that appear after data loads. */
@Composable
fun Reveal(visible: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(Motion.fade(360)) + slideInVertically(Motion.gentle()) { it / 6 } + scaleIn(Motion.gentle(), initialScale = 0.98f),
        exit = fadeOut(Motion.fade(200)) + scaleOut(targetScale = 0.98f),
    ) { content() }
}

/** Confirmation dialog in Sortora's style. Destructive confirms are tinted, never filled. */
@Composable
fun SortoraDialog(
    onDismiss: () -> Unit,
    title: String,
    text: String,
    confirm: String,
    onConfirm: () -> Unit,
    dismiss: String? = null,
    destructive: Boolean = false,
    icon: ImageVector? = null,
) {
    val c = Sortora.colors
    androidx.compose.material3.AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = c.surface,
        shape = Radius.Xl,
        tonalElevation = 0.dp,
        icon = icon?.let {
            {
                Box(
                    Modifier.size(48.dp).clip(CircleShape).background(if (destructive) c.dangerSoft else c.accentSoft),
                    contentAlignment = Alignment.Center,
                ) { Icon(it, contentDescription = null, tint = if (destructive) c.danger else c.accent, modifier = Modifier.size(22.dp)) }
            }
        },
        title = { Text(title, style = MaterialTheme.typography.headlineSmall, color = c.ink, textAlign = androidx.compose.ui.text.style.TextAlign.Center) },
        text = { Text(text, style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary) },
        confirmButton = { GhostButton(confirm, onConfirm, color = if (destructive) c.danger else c.accent) },
        dismissButton = dismiss?.let { { GhostButton(it, onDismiss, color = c.inkSecondary) } },
    )
}

/** Outlined field in Sortora's colours: quiet at rest, accent when focused. */
@Composable
fun SortoraTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supporting: String? = null,
    suffix: String? = null,
    leading: ImageVector? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    mono: Boolean = false,
) {
    val c = Sortora.colors
    androidx.compose.material3.OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it, color = c.inkTertiary) } },
        supportingText = supporting?.let { { Text(it, color = c.inkTertiary) } },
        suffix = suffix?.let { { Text(it, color = c.inkSecondary) } },
        leadingIcon = leading?.let { { Icon(it, contentDescription = null, tint = c.inkSecondary, modifier = Modifier.size(20.dp)) } },
        singleLine = singleLine,
        minLines = minLines,
        textStyle = if (mono) MonoStyle.copy(fontSize = androidx.compose.ui.unit.TextUnit(15f, androidx.compose.ui.unit.TextUnitType.Sp), color = c.ink)
        else MaterialTheme.typography.bodyLarge.copy(color = c.ink),
        shape = Radius.Md,
        colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = c.accent,
            unfocusedBorderColor = c.hairline,
            focusedContainerColor = c.surface,
            unfocusedContainerColor = c.sunken.copy(alpha = 0.55f),
            cursorColor = c.accent,
            focusedLabelColor = c.accent,
            unfocusedLabelColor = c.inkSecondary,
        ),
    )
}

/** Bottom sheet frame: soft top corners, a slim handle, Sortora surface. */
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
fun SortoraSheet(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    val c = Sortora.colors
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = androidx.compose.material3.rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surface,
        contentColor = c.ink,
        scrimColor = c.scrim,
        tonalElevation = 0.dp,
        shape = androidx.compose.foundation.shape.RoundedCornerShape(topStart = Radius.xxl, topEnd = Radius.xxl),
        dragHandle = {
            Box(Modifier.padding(top = Space.sm, bottom = Space.xs).size(width = 36.dp, height = 4.dp).clip(Radius.Pill).background(c.inkTertiary.copy(alpha = 0.4f)))
        },
        content = content,
    )
}
