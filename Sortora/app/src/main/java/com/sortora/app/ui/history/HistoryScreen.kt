@file:OptIn(ExperimentalCoroutinesApi::class)

package com.sortora.app.ui.history

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.FileDownload
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
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
import com.sortora.app.domain.model.UndoStatus
import com.sortora.app.domain.repository.BackgroundJobs
import com.sortora.app.domain.repository.HistoryRepository
import com.sortora.app.domain.repository.JobProgress
import com.sortora.app.domain.repository.JobState
import com.sortora.app.domain.repository.MediaSourceRepository
import com.sortora.app.domain.repository.TextFileWriter
import com.sortora.app.ui.components.CenteredEmptyState
import com.sortora.app.ui.design.Art
import com.sortora.app.ui.design.FluidBar
import com.sortora.app.ui.design.GhostButton
import com.sortora.app.ui.design.GlassTopBar
import com.sortora.app.ui.design.IconBubble
import com.sortora.app.ui.design.Motion
import com.sortora.app.ui.design.PageTitle
import com.sortora.app.ui.design.ScreenScaffold
import com.sortora.app.ui.design.SoftCard
import com.sortora.app.ui.design.Sortora
import com.sortora.app.ui.design.SortoraDialog
import com.sortora.app.ui.design.Space
import com.sortora.app.ui.design.isScrolled
import com.sortora.app.ui.design.pressable
import com.sortora.app.ui.design.scrolledPast
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
    /** Batch being undone right now, with its job progress. */
    val undoing: Long? = null,
    val undoProgress: JobProgress = JobProgress(JobState.IDLE),
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val history: HistoryRepository,
    private val writer: TextFileWriter,
    private val jobs: BackgroundJobs,
    private val sources: MediaSourceRepository,
) : ViewModel() {
    private val expanded = MutableStateFlow<Long?>(null)
    private val undoing = MutableStateFlow<Long?>(null)
    private val messages = Channel<Int>(Channel.BUFFERED)
    val messageFlow = messages.receiveAsFlow()

    val state: StateFlow<HistoryUiState> = combine(
        history.observeBatches(),
        expanded,
        expanded.flatMapLatest { id -> if (id == null) flowOf(emptyList()) else history.observeEntries(id) },
        undoing,
        undoing.flatMapLatest { id -> if (id == null) flowOf(JobProgress(JobState.IDLE)) else jobs.observeUndo(id) },
    ) { batches, open, entries, undoingId, progress ->
        HistoryUiState(false, batches, open, entries, undoingId, progress)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HistoryUiState())

    fun toggle(batchId: Long) {
        expanded.value = if (expanded.value == batchId) null else batchId
    }

    /** [restoreTree]: folder for moved files whose original folder is unknown (picked by the user). */
    fun undo(batch: LogBatch, restoreTree: Uri?) {
        val tree = restoreTree?.toString()
        if (tree != null) sources.persistTreeAccess(tree)
        undoing.value = batch.batchId
        expanded.value = batch.batchId
        jobs.startUndo(batch.batchId, tree)
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
    var confirmUndo by remember { mutableStateOf<LogBatch?>(null) }
    var pendingRestore by remember { mutableStateOf<LogBatch?>(null) }
    val list = rememberLazyListState()
    val scrolled by list.isScrolled()
    val pastTitle by list.scrolledPast(1)
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { viewModel.export(it) }
    val pickRestoreFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        pendingRestore?.let { batch -> if (uri != null) viewModel.undo(batch, uri) }
        pendingRestore = null
    }

    LaunchedEffect(Unit) {
        viewModel.messageFlow.collect { snackbar.showSnackbar(resources.getString(it)) }
    }
    LaunchedEffect(state.undoProgress.state) {
        when (state.undoProgress.state) {
            JobState.SUCCEEDED -> snackbar.showSnackbar(resources.getString(R.string.undo_done))
            JobState.FAILED -> snackbar.showSnackbar(resources.getString(R.string.undo_failed))
            else -> Unit
        }
    }

    ScreenScaffold(
        snackbar = snackbar,
        topBar = {
            GlassTopBar(
                title = stringResource(R.string.history_title),
                showTitle = pastTitle,
                scrolled = scrolled,
                navigation = { IconBubble(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), onBack) },
                actions = {
                    if (state.batches.isNotEmpty()) {
                        IconBubble(Icons.Rounded.FileDownload, stringResource(R.string.history_export), { export.launch("Sortora_history_${LocalDate.now()}.csv") })
                        IconBubble(Icons.Rounded.DeleteSweep, stringResource(R.string.history_clear), { confirmClear = true })
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loading && state.batches.isEmpty()) {
            CenteredEmptyState(
                art = Art.History,
                title = stringResource(R.string.history_empty_title),
                message = stringResource(R.string.history_empty_body),
                modifier = Modifier.padding(padding),
            )
            return@ScreenScaffold
        }
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            item(key = "title") { PageTitle(stringResource(R.string.history_title), eyebrow = stringResource(R.string.history_eyebrow)) }
            items(state.batches, key = { it.batchId }) { batch ->
                val open = state.expanded == batch.batchId
                val running = state.undoing == batch.batchId &&
                    (state.undoProgress.state == JobState.RUNNING || state.undoProgress.state == JobState.QUEUED)
                BatchCard(
                    batch = batch,
                    open = open,
                    running = running,
                    progress = state.undoProgress,
                    entries = if (open) state.entries else emptyList(),
                    onToggle = { viewModel.toggle(batch.batchId) },
                    onUndo = { confirmUndo = batch },
                    modifier = Modifier.animateItem(),
                )
            }
        }
    }

    confirmUndo?.let { batch ->
        SortoraDialog(
            onDismiss = { confirmUndo = null },
            icon = Icons.AutoMirrored.Rounded.Undo,
            title = pluralStringResource(R.plurals.undo_confirm_title, batch.undoable, batch.undoable),
            text = stringResource(
                when (batch.mode) {
                    OperationMode.COPY -> R.string.undo_confirm_copy
                    OperationMode.MOVE -> if (batch.needsRestoreFolder) R.string.undo_confirm_move_pick else R.string.undo_confirm_move
                    OperationMode.RENAME -> R.string.undo_confirm_rename
                },
            ),
            confirm = stringResource(if (batch.needsRestoreFolder) R.string.undo_choose_folder else R.string.action_undo),
            onConfirm = {
                confirmUndo = null
                if (batch.needsRestoreFolder) {
                    pendingRestore = batch
                    pickRestoreFolder.launch(null)
                } else {
                    viewModel.undo(batch, null)
                }
            },
            dismiss = stringResource(R.string.action_cancel),
        )
    }

    if (confirmClear) {
        SortoraDialog(
            onDismiss = { confirmClear = false },
            icon = Icons.Rounded.DeleteSweep,
            title = stringResource(R.string.history_clear_confirm_title),
            text = stringResource(R.string.history_clear_confirm_body),
            confirm = stringResource(R.string.history_clear),
            onConfirm = { confirmClear = false; viewModel.clear() },
            dismiss = stringResource(R.string.action_cancel),
            destructive = true,
        )
    }
}

@Composable
private fun BatchCard(
    batch: LogBatch,
    open: Boolean,
    running: Boolean,
    progress: JobProgress,
    entries: List<OperationLogEntry>,
    onToggle: () -> Unit,
    onUndo: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sortora.colors
    val chevron by animateFloatAsState(if (open) 180f else 0f, Motion.gentle(), label = "chevron")
    SoftCard(modifier.padding(horizontal = Space.md).fillMaxWidth(), contentPadding = PaddingValues(0.dp)) {
        Row(
            Modifier
                .fillMaxWidth()
                .pressable(onToggle, pressedScale = 0.99f)
                .padding(start = Space.md, end = Space.xs, top = Space.md, bottom = Space.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(if (batch.undone > 0) c.sunken else c.accentSoft), contentAlignment = Alignment.Center) {
                Icon(
                    when (batch.mode) {
                        OperationMode.COPY -> Icons.Rounded.ContentCopy
                        OperationMode.MOVE -> Icons.AutoMirrored.Rounded.DriveFileMove
                        OperationMode.RENAME -> Icons.Rounded.DriveFileRenameOutline
                    },
                    contentDescription = null,
                    tint = if (batch.undone > 0) c.inkTertiary else c.accent,
                    modifier = Modifier.size(19.dp),
                )
            }
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                Text(Formatters.dateTime(batch.timestamp), style = MaterialTheme.typography.titleSmall, color = c.ink)
                val parts = buildList {
                    add(
                        pluralStringResource(
                            when (batch.mode) {
                                OperationMode.COPY -> R.plurals.history_batch_copied
                                OperationMode.MOVE -> R.plurals.history_batch_moved
                                OperationMode.RENAME -> R.plurals.history_batch_renamed
                            },
                            batch.succeeded, batch.succeeded,
                        ),
                    )
                    if (batch.failed > 0) add(pluralStringResource(R.plurals.apply_failed, batch.failed, batch.failed))
                    if (batch.undone > 0) add(pluralStringResource(R.plurals.history_undone, batch.undone, batch.undone))
                }
                Text(parts.joinToString(" · "), style = MaterialTheme.typography.bodySmall, color = c.inkSecondary)
            }
            if (batch.undoable > 0 && !running) {
                GhostButton(stringResource(R.string.action_undo), onUndo, leading = Icons.AutoMirrored.Rounded.Undo)
            }
            Icon(
                Icons.Rounded.ExpandMore,
                contentDescription = null,
                tint = c.inkTertiary,
                modifier = Modifier.padding(horizontal = Space.xs).size(22.dp).graphicsLayer { rotationZ = chevron },
            )
        }
        AnimatedVisibility(running, enter = fadeIn(Motion.fade()) + expandVertically(Motion.gentle()), exit = fadeOut(Motion.fade()) + shrinkVertically(Motion.gentle())) {
            Column(Modifier.padding(horizontal = Space.md).padding(bottom = Space.md)) {
                FluidBar(null)
                progress.currentFile?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = c.inkTertiary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis, modifier = Modifier.padding(top = Space.xs))
                }
            }
        }
        AnimatedVisibility(open && entries.isNotEmpty(), enter = fadeIn(Motion.fade(320)) + expandVertically(Motion.gentle()), exit = fadeOut(Motion.fade(160)) + shrinkVertically(Motion.gentle())) {
            Column(Modifier.padding(bottom = Space.xs)) {
                HorizontalDivider(Modifier.padding(horizontal = Space.md), thickness = Dp.Hairline, color = c.hairline)
                entries.forEach { entry -> EntryRow(entry) }
            }
        }
    }
}

