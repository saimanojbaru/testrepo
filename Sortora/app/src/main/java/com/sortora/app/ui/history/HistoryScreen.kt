@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalCoroutinesApi::class)

package com.sortora.app.ui.history

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DeleteSweep
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.sortora.app.R
import com.sortora.app.domain.history.LogCsv
import com.sortora.app.domain.model.ApplyStatus
import com.sortora.app.domain.model.LogBatch
import com.sortora.app.domain.model.OperationLogEntry
import com.sortora.app.domain.model.OperationMode
import com.sortora.app.domain.repository.HistoryRepository
import com.sortora.app.domain.repository.TextFileWriter
import com.sortora.app.ui.components.EmptyState
import com.sortora.app.ui.components.Formatters
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class HistoryUiState(
    val loading: Boolean = true,
    val batches: List<LogBatch> = emptyList(),
    val expanded: Long? = null,
    val entries: List<OperationLogEntry> = emptyList(),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
    private val writer: TextFileWriter,
) : ViewModel() {
    private val expanded = MutableStateFlow<Long?>(null)
    private val messages = Channel<Int>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    val state: StateFlow<HistoryUiState> = combine(
        history.observeBatches(),
        expanded,
        expanded.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else history.observeEntries(id) },
    ) { batches, open, entries -> HistoryUiState(false, batches, open, entries) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun toggle(batchId: Long) {
        expanded.value = if (expanded.value == batchId) null else batchId
    }

    fun export(uri: Uri?) {
        uri ?: return
        viewModelScope.launch {
            val ok = runCatching { writer.write(uri.toString(), LogCsv.format(history.allEntries())) }.isSuccess
            messages.send(if (ok) R.string.history_exported else R.string.history_export_failed)
        }
    }

    fun clear() {
        viewModelScope.launch { history.clear() }
    }
}

@Composable
fun HistoryScreen(onBack: () -> Unit, viewModel: HistoryViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var confirmClear by remember { mutableStateOf(false) }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { viewModel.export(it) }

    LaunchedEffect(Unit) {
        viewModel.messageFlow.collect { snackbar.showSnackbar(resources.getString(it)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.history_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
                actions = {
                    if (state.batches.isNotEmpty()) {
                        IconButton(onClick = { export.launch("Sortora_history_${LocalDate.now()}.csv") }) {
                            Icon(Icons.Outlined.FileDownload, stringResource(R.string.history_export))
                        }
                        IconButton(onClick = { confirmClear = true }) { Icon(Icons.Outlined.DeleteSweep, stringResource(R.string.history_clear)) }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (!state.loading && state.batches.isEmpty()) {
            EmptyState(
                icon = Icons.Outlined.History,
                title = stringResource(R.string.history_empty_title),
                message = stringResource(R.string.history_empty_body),
                modifier = Modifier.padding(padding),
            )
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding())) {
            items(state.batches, key = { it.batchId }) { batch ->
                val open = state.expanded == batch.batchId
                Row(
                    Modifier.fillMaxWidth().clickable { viewModel.toggle(batch.batchId) }.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(Formatters.dateTime(batch.timestamp), style = MaterialTheme.typography.titleSmall)
                        Text(
                            pluralStringResource(
                                if (batch.mode == OperationMode.MOVE) R.plurals.history_batch_moved else R.plurals.history_batch_copied,
                                batch.succeeded, batch.succeeded,
                            ) + if (batch.failed > 0) " · " + pluralStringResource(R.plurals.apply_failed, batch.failed, batch.failed) else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Icon(if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore, contentDescription = null)
                }
                if (open) {
                    state.entries.forEach { entry -> EntryRow(entry) }
                }
                HorizontalDivider()
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text(stringResource(R.string.history_clear_confirm_title)) },
            text = { Text(stringResource(R.string.history_clear_confirm_body)) },
            confirmButton = { TextButton(onClick = { confirmClear = false; viewModel.clear() }) { Text(stringResource(R.string.history_clear)) } },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun EntryRow(entry: OperationLogEntry) {
    val (icon, tint) = when (entry.status) {
        ApplyStatus.SUCCESS -> Icons.Outlined.CheckCircle to MaterialTheme.colorScheme.primary
        ApplyStatus.COPIED_ORIGINAL_KEPT -> Icons.Outlined.Info to MaterialTheme.colorScheme.tertiary
        else -> Icons.Outlined.ErrorOutline to MaterialTheme.colorScheme.error
    }
    Row(Modifier.fillMaxWidth().padding(start = 24.dp, end = 16.dp, top = 6.dp, bottom = 6.dp), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(18.dp))
        Spacer(Modifier.width(12.dp))
        Column {
            Text(entry.originalName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text("→ ${entry.targetFolder}/${entry.newName}", style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
            entry.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = tint) }
        }
    }
}
