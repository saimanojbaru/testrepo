package com.hitit.app.ui.screens.reps

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hitit.app.ui.components.EmptyState
import com.hitit.app.ui.components.RepRow
import com.hitit.app.ui.components.ScreenHeader

@Composable
fun RepsScreen(
    onAddRep: () -> Unit,
    onOpenRep: (Long) -> Unit,
    viewModel: RepsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = "Reps",
            subtitle = "${state.reps.size} active",
            action = {
                IconButton(onClick = onAddRep) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Rep")
                }
            },
        )

        if (state.reps.isEmpty() && !state.loading) {
            EmptyState("No reps yet.\nTap + to create one.")
        } else {
            state.reps.forEach { rep ->
                RepRow(
                    emoji = rep.emoji,
                    name = rep.name,
                    colorHex = rep.colorHex,
                    streak = rep.streak,
                    progressText = rep.subtitle,
                    met = rep.met,
                    onToggle = { viewModel.toggle(rep) },
                    onClick = { onOpenRep(rep.id) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}
