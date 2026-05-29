package com.hitit.app.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.hitit.app.ui.theme.parseHexColor

/**
 * A single Rep row used on Today and Reps. The trailing button toggles today's hit.
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
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .let { if (onClick != null) it.clickable(onClick = onClick) else it },
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
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
                Text(
                    text = buildString {
                        append("🔥 ")
                        append(streak)
                        append("  ·  ")
                        append(progressText)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            ToggleHitButton(met = met, accent = accent, onToggle = onToggle)
        }
    }
}

@Composable
private fun ToggleHitButton(
    met: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    onToggle: () -> Unit,
) {
    val border = if (met) null else BorderStroke(2.dp, MaterialTheme.colorScheme.onSurfaceVariant)
    Surface(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onToggle),
        shape = CircleShape,
        color = if (met) accent else MaterialTheme.colorScheme.surfaceVariant,
        border = border,
    ) {
        if (met) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = "Done",
                    tint = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
    }
}
