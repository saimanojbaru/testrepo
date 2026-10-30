package com.sortora.app.ui.design

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import com.sortora.app.ui.theme.LocalReduceMotion

/** Shorthand for the theme's extended values. */
object Sortora {
    val colors: SortoraColors
        @Composable @ReadOnlyComposable get() = LocalSortoraColors.current

    val reduceMotion: Boolean
        @Composable @ReadOnlyComposable get() = LocalReduceMotion.current
}

/** How far something floats above the canvas. Each level is two layers: a wide ambient blur and a tight contact shadow. */
enum class Depth { Flat, Low, Mid, High }

fun Modifier.softShadow(shape: Shape, depth: Depth, colors: SortoraColors): Modifier {
    if (depth == Depth.Flat) return this
    val strength = if (colors.dark) 3.2f else 1f
    val (ambientRadius, ambientY, ambientAlpha) = when (depth) {
        Depth.Low -> Triple(14.dp, 4.dp, 0.05f)
        Depth.Mid -> Triple(26.dp, 10.dp, 0.075f)
        else -> Triple(44.dp, 18.dp, 0.11f)
    }
    return this
        .dropShadow(shape, Shadow(radius = ambientRadius, color = colors.shadow, offset = DpOffset(0.dp, ambientY), alpha = (ambientAlpha * strength).coerceAtMost(0.5f)))
        .dropShadow(shape, Shadow(radius = 2.dp, color = colors.shadow, offset = DpOffset(0.dp, 1.dp), alpha = (0.035f * strength).coerceAtMost(0.3f)))
}

/** A coloured glow under accent elements, so primary buttons read as lit rather than heavy. */
fun Modifier.glow(shape: Shape, color: Color, radius: Dp = 18.dp, y: Dp = 6.dp, alpha: Float = 0.35f): Modifier =
    dropShadow(shape, Shadow(radius = radius, color = color, offset = DpOffset(0.dp, y), alpha = alpha))

fun Modifier.hairline(shape: Shape, color: Color): Modifier = border(Dp.Hairline, color, shape)

/**
 * The app's one tap behaviour: the element sinks slightly under the finger and springs back on
 * release, with a light haptic. No ripple: depth does the talking.
 */
fun Modifier.pressable(
    onClick: () -> Unit,
    enabled: Boolean = true,
    haptic: HapticFeedbackType? = HapticFeedbackType.ContextClick,
    pressedScale: Float = 0.97f,
    role: Role? = Role.Button,
    onClickLabel: String? = null,
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current
    val reduceMotion = LocalReduceMotion.current
    val scale by animateFloatAsState(
        targetValue = if (pressed && enabled && !reduceMotion) pressedScale else 1f,
        animationSpec = if (pressed) spring(dampingRatio = 0.9f, stiffness = 1500f) else spring(dampingRatio = 0.5f, stiffness = 520f),
        label = "press",
    )
    this
        .graphicsLayer {
            scaleX = scale
            scaleY = scale
        }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClickLabel = onClickLabel,
            role = role,
        ) {
            haptic?.let(haptics::performHapticFeedback)
            onClick()
        }
}
