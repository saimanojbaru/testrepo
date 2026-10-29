@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sortora.app.ui.analysis

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.sortora.app.R
import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.SortSession
import com.sortora.app.domain.repository.BackgroundJobs
import com.sortora.app.domain.repository.JobProgress
import com.sortora.app.domain.repository.JobState
import com.sortora.app.domain.repository.SessionRepository
import com.sortora.app.ui.components.EmptyState
import com.sortora.app.ui.components.icon
import com.sortora.app.ui.components.labelRes
import com.sortora.app.ui.navigation.AnalysisRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class AnalysisUiState(
    val session: SortSession? = null,
    val job: JobProgress = JobProgress(JobState.IDLE),
    val categories: Map<ContentCategory, Int> = emptyMap(),
) {
    val processed: Int get() = (session?.analyzedItems ?: 0) + (session?.failedItems ?: 0)
    val total: Int get() = session?.totalItems ?: 0
    val progress: Float get() = if (total == 0) 0f else processed.toFloat() / total
    val finished: Boolean get() = total > 0 && processed >= total
    val stopped: Boolean get() = job.state == JobState.CANCELLED || job.state == JobState.FAILED
}

@HiltViewModel
class AnalysisViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    sessions: SessionRepository,
    private val jobs: BackgroundJobs,
) : ViewModel() {
    val sessionId: Long = savedStateHandle.toRoute<AnalysisRoute>().sessionId

    val state: StateFlow<AnalysisUiState> = combine(
        sessions.observeSession(sessionId),
        jobs.observeAnalysis(sessionId),
        sessions.observeCategoryCounts(sessionId),
    ) { session, job, categories -> AnalysisUiState(session, job, categories) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AnalysisUiState())

    fun cancel() = jobs.cancelAnalysis(sessionId)

    fun retry() = jobs.startAnalysis(sessionId)
}

@Composable
fun AnalysisScreen(onBack: () -> Unit, onReview: (Long) -> Unit, viewModel: AnalysisViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var confirmCancel by remember { mutableStateOf(false) }

    // Jump straight to the preview once everything is analysed.
    LaunchedEffect(state.finished) {
        if (state.finished) onReview(viewModel.sessionId)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.analysis_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            if (state.stopped && !state.finished) {
                EmptyState(
                    icon = Icons.Outlined.ErrorOutline,
                    title = stringResource(if (state.job.state == JobState.FAILED) R.string.analysis_failed_title else R.string.analysis_stopped_title),
                    message = pluralStringResource(R.plurals.analysis_stopped_body, state.session?.analyzedItems ?: 0, state.session?.analyzedItems ?: 0),
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if ((state.session?.analyzedItems ?: 0) > 0) {
                            Button(onClick = { onReview(viewModel.sessionId) }) { Text(stringResource(R.string.analysis_review_partial)) }
                        }
                        OutlinedButton(onClick = viewModel::retry) { Text(stringResource(R.string.analysis_continue)) }
                    }
                }
                return@Column
            }

            Spacer(Modifier.height(16.dp))
            Box(contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    progress = { state.progress },
                    modifier = Modifier.size(160.dp),
                    strokeWidth = 10.dp,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                )
                Text("${(state.progress * 100).toInt()}%", style = MaterialTheme.typography.headlineMedium)
            }
            Spacer(Modifier.height(24.dp))
            Text(
                stringResource(R.string.analysis_progress, state.processed, state.total),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                state.job.currentFile ?: stringResource(
                    if (state.job.state == JobState.QUEUED) R.string.analysis_waiting else R.string.analysis_loading_models,
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.analysis_background_hint),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (state.categories.isNotEmpty()) {
                Spacer(Modifier.height(24.dp))
                Text(stringResource(R.string.analysis_found_so_far), style = MaterialTheme.typography.labelLarge, modifier = Modifier.fillMaxWidth())
                Spacer(Modifier.height(8.dp))
                FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.categories.entries.sortedByDescending { it.value }.forEach { (category, count) ->
                        AssistChip(
                            onClick = {},
                            label = { Text("${stringResource(category.labelRes())} · $count") },
                            leadingIcon = { Icon(category.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
                        )
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            TextButton(onClick = { confirmCancel = true }) { Text(stringResource(R.string.analysis_stop)) }
        }
    }

    if (confirmCancel) {
        AlertDialog(
            onDismissRequest = { confirmCancel = false },
            title = { Text(stringResource(R.string.analysis_stop_confirm_title)) },
            text = { Text(stringResource(R.string.analysis_stop_confirm_body)) },
            confirmButton = {
                TextButton(onClick = { confirmCancel = false; viewModel.cancel() }) { Text(stringResource(R.string.analysis_stop)) }
            },
            dismissButton = {
                TextButton(onClick = { confirmCancel = false }) { Text(stringResource(R.string.action_keep_going)) }
            },
        )
    }
}
