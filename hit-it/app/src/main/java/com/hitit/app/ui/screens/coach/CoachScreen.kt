package com.hitit.app.ui.screens.coach

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hitit.app.ui.components.BackHeader
import com.hitit.app.ui.components.EmptyState
import com.hitit.app.ui.components.SectionLabel
import com.hitit.domain.coach.CoachInsight
import com.hitit.domain.coach.InsightTone

@Composable
fun CoachScreen(
    onBack: () -> Unit,
    viewModel: CoachViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        BackHeader(title = "Your Coach", onBack = onBack)

        Text(
            text = if (state.llmActive) "Private on-device AI. It won't let you lie to yourself."
            else "Private. On-device. It won't let you lie to yourself.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        state.nudge?.let { nudge ->
            SectionLabel("Right now")
            InsightCard(nudge, emphasized = true)
        }

        state.llmSummary?.let { summary ->
            SectionLabel("Coach's take")
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
                    .clip(RoundedCornerShape(18.dp)),
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface,
            ) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(18.dp),
                )
            }
        }

        SectionLabel("This week's review")
        if (state.weekly.isEmpty() && !state.loading) {
            EmptyState("Not enough history yet.\nLog a few days and your review appears here.")
        } else {
            state.weekly.forEach { InsightCard(it) }
        }

        LlmSettings(viewModel)

        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun LlmSettings(viewModel: CoachViewModel) {
    var enabled by remember { mutableStateOf(viewModel.llmEnabled) }
    var path by remember { mutableStateOf(viewModel.modelPath) }

    SectionLabel("On-device AI (optional)")
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Generative coach",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "Rephrases the review with an on-device model you provide. " +
                            "Stays private — nothing leaves your phone. Falls back to the rule-based " +
                            "coach when off or unavailable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it; viewModel.setLlmEnabled(it) },
                )
            }
            if (enabled) {
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = path,
                    onValueChange = { path = it },
                    label = { Text("Model file path (.task / .litertlm)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = { viewModel.setModelPath(path) },
                    modifier = Modifier.padding(top = 8.dp),
                ) {
                    Text("Save path")
                }
            }
        }
    }
}

@Composable
private fun InsightCard(insight: CoachInsight, emphasized: Boolean = false) {
    val accent = toneColor(insight.tone)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(18.dp))
            .border(if (emphasized) 1.5.dp else 1.dp, accent.copy(alpha = if (emphasized) 0.8f else 0.4f), RoundedCornerShape(18.dp)),
        shape = RoundedCornerShape(18.dp),
        color = if (emphasized) accent.copy(alpha = 0.10f) else MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = insight.headline,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Black,
                color = accent,
            )
            Text(
                text = insight.detail,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

private fun toneColor(tone: InsightTone): Color = when (tone) {
    InsightTone.POSITIVE -> Color(0xFF4ADE80)
    InsightTone.NEUTRAL -> Color(0xFF38BDF8)
    InsightTone.WARNING -> Color(0xFFFFB300)
    InsightTone.CRITICAL -> Color(0xFFFF4D6D)
}
