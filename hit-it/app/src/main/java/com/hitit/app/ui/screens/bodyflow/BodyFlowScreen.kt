package com.hitit.app.ui.screens.bodyflow

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.background
import androidx.health.connect.client.PermissionController
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hitit.app.ui.components.frostedGlass
import com.hitit.app.ui.theme.AthleticLabelStyle
import com.hitit.app.ui.theme.AuroraCyan
import com.hitit.app.ui.theme.AuroraInk
import com.hitit.app.ui.theme.AuroraMuted
import com.hitit.app.ui.theme.AuroraPink
import com.hitit.app.ui.theme.AuroraViolet
import com.hitit.domain.body.FoodEstimator

/**
 * BodyFlow v1 — the health pillar: today's Body Pulse, a Vibe Check tie-in, and smart food logging
 * with live offline kcal estimation ("200g chicken breast and rice" → 525 kcal as you type).
 */
@Composable
fun BodyFlowScreen(
    onCheckIn: () -> Unit,
    onOpenYoga: () -> Unit = {},
    viewModel: BodyFlowViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Header: pillar name + today's Body Pulse as the big number.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column {
                Text(
                    text = "BodyFlow",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Black,
                    color = AuroraInk,
                )
                Text(
                    text = if (state.started) state.pulseLabel else "Fuel + feelings, one place",
                    style = MaterialTheme.typography.bodyMedium,
                    color = AuroraMuted,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = "BODY PULSE", style = AthleticLabelStyle, color = AuroraMuted)
                Text(
                    text = "${state.bodyPulse}",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                    color = AuroraCyan,
                )
            }
        }

        VibeCheckCard(checkedIn = state.checkedInToday, mood = state.mood, onClick = onCheckIn)

        // Live body signals from Health Connect (read-only, on-device, opt-in).
        val hcLauncher = rememberLauncherForActivityResult(
            PermissionController.createRequestPermissionResultContract(),
        ) { viewModel.refreshSignals() }
        BodySignalsCard(
            signals = state.signals,
            onConnect = { hcLauncher.launch(viewModel.healthPermissions) },
        )

        FuelSection(
            todayKcal = state.todayKcal,
            entries = state.entries,
            onLog = { desc, kcal -> viewModel.logFood(desc, kcal) },
            onDelete = { viewModel.deleteEntry(it) },
        )

        // Pose Freak — the autonomous yoga form-checker.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .frostedGlass(cornerRadius = 22.dp, accent = AuroraViolet)
                .clickable(onClick = onOpenYoga)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(text = "🧘", style = MaterialTheme.typography.headlineSmall)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Pose Freak",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AuroraInk,
                )
                Text(
                    text = "Teach it a pose once — it grades your form live and calls out the joint that's off.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroraMuted,
                )
            }
        }

        // Honest roadmap teaser — real integrations land in the next drops, no fake UI.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 10.dp)
                .frostedGlass(cornerRadius = 22.dp)
                .padding(16.dp),
        ) {
            Text(text = "NEXT DROPS", style = AthleticLabelStyle, color = AuroraMuted)
            Spacer(Modifier.height(6.dp))
            Text(
                text = "📸 Photo food diary\n🔔 Opt-in spend auto-capture from notifications",
                style = MaterialTheme.typography.bodyMedium,
                color = AuroraInk,
                lineHeight = MaterialTheme.typography.bodyLarge.lineHeight,
            )
        }

        Spacer(Modifier.height(28.dp))
    }
}

/** Steps / sleep / heart-rate from Health Connect, with connect/unavailable fallbacks. */
@Composable
private fun BodySignalsCard(signals: BodySignalsUi, onConnect: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .frostedGlass(cornerRadius = 22.dp, accent = if (signals.hcState == HcState.CONNECTED) AuroraCyan else null)
            .padding(16.dp),
    ) {
        Text(text = "BODY SIGNALS", style = AthleticLabelStyle, color = AuroraMuted)
        Spacer(Modifier.height(8.dp))
        when (signals.hcState) {
            HcState.CONNECTED -> {
                Row(modifier = Modifier.fillMaxWidth()) {
                    SignalCell("👟", signals.steps?.toString() ?: "—", "steps", Modifier.weight(1f))
                    SignalCell(
                        "😴",
                        signals.sleepMinutes?.let { "${it / 60}h ${it % 60}m" } ?: "—",
                        "last night",
                        Modifier.weight(1f),
                    )
                    SignalCell("❤️", signals.bpm?.let { "$it" } ?: "—", "bpm", Modifier.weight(1f))
                }
                Text(
                    text = "Read live from Health Connect — never stored, never leaves the phone.",
                    style = MaterialTheme.typography.labelSmall,
                    color = AuroraMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            HcState.DISCONNECTED -> {
                Text(
                    text = "Pull steps, sleep & heart rate from Health Connect into your Body Pulse.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroraInk,
                )
                Spacer(Modifier.height(10.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(AuroraCyan)
                        .clickable(onClick = onConnect)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = "Connect Health Connect",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = androidx.compose.ui.graphics.Color(0xFF04201F),
                    )
                }
            }
            HcState.UPDATE_REQUIRED -> Text(
                text = "Health Connect needs an update from the Play Store before signals can flow.",
                style = MaterialTheme.typography.bodySmall,
                color = AuroraMuted,
            )
            HcState.UNAVAILABLE -> Text(
                text = "Health Connect isn't available on this device (needs Android 9+ with the Health Connect app).",
                style = MaterialTheme.typography.bodySmall,
                color = AuroraMuted,
            )
        }
    }
}

@Composable
private fun SignalCell(emoji: String, value: String, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = emoji, style = MaterialTheme.typography.titleMedium)
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = AuroraInk,
        )
        Text(text = label, style = MaterialTheme.typography.labelSmall, color = AuroraMuted)
    }
}

