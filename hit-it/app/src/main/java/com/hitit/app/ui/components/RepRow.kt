package com.hitit.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hitit.app.ui.theme.parseHexColor

/**
 * A single Rep row used on Today and Reps. The trailing button toggles today's hit with tactile
 * "juice": the whole row springs slightly when met, the toggle button compresses on press and the
 * checkmark bursts in with an overshoot, and the card gains a colored accent border when complete.
 */
@Composable
fun RepRow(
    emoji: String,
    name: String,
    colorHex: String,
    streak: Int,
    progressText: String,
    met: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
) {
    val accent = parseHexColor(colorHex, MaterialTheme.colorScheme.primary)

    // The whole row gives a subtle bouncy "settle" when it flips to met.
    val rowScale by animateFloatAsState(
        targetValue = if (met) 1.02f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "rowScale",
    )
    val cardColor by animateColorAsState(
        targetValue = if (met) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface,
        label = "cardColor",
    )
    val borderColor by animateColorAsState(
        targetValue = if (met) accent.copy(alpha = 0.6f) else Color.Transparent,
        label = "borderColor",
    )

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .graphicsLayer { scaleX = rowScale; scaleY = rowScale }
            .clip(RoundedCornerShape(16.dp))
            .border(1.5.dp, borderColor, RoundedCornerShape(16.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(16.dp),
        color = cardColor,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(accent.copy(alpha = 0.18f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = emoji, style = MaterialTheme.typography.titleMedium)
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (streak > 0) {
                        // Streak flame grows with the streak length.
                        val flameScale = (1f + (streak.coerceAtMost(30) / 30f) * 0.5f)
                        Text(
                            text = "🔥",
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.scale(flameScale),
                        )
                        Text(
                            text = " $streak  ·  ",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = accent,
                        )
                    }
                    Text(
                        text = progressText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ToggleHitButton(met = met, accent = accent, onToggle = onToggle)
        }
    }
}

@Composable
private fun ToggleHitButton(
    met: Boolean,
    accent: Color,
    onToggle: () -> Unit,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val haptics = LocalHapticFeedback.current

    // Compress under the finger, spring back on release.
    val pressScale by animateFloatAsState(
        targetValue = if (isPressed) 0.82f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "pressScale",
    )
    // Checkmark bursts in past 1.0 then settles.
    val checkScale by animateFloatAsState(
        targetValue = if (met) 1f else 0f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessMedium),
        label = "checkScale",
    )

    val border = if (met) null else BorderStroke(2.dp, MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(
        modifier = Modifier
            .size(40.dp)
            .graphicsLayer { scaleX = pressScale; scaleY = pressScale }
            .clip(CircleShape)
            .clickable(interactionSource = interactionSource, indication = null) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            },
        shape = CircleShape,
        color = if (met) accent else MaterialTheme.colorScheme.surfaceVariant,
        border = border,
    ) {
        if (checkScale > 0.01f) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Done",
                    tint = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.scale(checkScale),
                )
            }
        }
    }
}
