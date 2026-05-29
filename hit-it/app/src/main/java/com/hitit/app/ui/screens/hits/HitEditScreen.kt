package com.hitit.app.ui.screens.hits

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hitit.app.ui.components.BackHeader
import com.hitit.app.ui.components.SectionLabel

private val PRIORITIES = listOf("HIGH" to "High", "MEDIUM" to "Medium", "LOW" to "Low")

private val DUE_LABELS = mapOf(
    DueOption.NONE to "None",
    DueOption.TODAY to "Today",
    DueOption.TOMORROW to "Tomorrow",
    DueOption.WEEK to "In a week",
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HitEditScreen(
    onDone: () -> Unit,
    viewModel: HitEditViewModel = hiltViewModel(),
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
        BackHeader(title = if (state.isNew) "New Hit" else "Edit Hit", onBack = onDone)

        Column(modifier = Modifier.padding(horizontal = 20.dp)) {
            OutlinedTextField(
                value = state.title,
                onValueChange = viewModel::onTitle,
                label = { Text("Title") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = state.notes,
                onValueChange = viewModel::onNotes,
                label = { Text("Notes") },
                modifier = Modifier.fillMaxWidth(),
            )
        }

        SectionLabel("Priority")
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            PRIORITIES.forEach { (key, label) ->
                FilterChip(
                    selected = state.priority == key,
                    onClick = { viewModel.onPriority(key) },
                    label = { Text(label) },
                )
            }
        }

        SectionLabel("Due")
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            DueOption.entries.forEach { option ->
                FilterChip(
                    selected = state.due == option,
                    onClick = { viewModel.onDue(option) },
                    label = { Text(DUE_LABELS[option] ?: option.name) },
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Today's Main Target",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Highlight this as your one focus for today",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.isMainTarget, onCheckedChange = viewModel::onMainTarget)
        }

        if (state.reps.isNotEmpty()) {
            SectionLabel("Link to a Rep (optional)")
            Row(
                modifier = Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.reps.forEach { rep ->
                    FilterChip(
                        selected = state.linkedRepId == rep.id,
                        onClick = { viewModel.onLinkRep(rep.id) },
                        label = { Text("${rep.emoji} ${rep.name}") },
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = viewModel::save,
            enabled = state.canSave,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Text(if (state.isNew) "Create Hit" else "Save")
        }
        Spacer(Modifier.height(32.dp))
    }
}
