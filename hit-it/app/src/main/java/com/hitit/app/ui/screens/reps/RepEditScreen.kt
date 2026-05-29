package com.hitit.app.ui.screens.reps

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hitit.app.ui.components.BackHeader
import com.hitit.app.ui.components.SectionLabel
import com.hitit.app.ui.theme.RepPalette
import com.hitit.app.ui.theme.parseHexColor
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

private val SCHEDULES = listOf(
    "DAILY" to "Daily",
    "WEEKDAYS" to "Weekdays",
    "CUSTOM" to "Custom",
    "WEEKLY" to "Weekly",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RepEditScreen(
    onDone: () -> Unit,
    viewModel: RepEditViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        BackHeader(title = if (state.isNew) "New Rep" else "Edit Rep", onBack = onDone)

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onName,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.emoji,
                onValueChange = viewModel::onEmoji,
                label = { Text("Emoji") },
                singleLine = true,
                modifier = Modifier.width(120.dp),
            )
        }

        SectionLabel("Color")
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            RepPalette.forEach { hex ->
                val color = parseHexColor(hex, MaterialTheme.colorScheme.primary)
                val selected = hex == state.colorHex
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(color)
                        .then(
                            if (selected) Modifier.border(3.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                            else Modifier,
                        )
                        .clickable { viewModel.onColor(hex) },
                )
            }
        }

        SectionLabel("Schedule")
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SCHEDULES.forEach { (key, label) ->
                FilterChip(
                    selected = state.scheduleType == key,
                    onClick = { viewModel.onSchedule(key) },
                    label = { Text(label) },
                )
            }
        }

        if (state.scheduleType == "CUSTOM") {
            SectionLabel("Days")
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                (1..7).forEach { iso ->
                    val name = DayOfWeek.of(iso).getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    FilterChip(
                        selected = iso in state.customDays,
                        onClick = { viewModel.toggleCustomDay(iso) },
                        label = { Text(name) },
                    )
                }
            }
        }

        if (state.scheduleType == "WEEKLY") {
            Stepper(
                label = "Times per week",
                value = state.weeklyTarget,
                min = 1,
                max = 21,
                onChange = viewModel::onWeeklyTarget,
            )
        }

        Stepper(
            label = "Hits per day",
            value = state.targetCount,
            min = 1,
            max = 20,
            onChange = viewModel::onTargetCount,
        )

        Stepper(
            label = "Rest days (skip protection)",
            value = state.restDaysAllowed,
            min = 0,
            max = 7,
            onChange = viewModel::onRestDays,
        )

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = viewModel::save,
            enabled = state.canSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Text(if (state.isNew) "Create Rep" else "Save")
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun Stepper(
    label: String,
    value: Int,
    min: Int,
    max: Int,
    onChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { onChange(value - 1) }, enabled = value > min) {
                Icon(Icons.Filled.Remove, contentDescription = "Decrease")
            }
            Text(
                text = "$value",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            IconButton(onClick = { onChange(value + 1) }, enabled = value < max) {
                Icon(Icons.Filled.Add, contentDescription = "Increase")
            }
        }
    }
}
