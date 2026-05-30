package com.hitit.app.ui.screens.today

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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hitit.app.ui.components.EmptyState
import com.hitit.app.ui.components.MomentumHeader
import com.hitit.app.ui.components.RepRow
import com.hitit.app.ui.components.ScreenHeader
import com.hitit.app.ui.components.SectionLabel

@Composable
fun TodayScreen(
    onAddRep: () -> Unit,
    onOpenRep: (Long) -> Unit,
    onLockIn: () -> Unit,
    onCheckIn: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = "Today",
            subtitle = state.dateLabel,
            action = {
                Row {
                    IconButton(onClick = onLockIn) {
                        Icon(Icons.Filled.Timer, contentDescription = "Lock In")
                    }
                    IconButton(onClick = onAddRep) {
                        Icon(Icons.Filled.Add, contentDescription = "Add Rep")
                    }
                }
            },
        )

        MomentumHeader(
            tier = state.tier,
            level = state.level,
            progress = state.progress,
            momentum = state.momentum,
        )

        CheckInCard(
            checkedIn = state.checkedIn,
            mood = state.checkInMood,
            onClick = onCheckIn,
        )

        state.mainTarget?.let { target ->
            SectionLabel("Main Target")
            MainTargetCard(target = target, onToggle = { viewModel.toggleMainTarget(target) })
        }

        SectionLabel("Today's reps")

        if (state.reps.isEmpty() && !state.loading) {
            EmptyState("No reps scheduled today.\nTap + to add your first one.")
        } else {
            state.reps.forEach { rep ->
                RepRow(
                    emoji = rep.emoji,
                    name = rep.name,
                    colorHex = rep.colorHex,
                    streak = rep.streak,
                    progressText = rep.progressText,
                    met = rep.met,
                    onToggle = { viewModel.toggle(rep) },
                    onClick = { onOpenRep(rep.id) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun CheckInCard(checkedIn: Boolean, mood: Int?, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp)
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = if (checkedIn) moodEmoji(mood ?: 5) else "📝",
                style = MaterialTheme.typography.titleLarge,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = if (checkedIn) "Checked in" else "Check-In",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = if (checkedIn) "Tap to update today's reflection"
                    else "How are you today? Tap to reflect.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
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

@Composable
private fun MainTargetCard(target: MainTargetUi, onToggle: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggle),
                shape = CircleShape,
                color = if (target.done) MaterialTheme.colorScheme.secondary
                else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                if (target.done) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Completed",
                            tint = MaterialTheme.colorScheme.onSecondary,
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = target.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (target.done) TextDecoration.LineThrough else null,
                )
                Text(
                    text = target.priority.lowercase().replaceFirstChar { it.uppercase() } + " priority",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Icon(
                imageVector = Icons.Filled.Star,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.secondary,
            )
        }
    }
}
