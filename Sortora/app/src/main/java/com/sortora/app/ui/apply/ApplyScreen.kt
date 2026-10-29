@file:OptIn(ExperimentalMaterial3Api::class)

package com.sortora.app.ui.apply

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.sortora.app.R
import com.sortora.app.domain.model.ApplyStatus
import com.sortora.app.domain.model.OperationMode
import com.sortora.app.domain.model.SessionItem
import com.sortora.app.domain.model.SessionStatus
import com.sortora.app.domain.model.SortSession
import com.sortora.app.domain.repository.BackgroundJobs
import com.sortora.app.domain.repository.JobProgress
import com.sortora.app.domain.repository.JobState
import com.sortora.app.domain.repository.SessionRepository
import com.sortora.app.domain.usecase.ApplySummary
import com.sortora.app.ui.navigation.ApplyRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class ApplyUiState(
    val session: SortSession? = null,
    val job: JobProgress = JobProgress(JobState.IDLE),
    val total: Int = 0,
    val done: Int = 0,
    val summary: ApplySummary = ApplySummary(0, 0, 0),
    val problems: List<SessionItem> = emptyList(),
) {
    val finished: Boolean
        get() = session?.status == SessionStatus.APPLIED || job.state == JobState.SUCCEEDED || job.state == JobState.FAILED
}

@HiltViewModel
class ApplyViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    sessions: SessionRepository,
    jobs: BackgroundJobs,
) : ViewModel() {
    private val sessionId = savedStateHandle.toRoute<ApplyRoute>().sessionId

    val state: StateFlow<ApplyUiState> = combine(
        sessions.observeSession(sessionId),
        sessions.observeItems(sessionId),
        jobs.observeApply(sessionId),
    ) { session, items, job ->
        val planned = items.filter { it.plannedName != null }
        ApplyUiState(
            session = session,
            job = job,
            total = planned.size,
            done = planned.count { it.applyStatus != null },
            summary = ApplySummary.of(planned.map { it.applyStatus }),
            problems = planned.filter { it.applyStatus == ApplyStatus.FAILED || it.applyStatus == ApplyStatus.COPIED_ORIGINAL_KEPT },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ApplyUiState())
}

@Composable
fun ApplyScreen(onDone: () -> Unit, onOpenHistory: () -> Unit, viewModel: ApplyViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val move = state.session?.mode == OperationMode.MOVE

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            when {
                                state.finished -> R.string.apply_title_done
                                move -> R.string.apply_title_moving
                                else -> R.string.apply_title_copying
                            },
                        ),
                    )
                },
            )
        },
        bottomBar = {
            if (state.finished) {
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(onClick = onOpenHistory, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.apply_view_log)) }
                    Button(onClick = onDone, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.action_done)) }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!state.finished) {
                item {
                    Column {
                        LinearProgressIndicator(
                            progress = { if (state.total == 0) 0f else state.done.toFloat() / state.total },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Spacer(Modifier.height(12.dp))
                        Text(stringResource(R.string.apply_progress, state.done, state.total), style = MaterialTheme.typography.titleMedium)
                        state.job.currentFile?.let {
                            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                        }
                        Spacer(Modifier.height(8.dp))
                        Text(stringResource(R.string.apply_background_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else {
                state.job.error?.let { error ->
                    item { ResultRow(Icons.Outlined.ErrorOutline, MaterialTheme.colorScheme.error, stringResource(R.string.apply_stopped, error)) }
                }
                item {
                    ResultRow(
                        Icons.Outlined.CheckCircle,
                        MaterialTheme.colorScheme.primary,
                        pluralStringResource(if (move) R.plurals.apply_moved else R.plurals.apply_copied, state.summary.succeeded, state.summary.succeeded),
                    )
                }
                if (state.summary.keptOriginal > 0) {
                    item {
                        ResultRow(
                            Icons.Outlined.Info,
                            MaterialTheme.colorScheme.tertiary,
                            pluralStringResource(R.plurals.apply_kept_original, state.summary.keptOriginal, state.summary.keptOriginal),
                        )
                    }
                }
                if (state.summary.failed > 0) {
                    item {
                        ResultRow(
                            Icons.Outlined.ErrorOutline,
                            MaterialTheme.colorScheme.error,
                            pluralStringResource(R.plurals.apply_failed, state.summary.failed, state.summary.failed),
                        )
                    }
                }
                item {
                    Text(stringResource(R.string.apply_log_saved), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            items(state.problems, key = { it.id }) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
                    Column(Modifier.padding(12.dp)) {
                        Text(item.file.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                        Text(item.applyMessage.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultRow(icon: ImageVector, tint: androidx.compose.ui.graphics.Color, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
        Spacer(Modifier.width(12.dp))
        Text(text, style = MaterialTheme.typography.titleMedium)
    }
}
