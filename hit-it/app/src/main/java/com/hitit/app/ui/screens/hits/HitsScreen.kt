package com.hitit.app.ui.screens.hits

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
import com.hitit.app.ui.components.ScreenHeader
import com.hitit.app.ui.components.SectionLabel

@Composable
fun HitsScreen(
    onAddHit: () -> Unit,
    onOpenHit: (Long) -> Unit,
    viewModel: HitsViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        ScreenHeader(
            title = "Hits",
            subtitle = "${state.open.size} to do",
            action = {
                IconButton(onClick = onAddHit) {
                    Icon(Icons.Filled.Add, contentDescription = "Add Hit")
                }
            },
        )

        if (state.open.isEmpty() && state.done.isEmpty() && !state.loading) {
            EmptyState("No hits yet.\nTap + to capture a task.")
        }

        if (state.open.isNotEmpty()) {
            SectionLabel("To do")
            state.open.forEach { hit ->
                HitRow(
                    hit = hit,
                    onToggleDone = { viewModel.toggleDone(hit) },
                    onToggleMainTarget = { viewModel.toggleMainTarget(hit) },
                    onClick = { onOpenHit(hit.id) },
                )
            }
        }

        if (state.done.isNotEmpty()) {
            SectionLabel("Done")
            state.done.forEach { hit ->
                HitRow(
                    hit = hit,
                    onToggleDone = { viewModel.toggleDone(hit) },
                    onToggleMainTarget = { viewModel.toggleMainTarget(hit) },
                    onClick = { onOpenHit(hit.id) },
                )
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun HitRow(
    hit: HitUi,
    onToggleDone: () -> Unit,
    onToggleMainTarget: () -> Unit,
    onClick: () -> Unit,
) {
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
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Surface(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .clickable(onClick = onToggleDone),
                shape = CircleShape,
                color = if (hit.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
            ) {
                if (hit.done) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = "Completed",
                            tint = MaterialTheme.colorScheme.onPrimary,
                        )
                    }
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = hit.title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (hit.done) TextDecoration.LineThrough else null,
                )
                Text(
                    text = hit.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            IconButton(onClick = onToggleMainTarget) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = "Main Target",
                    tint = if (hit.isMainTarget) MaterialTheme.colorScheme.secondary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
