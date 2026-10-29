@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sortora.app.ui.preview

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortora.app.R
import com.sortora.app.domain.model.DateSource
import com.sortora.app.domain.model.OperationMode
import com.sortora.app.domain.model.SessionItem
import com.sortora.app.ui.components.EmptyState
import com.sortora.app.ui.components.Formatters
import com.sortora.app.ui.components.MediaThumbnail
import com.sortora.app.ui.components.labelRes

@Composable
fun PreviewScreen(
    onBack: () -> Unit,
    onApplied: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: PreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var confirmMove by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    val pickDestination = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { viewModel.onDestinationPicked(it) }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is PreviewEvent.Applied -> onApplied(event.sessionId)
                is PreviewEvent.Message -> snackbar.showSnackbar(resources.getString(event.text))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(stringResource(R.string.preview_title))
                        if (!state.loading) {
                            Text(
                                pluralStringResource(R.plurals.preview_summary, state.includedCount, state.includedCount, state.folders.size),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) }
                },
                actions = {
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more)) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.preview_select_all)) }, onClick = { menuOpen = false; viewModel.setAllIncluded(true) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.preview_select_none)) }, onClick = { menuOpen = false; viewModel.setAllIncluded(false) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.preview_naming_settings)) }, onClick = { menuOpen = false; onOpenSettings() })
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            ApplyBar(
                state = state,
                onModeChange = viewModel::setMode,
                onChooseDestination = { pickDestination.launch(null) },
                onApply = { if (state.mode == OperationMode.MOVE) confirmMove = true else viewModel.apply() },
            )
        },
    ) { padding ->
        when {
            state.loading -> Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator()
            }
            state.totalAnalyzed == 0 && state.failed.isEmpty() -> EmptyState(
                icon = Icons.Outlined.ImageSearch,
                title = stringResource(R.string.preview_empty_title),
                message = stringResource(R.string.preview_empty_body),
                modifier = Modifier.padding(padding),
            )
            else -> LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 8.dp),
            ) {
                if (state.stillAnalyzing > 0) {
                    item {
                        InfoCard(Icons.Outlined.HourglassTop, pluralStringResource(R.plurals.preview_still_analyzing, state.stillAnalyzing, state.stillAnalyzing))
                    }
                }
                item {
                    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = state.filter == null,
                                onClick = { viewModel.setFilter(null) },
                                label = { Text(stringResource(R.string.preview_filter_all, state.includedCount)) },
                            )
                        }
                        items(state.folders, key = { it.folder }) { group ->
                            FilterChip(
                                selected = state.filter == group.folder,
                                onClick = { viewModel.setFilter(group.folder) },
                                label = { Text("${group.folder} (${group.count})") },
                                leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp)) },
                            )
                        }
                    }
                }
                items(state.rows, key = { it.item.id }) { row ->
                    PreviewItem(row, onToggle = { viewModel.toggleIncluded(row) }, onClick = { viewModel.startEditing(row) })
                    HorizontalDivider(Modifier.padding(start = 88.dp))
                }
                if (state.failed.isNotEmpty() && state.filter == null) {
                    item {
                        Text(
                            pluralStringResource(R.plurals.preview_failed_header, state.failed.size, state.failed.size),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
                        )
                    }
                    items(state.failed, key = { "failed-${it.id}" }) { item -> FailedItem(item) }
                }
            }
        }
    }

    state.editing?.let { row ->
        EditSheet(
            row = row,
            suggestions = state.folderSuggestions,
            onDismiss = viewModel::stopEditing,
            onSave = { name, folder -> viewModel.saveEdit(row, name, folder) },
            onReset = { viewModel.resetEdit(row) },
        )
    }

    if (confirmMove) {
        AlertDialog(
            onDismissRequest = { confirmMove = false },
            icon = { Icon(Icons.Outlined.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
            title = { Text(pluralStringResource(R.plurals.preview_move_confirm_title, state.includedCount, state.includedCount)) },
            text = { Text(stringResource(R.string.preview_move_confirm_body)) },
            confirmButton = {
                TextButton(
                    onClick = { confirmMove = false; viewModel.apply() },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                ) { Text(stringResource(R.string.preview_move_confirm_action)) }
            },
            dismissButton = { TextButton(onClick = { confirmMove = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun PreviewItem(row: PreviewRow, onToggle: () -> Unit, onClick: () -> Unit) {
    val analysis = row.item.analysis
    val lowConfidence = analysis != null && (analysis.limited || analysis.confidence < 0.5f)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaThumbnail(row.item.file.uri, row.item.file.kind)
        Spacer(Modifier.width(16.dp))
        Column(Modifier.weight(1f)) {
            Text(
                row.plan.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (row.item.included) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline,
            )
            Text(
                stringResource(R.string.preview_was, row.item.file.displayName),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 2.dp)) {
                Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(4.dp))
                Text(
                    row.plan.folder,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (lowConfidence) {
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary)
                    Spacer(Modifier.width(2.dp))
                    Text(stringResource(R.string.preview_low_confidence), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
        }
        Checkbox(checked = row.item.included, onCheckedChange = { onToggle() })
    }
}

@Composable
private fun FailedItem(item: SessionItem) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Outlined.ErrorOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error)
        Spacer(Modifier.width(16.dp))
        Column {
            Text(item.file.displayName, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(item.error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun InfoCard(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun ApplyBar(
    state: PreviewUiState,
    onModeChange: (OperationMode) -> Unit,
    onChooseDestination: () -> Unit,
    onApply: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)) {
            if (!state.destinationReady) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.preview_no_destination), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                    TextButton(onClick = onChooseDestination) { Text(stringResource(R.string.home_choose_destination)) }
                }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.width(6.dp))
                    Text(
                        stringResource(R.string.preview_destination, state.destinationLabel.orEmpty()),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = onChooseDestination) { Text(stringResource(R.string.action_change)) }
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                SegmentedButton(
                    selected = state.mode == OperationMode.COPY,
                    onClick = { onModeChange(OperationMode.COPY) },
                    shape = SegmentedButtonDefaults.itemShape(0, 2),
                    icon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                ) { Text(stringResource(R.string.mode_copy)) }
                SegmentedButton(
                    selected = state.mode == OperationMode.MOVE,
                    onClick = { onModeChange(OperationMode.MOVE) },
                    shape = SegmentedButtonDefaults.itemShape(1, 2),
                    icon = { Icon(Icons.AutoMirrored.Outlined.DriveFileMove, contentDescription = null, modifier = Modifier.size(18.dp)) },
                ) { Text(stringResource(R.string.mode_move)) }
            }
            Text(
                stringResource(if (state.mode == OperationMode.COPY) R.string.mode_copy_hint else R.string.mode_move_hint),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.mode == OperationMode.MOVE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 8.dp),
            )
            Button(
                onClick = onApply,
                enabled = state.includedCount > 0 && !state.applying,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (state.applying) {
                    CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                } else {
                    Text(
                        pluralStringResource(
                            if (state.mode == OperationMode.COPY) R.plurals.preview_apply_copy else R.plurals.preview_apply_move,
                            state.includedCount, state.includedCount,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditSheet(
    row: PreviewRow,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, folder: String) -> Unit,
    onReset: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val extension = row.item.file.extension
    var name by remember(row.item.id) { mutableStateOf(row.plan.name.substringBeforeLast('.')) }
    var folder by remember(row.item.id) { mutableStateOf(row.plan.folder) }
    val analysis = row.item.analysis

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MediaThumbnail(row.item.file.uri, row.item.file.kind, size = 88.dp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(row.item.file.displayName, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
                    Text(
                        "${stringResource(row.plan.category.labelRes())} · ${Formatters.bytes(row.item.file.sizeBytes)}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    row.plan.userCategoryMatch?.let { match ->
                        Text(
                            stringResource(R.string.preview_matched_folder, match.category.folderName, match.reason),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.preview_new_name)) },
                suffix = { if (extension.isNotEmpty()) Text(".$extension") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = folder,
                onValueChange = { folder = it },
                label = { Text(stringResource(R.string.preview_folder)) },
                supportingText = { Text(stringResource(R.string.preview_folder_hint)) },
                leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                suggestions.take(16).forEach { suggestion ->
                    FilterChip(selected = folder == suggestion, onClick = { folder = suggestion }, label = { Text(suggestion) })
                }
            }

            if (analysis != null) {
                Spacer(Modifier.height(16.dp))
                Text(stringResource(R.string.preview_detected), style = MaterialTheme.typography.labelLarge)
                Spacer(Modifier.height(4.dp))
                if (analysis.labels.isNotEmpty()) {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        analysis.labels.take(8).forEach { label ->
                            SuggestionChip(onClick = {}, label = { Text("${label.text} ${Formatters.percent(label.confidence)}") })
                        }
                    }
                }
                val details = buildList {
                    add(stringResource(R.string.detail_confidence, Formatters.percent(analysis.confidence)))
                    if (analysis.faces.count > 0) add(pluralStringResource(R.plurals.detail_faces, analysis.faces.count, analysis.faces.count))
                    analysis.text.title?.let { add(stringResource(R.string.detail_text_title, it)) }
                    analysis.video?.let { video ->
                        add(stringResource(R.string.detail_video_faces, Formatters.percent(video.faceFrameRatio)))
                        add(stringResource(R.string.detail_video_motion, Formatters.percent(video.averageFrameDifference)))
                    }
                    analysis.metadata.captureTimeMillis?.let { date ->
                        val source = stringResource(
                            when (analysis.metadata.dateSource) {
                                DateSource.EXIF -> R.string.date_source_exif
                                DateSource.CONTAINER -> R.string.date_source_container
                                DateSource.FILENAME -> R.string.date_source_filename
                                DateSource.MEDIA_STORE -> R.string.date_source_media_store
                                DateSource.FILE_MODIFIED -> R.string.date_source_modified
                                DateSource.NONE -> R.string.date_source_none
                            },
                        )
                        add(stringResource(R.string.detail_date, Formatters.dateTime(date), source))
                    }
                    if (analysis.limited) add(stringResource(R.string.detail_limited))
                }
                details.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (row.plan.nameEdited || row.plan.folderEdited) {
                    TextButton(onClick = onReset) { Text(stringResource(R.string.preview_reset)) }
                    Spacer(Modifier.weight(1f))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = { onSave(name, folder) }) { Text(stringResource(R.string.action_save)) }
            }
        }
    }
}
