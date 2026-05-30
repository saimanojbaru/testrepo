package com.hitit.app.ui.screens.lockin

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hitit.app.lockin.FocusZones
import com.hitit.app.ui.components.BackHeader
import com.hitit.app.ui.components.SectionLabel
import com.hitit.domain.lockin.LockInClock

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LockInScreen(
    onBack: () -> Unit,
    viewModel: LockInViewModel = hiltViewModel(),
) {
    val setup by viewModel.setup.collectAsStateWithLifecycle()
    val run by viewModel.run.collectAsStateWithLifecycle()
    val totalFocus by viewModel.totalFocusMinutes.collectAsStateWithLifecycle()
    val sessions by viewModel.sessionCount.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        BackHeader(title = "Lock In", onBack = onBack)

        val finished = run.justFinishedMinutes
        when {
            finished != null -> FinishedView(minutes = finished, onDone = viewModel::acknowledgeFinish)
            run.active -> RunningView(
                remainingMillis = run.remainingMillis,
                totalMillis = run.totalMillis,
                zone = run.zone,
                label = run.label,
                paused = run.paused,
                onPause = viewModel::pause,
                onResume = viewModel::resume,
                onStop = viewModel::stop,
            )
            else -> SetupView(
                presets = viewModel.presets,
                selectedPreset = setup.presetMinutes,
                selectedZone = setup.zone,
                reps = setup.reps,
                selectedRepId = setup.repId,
                totalFocus = totalFocus,
                sessions = sessions,
                onPreset = viewModel::selectPreset,
                onZone = viewModel::selectZone,
                onToggleRep = viewModel::toggleRep,
                onStart = viewModel::start,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SetupView(
    presets: List<Int>,
    selectedPreset: Int,
    selectedZone: String,
    reps: List<RepChoice>,
    selectedRepId: Long?,
    totalFocus: Int,
    sessions: Int,
    onPreset: (Int) -> Unit,
    onZone: (String) -> Unit,
    onToggleRep: (Long) -> Unit,
    onStart: () -> Unit,
) {
    SectionLabel("Duration")
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        presets.forEach { minutes ->
            FilterChip(
                selected = minutes == selectedPreset,
                onClick = { onPreset(minutes) },
                label = { Text("$minutes min") },
            )
        }
    }

    SectionLabel("Zone")
    Row(
        modifier = Modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FocusZones.ALL.forEach { zone ->
            FilterChip(
                selected = zone.id == selectedZone,
                onClick = { onZone(zone.id) },
                label = { Text(zone.label) },
            )
        }
    }

    if (reps.isNotEmpty()) {
        SectionLabel("Focus on (optional)")
        Row(
            modifier = Modifier
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            reps.forEach { rep ->
                FilterChip(
                    selected = selectedRepId == rep.id,
                    onClick = { onToggleRep(rep.id) },
                    label = { Text("${rep.emoji} ${rep.name}") },
                )
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        StatCard("Focus minutes", "$totalFocus", Modifier.weight(1f))
        StatCard("Sessions", "$sessions", Modifier.weight(1f))
    }

    Spacer(Modifier.height(8.dp))
    Button(
        onClick = onStart,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
    ) {
        Text("Start Lock In")
    }
    Spacer(Modifier.height(32.dp))
}

@Composable
private fun RunningView(
    remainingMillis: Long,
    totalMillis: Long,
    zone: String,
    label: String,
    paused: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onStop: () -> Unit,
) {
    val zoneColor = FocusZones.color(zone)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = FocusZones.label(zone) + if (label.isNotBlank()) " · $label" else "",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(24.dp))
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(260.dp)) {
            CircularProgressIndicator(
                progress = { LockInClock.progress(remainingMillis, totalMillis) },
                modifier = Modifier.size(260.dp),
                color = zoneColor,
                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                strokeWidth = 12.dp,
            )
            Text(
                text = LockInClock.format(remainingMillis),
                style = MaterialTheme.typography.displayLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
        Spacer(Modifier.height(32.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            if (paused) {
                Button(onClick = onResume) { Text("Resume") }
            } else {
                Button(onClick = onPause) { Text("Pause") }
            }
            OutlinedButton(onClick = onStop) { Text("Stop") }
        }
    }
}

@Composable
private fun FinishedView(minutes: Int, onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(text = "🎯", style = MaterialTheme.typography.displayMedium)
        Spacer(Modifier.height(12.dp))
        Text(
            text = if (minutes > 0) "Locked in for $minutes min" else "Session ended",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground,
        )
        Text(
            text = "+$minutes ⚡ Momentum",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.secondary,
        )
        Spacer(Modifier.height(24.dp))
        Button(onClick = onDone) { Text("Done") }
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