@Composable
private fun VibeCheckCard(checkedIn: Boolean, mood: Int?, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .frostedGlass(cornerRadius = 22.dp, accent = if (checkedIn) AuroraCyan else null)
            .clickable(onClick = onClick)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(
            text = if (checkedIn) moodEmoji(mood ?: 5) else "🫀",
            style = MaterialTheme.typography.headlineSmall,
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (checkedIn) "Vibe checked ✓" else "Vibe Check",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = AuroraInk,
            )
            Text(
                text = if (checkedIn) "Mood logged — tap to update"
                else "30 seconds: how's the energy today?",
                style = MaterialTheme.typography.bodySmall,
                color = AuroraMuted,
            )
        }
    }
}

@Composable
private fun FuelSection(
    todayKcal: Int,
    entries: List<FoodEntryUi>,
    onLog: (String, Int) -> Unit,
    onDelete: (Long) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var manualKcal by remember { mutableStateOf("") }
    val estimate = remember(input) { if (input.isBlank()) null else FoodEstimator.estimate(input) }
    val effectiveKcal = manualKcal.toIntOrNull() ?: estimate?.kcal ?: 0

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 22.dp, top = 14.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "Fuel today", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = AuroraInk)
        Text(
            text = "$todayKcal kcal",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Black,
            color = AuroraPink,
        )
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .frostedGlass(cornerRadius = 24.dp)
            .padding(14.dp),
    ) {
        OutlinedTextField(
            value = input,
            onValueChange = { input = it; manualKcal = "" },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("What did you eat? e.g. 200g chicken breast and rice") },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = AuroraCyan,
                unfocusedBorderColor = AuroraMuted.copy(alpha = 0.3f),
            ),
            shape = RoundedCornerShape(16.dp),
            maxLines = 2,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            // Live estimate chip — or a manual kcal field when the estimator can't read it.
            if (estimate != null && manualKcal.isBlank()) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AuroraCyan.copy(alpha = 0.14f))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Text(
                        text = "≈ ${estimate.kcal} kcal",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Black,
                        color = AuroraCyan,
                    )
                }
                if (estimate.unmatched.isNotEmpty()) {
                    Text(
                        text = "couldn't read: ${estimate.unmatched.first()}",
                        style = MaterialTheme.typography.labelSmall,
                        color = AuroraMuted,
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
            } else {
                OutlinedTextField(
                    value = manualKcal,
                    onValueChange = { v -> manualKcal = v.filter { it.isDigit() }.take(5) },
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("kcal") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = AuroraCyan,
                        unfocusedBorderColor = AuroraMuted.copy(alpha = 0.3f),
                    ),
                    shape = RoundedCornerShape(16.dp),
                    singleLine = true,
                )
            }
            IconButton(
                onClick = {
                    onLog(input, effectiveKcal)
                    input = ""
                    manualKcal = ""
                },
                enabled = input.isNotBlank() && effectiveKcal > 0,
                modifier = Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(if (input.isNotBlank() && effectiveKcal > 0) AuroraViolet else AuroraMuted.copy(alpha = 0.2f)),
            ) {
                Icon(Icons.AutoMirrored.Filled.Send, contentDescription = "Log food", tint = androidx.compose.ui.graphics.Color.White)
            }
        }
    }

    if (entries.isEmpty()) {
        Text(
            text = "Nothing logged yet — type a meal above, the kcal math is on us. ✨",
            style = MaterialTheme.typography.bodySmall,
            color = AuroraMuted,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
        )
    } else {
        entries.forEach { entry ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp)
                    .frostedGlass(cornerRadius = 18.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "🍽️", style = MaterialTheme.typography.titleMedium)
                Column(modifier = Modifier.weight(1f).padding(start = 10.dp)) {
                    Text(
                        text = entry.description,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = AuroraInk,
                    )
                    Text(
                        text = "${entry.kcal} kcal",
                        style = MaterialTheme.typography.labelSmall,
                        color = AuroraMuted,
                    )
                }
                IconButton(onClick = { onDelete(entry.id) }, modifier = Modifier.size(28.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Delete", tint = AuroraMuted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

private fun moodEmoji(mood: Int): String = when {
    mood <= 2 -> "😞"
    mood <= 4 -> "😕"
    mood <= 6 -> "😐"
    mood <= 8 -> "🙂"
    else -> "😄"
}
