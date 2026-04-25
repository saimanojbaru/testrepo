package com.spotifymashup.generator.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.spotifymashup.generator.data.HookDto
import com.spotifymashup.generator.ui.theme.BrandAccent
import com.spotifymashup.generator.ui.theme.BrandPrimary
import com.spotifymashup.generator.ui.theme.BrandSecondary

@Composable
fun HookCard(
    hook: HookDto,
    selected: Boolean,
    isAnotherSelected: Boolean,
    onSelect: () -> Unit,
) {
    val labelColor = when (hook.label.lowercase()) {
        "drop" -> BrandAccent
        "chorus" -> BrandPrimary
        "hook" -> BrandSecondary
        else -> MaterialTheme.colorScheme.tertiary
    }
    Card(
        modifier = Modifier.fillMaxWidth().alpha(if (isAnotherSelected && !selected) 0.4f else 1f),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .background(labelColor.copy(alpha = 0.20f), RoundedCornerShape(6.dp))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                ) {
                    Text(
                        text = hook.label.uppercase(),
                        style = MaterialTheme.typography.labelSmall,
                        color = labelColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Spacer(Modifier.width(10.dp))
                Text(
                    text = "${formatMs(hook.startMs)}–${formatMs(hook.endMs)}",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(999.dp))
                        .background(Brush.horizontalGradient(listOf(BrandSecondary, BrandAccent)))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = "${hook.score.toInt()}%",
                        style = MaterialTheme.typography.labelLarge,
                        color = androidx.compose.ui.graphics.Color.White,
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            MiniWaveform(seed = (hook.startMs * 31L + hook.endMs.toLong()), height = 40.dp)
            if (hook.reasons.isNotEmpty()) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = hook.reasons.joinToString("  ·  "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    modifier = Modifier.weight(1f).height(44.dp),
                    onClick = { /* preview hook segment when implemented */ },
                ) { Text("▶  Preview") }
                Button(
                    modifier = Modifier.weight(1f).height(44.dp),
                    onClick = onSelect,
                    colors = if (selected) ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                    ) else ButtonDefaults.buttonColors(),
                ) { Text(if (selected) "✓ Selected" else "Use this") }
            }
        }
    }
}

private fun formatMs(ms: Int): String {
    val s = ms / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}
