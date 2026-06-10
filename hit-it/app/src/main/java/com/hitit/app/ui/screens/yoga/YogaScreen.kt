package com.hitit.app.ui.screens.yoga

import androidx.compose.foundation.background
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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hitit.app.ui.components.frostedGlass
import com.hitit.app.ui.theme.AthleticLabelStyle
import com.hitit.app.ui.theme.AuroraCyan
import com.hitit.app.ui.theme.AuroraInk
import com.hitit.app.ui.theme.AuroraMuted
import com.hitit.app.ui.theme.AuroraViolet

/**
 * Pose Freak — the yoga library: your captured reference poses, practice history, and the door
 * into the Pose Studio (capture or practice). Shared across flavors; the studio itself is the
 * flavor-split piece (full = live camera grading, lite = upsell).
 */
@Composable
fun YogaScreen(
    onBack: () -> Unit,
    onCapturePose: () -> Unit,
    onPractice: (Long) -> Unit,
    viewModel: YogaViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 20.dp, top = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = AuroraInk)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "🧘 Pose Freak",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                    color = AuroraInk,
                )
                Text(
                    text = "teach it a pose, then let it judge you",
                    style = MaterialTheme.typography.labelSmall,
                    color = AuroraMuted,
                )
            }
        }

        // Capture a new reference pose.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 8.dp)
                .frostedGlass(cornerRadius = 22.dp, accent = AuroraCyan)
                .clickable(onClick = onCapturePose)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(AuroraCyan.copy(alpha = 0.16f)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, tint = AuroraCyan)
            }
            Column {
                Text(
                    text = "Teach a new pose",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = AuroraInk,
                )
                Text(
                    text = "Strike it on camera once — that photo becomes the judge.",
                    style = MaterialTheme.typography.bodySmall,
                    color = AuroraMuted,
                )
            }
        }

        if (state.poses.isEmpty() && !state.loading) {
            Text(
                text = "No reference poses yet. Teach one and the autonomous form-checker wakes up.",
                style = MaterialTheme.typography.bodySmall,
                color = AuroraMuted,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
        }

        state.poses.forEach { pose ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 5.dp)
                    .frostedGlass(cornerRadius = 20.dp)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "🧘", style = MaterialTheme.typography.titleLarge)
                Text(
                    text = pose.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = AuroraInk,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 10.dp),
                )
                // Practice = the autonomous judge session.
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(14.dp))
                        .background(AuroraViolet)
                        .clickable { onPractice(pose.id) }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = "Practice",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                        )
                    }
                }
                IconButton(onClick = { viewModel.deletePose(pose.id) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Delete", tint = AuroraMuted, modifier = Modifier.size(16.dp))
                }
            }
        }

        if (state.sessions.isNotEmpty()) {
            Text(
                text = "RECENT SESSIONS",
                style = AthleticLabelStyle,
                color = AuroraMuted,
                modifier = Modifier.padding(start = 24.dp, top = 16.dp, bottom = 6.dp),
            )
            state.sessions.forEach { s ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 24.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = "${s.poseName} · ${s.dateLabel}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = AuroraInk,
                    )
                    Text(
                        text = "${s.bestScore} pts · ${s.holdSeconds}s hold",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = AuroraCyan,
                    )
                }
            }
        }

        Spacer(Modifier.height(28.dp))
    }
}