@Composable
private fun EntryRow(entry: OperationLogEntry) {
    val c = Sortora.colors
    val undone = entry.undoStatus == UndoStatus.UNDONE
    val (icon, tint) = when {
        undone -> Icons.AutoMirrored.Rounded.Undo to c.inkTertiary
        entry.status == ApplyStatus.SUCCESS -> Icons.Rounded.CheckCircle to c.success
        entry.status == ApplyStatus.COPIED_ORIGINAL_KEPT -> Icons.Rounded.Info to c.warning
        else -> Icons.Rounded.ErrorOutline to c.danger
    }
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.md, vertical = Space.xs), verticalAlignment = Alignment.Top) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.padding(top = 2.dp).size(16.dp))
        Spacer(Modifier.width(Space.sm))
        Column {
            Text(entry.originalName, style = MaterialTheme.typography.bodySmall, color = c.inkTertiary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            val target = if (entry.mode == OperationMode.RENAME || entry.targetFolder.isEmpty()) entry.newName else "${entry.targetFolder}/${entry.newName}"
            Text(target, style = MaterialTheme.typography.bodyMedium, color = if (undone) c.inkTertiary else c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            when {
                undone -> Text(stringResource(R.string.history_entry_undone), style = MaterialTheme.typography.bodySmall, color = tint)
                entry.undoStatus == UndoStatus.FAILED -> Text(
                    stringResource(R.string.history_entry_undo_failed, entry.undoMessage.orEmpty()),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.danger,
                )
                else -> entry.message?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = tint) }
            }
        }
    }
}
