package com.hitit.app.ui.screens.reps

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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hitit.app.ui.components.BackHeader
import com.hitit.app.ui.components.EmptyState
import com.hitit.app.ui.components.Heatmap
import com.hitit.app.ui.components.HeatmapLegend
import com.hitit.app.ui.components.SectionLabel

@Composable
fun RepDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    viewModel: RepDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        BackHeader(
            title = if (state.exists) "${state.emoji}  ${state.name}" else "Rep",
            onBack = onBack,
            action = {
                if (state.exists) {
                    IconButton(onClick = { onEdit(state.id) }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit")
                    }
                }
            },
        )

        if (state.loaded && !state.exists) {
            EmptyState("This rep no longer exists.")
            return@Column
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(label = "Current streak", value = "🔥 ${state.currentStreak}", modifier = Modifier.weight(1f))
            StatCard(label = "Longest", value = "🏆 ${state.longestStreak}", modifier = Modifier.weight(1f))
        }

        Text(
            text = state.scheduleLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(
                    text = "Rest mode",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "Pause without breaking your streak",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(checked = state.isResting, onCheckedChange = { viewModel.toggleRestMode() })
        }

        SectionLabel("This year")
        Heatmap(
            columns = state.columns,
            modifier = Modifier.padding(horizontal = 20.dp),
        )
        HeatmapLegend(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))

        Spacer(Modifier.height(16.dp))
        OutlinedButton(
            onClick = { viewModel.setArchived(!state.isArchived) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Text(if (state.isArchived) "Unarchive" else "Archive")
        }
        Spacer(Modifier.height(32.dp))
    }
}

@Composable
private fun StatCard(label: String, value: String, modifier: Modifier = Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = value,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
