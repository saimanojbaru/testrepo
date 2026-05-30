package com.hitit.app.ui.screens.checkin

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hitit.app.ui.components.BackHeader
import com.hitit.app.ui.components.SectionLabel
import kotlin.math.roundToInt

@Composable
fun CheckInScreen(
    onBack: () -> Unit,
    viewModel: CheckInViewModel = hiltViewModel(),
) {
    val form by viewModel.form.collectAsStateWithLifecycle()

    LaunchedEffect(form.saved) {
        if (form.saved) onBack()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        BackHeader(title = "Check-In", onBack = onBack)

        Text(
            text = viewModel.dateLabel,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        SectionLabel("Morning")
        OutlinedTextField(
            value = form.morning,
            onValueChange = viewModel::onMorning,
            placeholder = { Text("What will make today great?") },
            minLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )

        SectionLabel("Evening")
        OutlinedTextField(
            value = form.evening,
            onValueChange = viewModel::onEvening,
            placeholder = { Text("How did today actually go?") },
            minLines = 2,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        )

        SectionLabel("Mood: ${form.mood} ${moodEmoji(form.mood)}")
        Slider(
            value = form.mood.toFloat(),
            onValueChange = { viewModel.onMood(it.roundToInt()) },
            valueRange = 1f..10f,
            steps = 8,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        SectionLabel("Energy: ${form.energy}")
        Slider(
            value = form.energy.toFloat(),
            onValueChange = { viewModel.onEnergy(it.roundToInt()) },
            valueRange = 1f..10f,
            steps = 8,
            modifier = Modifier.padding(horizontal = 20.dp),
        )

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = viewModel::save,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp),
        ) {
            Text("Save Check-In")
        }
        Spacer(Modifier.height(32.dp))
    }
}

private fun moodEmoji(mood: Int): String = when {
    mood <= 2 -> "😞"
    mood <= 4 -> "😕"
    mood <= 6 -> "😐"
    mood <= 8 -> "🙂"
    else -> "😄"
}
