package com.hitit.app.ui.screens.grid

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hitit.app.ui.components.HeatmapLegend
import com.hitit.app.ui.components.HeatmapWithMonths
import com.hitit.app.ui.components.ScreenHeader
import java.time.format.DateTimeFormatter

@Composable
fun GridScreen(
    viewModel: GridViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var selected by remember { mutableStateOf<String?>(null) }
    val dateFormat = remember { DateTimeFormatter.ofPattern("EEE, MMM d") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(title = "The Grid", subtitle = "${state.year}")

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            StatCard(label = "Active days", value = "${state.activeDays}", modifier = Modifier.weight(1f))
            StatCard(label = "Total hits", value = "${state.totalHits}", modifier = Modifier.weight(1f))
        }

        Spacer(Modifier.height(8.dp))
        HeatmapWithMonths(
            columns = state.columns,
            modifier = Modifier.padding(horizontal = 20.dp),
            onCellClick = { cell ->
                selected = if (cell.inYear && !cell.isFuture) {
                    val reps = if (cell.intensity == 0) "no reps" else "${cell.intensity} rep(s)"
                    "${cell.date.format(dateFormat)} · $reps"
                } else {
                    null
                }
            },
        )
        HeatmapLegend(modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp))

        if (selected != null) {
            Text(
                text = selected!!,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        Spacer(Modifier.height(24.dp))
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
