@file:OptIn(ExperimentalMaterial3Api::class)

package com.sortora.app.ui.preview

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.DriveFileMove
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.CopyAll
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Face
import androidx.compose.material.icons.outlined.FilterAltOff
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.HourglassTop
import androidx.compose.material.icons.outlined.ImageSearch
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortora.app.R
import com.sortora.app.domain.filters.SmartFilter
import com.sortora.app.domain.model.OperationMode
import com.sortora.app.domain.model.SessionItem
import com.sortora.app.ui.components.EmptyState
import com.sortora.app.ui.components.MediaThumbnail
import com.sortora.app.ui.components.icon
import com.sortora.app.ui.components.labelRes

@Composable
fun PreviewScreen(
    onBack: () -> Unit,
    onApplied: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFaces: (Long) -> Unit,
    viewModel: PreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var confirmApply by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var folderDialog by rememberSaveable { mutableStateOf(false) }
    var patternDialog by rememberSaveable { mutableStateOf(false) }
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
                                if (state.mode == OperationMode.RENAME) {
                                    pluralStringResource(R.plurals.preview_summary_rename, state.includedCount, state.includedCount)
                                } else {
                                    pluralStringResource(R.plurals.preview_summary, state.includedCount, state.includedCount, state.folders.size)
                                },
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
                    if (state.faceCount > 0) {
                        IconButton(onClick = { onOpenFaces(viewModel.sessionId) }) { Icon(Icons.Outlined.Face, stringResource(R.string.faces_title)) }
                    }
                    IconButton(onClick = { menuOpen = true }) { Icon(Icons.Outlined.MoreVert, stringResource(R.string.action_more)) }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        val scope = if (state.isFiltered) R.plurals.bulk_scope_shown else R.plurals.bulk_scope_all
                        Text(
                            pluralStringResource(scope, state.rows.size, state.rows.size),
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        )
                        DropdownMenuItem(text = { Text(stringResource(R.string.bulk_include)) }, onClick = { menuOpen = false; viewModel.includeVisible(true) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.bulk_exclude)) }, onClick = { menuOpen = false; viewModel.includeVisible(false) })
                        DropdownMenuItem(text = { Text(stringResource(R.string.bulk_rename)) }, onClick = { menuOpen = false; patternDialog = true })
                        if (state.mode != OperationMode.RENAME) {
                            DropdownMenuItem(text = { Text(stringResource(R.string.bulk_set_folder)) }, onClick = { menuOpen = false; folderDialog = true })
                        }
                        DropdownMenuItem(text = { Text(stringResource(R.string.bulk_reset)) }, onClick = { menuOpen = false; viewModel.resetVisible() })
                        HorizontalDivider()
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
                onApply = { if (state.mode == OperationMode.COPY) viewModel.apply() else confirmApply = true },
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
                if (state.duplicateGroups > 0 && SmartFilter.DUPLICATE !in state.smartFilters) {
                    item {
                        InfoCard(
                            Icons.Outlined.CopyAll,
                            pluralStringResource(R.plurals.preview_duplicates_found, state.duplicateGroups, state.duplicateGroups),
                            action = stringResource(R.string.action_review),
                            onAction = { viewModel.toggleSmartFilter(SmartFilter.DUPLICATE) },
                        )
                    }
                }
                if (state.mode != OperationMode.RENAME) {
                    item { FolderChips(state, viewModel::setFolderFilter) }
                }
                item { SmartFilterChips(state, viewModel::toggleSmartFilter, viewModel::clearFilters) }
                if (state.rows.isEmpty()) {
                    item {
                        EmptyState(
                            icon = Icons.Outlined.FilterAltOff,
                            title = stringResource(R.string.preview_no_matches_title),
                            message = stringResource(R.string.preview_no_matches_body),
                        ) { TextButton(onClick = viewModel::clearFilters) { Text(stringResource(R.string.filters_clear)) } }
                    }
                }
                items(state.rows, key = { it.item.id }) { row ->
                    PreviewItem(row, onToggle = { viewModel.toggleIncluded(row) }, onClick = { viewModel.startEditing(row) })
                    HorizontalDivider(Modifier.padding(start = 88.dp))
                }
                if (state.failed.isNotEmpty() && !state.isFiltered) {
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
            duplicates = state.editingDuplicates,
            mode = state.mode,
            suggestions = state.folderSuggestions,
            onDismiss = viewModel::stopEditing,
            onSave = { name, folder -> viewModel.saveEdit(row, name, folder) },
            onReset = { viewModel.resetEdit(row) },
            onKeep = { viewModel.keepThisDuplicate(it) },
        )
    }

    if (folderDialog) {
        SetFolderSheet(
            count = state.rows.size,
            suggestions = state.folderSuggestions,
            onDismiss = { folderDialog = false },
            onConfirm = { folder -> folderDialog = false; viewModel.setFolderForVisible(folder) },
        )
    }

    if (patternDialog) {
        PatternSheet(
            count = state.rows.size,
            preview = viewModel::previewPattern,
            onDismiss = { patternDialog = false },
            onConfirm = { pattern -> patternDialog = false; viewModel.renameVisible(pattern) },
        )
    }

    if (confirmApply) {
        ConfirmApplyDialog(
            mode = state.mode,
            count = state.includedCount,
            onDismiss = { confirmApply = false },
            onConfirm = { confirmApply = false; viewModel.apply() },
        )
    }
}

@Composable
private fun FolderChips(state: PreviewUiState, onSelect: (String?) -> Unit) {
    LazyRow(contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            FilterChip(
                selected = state.folderFilter == null,
                onClick = { onSelect(null) },
                label = { Text(stringResource(R.string.preview_filter_all, state.includedCount)) },
            )
        }
        items(state.folders, key = { it.folder }) { group ->
            FilterChip(
                selected = state.folderFilter == group.folder,
                onClick = { onSelect(group.folder) },
                label = { Text("${group.folder} (${group.count})") },
                leadingIcon = { Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
    }
}

@Composable
private fun SmartFilterChips(state: PreviewUiState, onToggle: (SmartFilter) -> Unit, onClear: () -> Unit) {
    val available = SmartFilter.entries.filter { (state.filterCounts[it] ?: 0) > 0 || it in state.smartFilters }
    if (available.isEmpty()) return
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        items(available, key = { it.name }) { filter ->
            FilterChip(
                selected = filter in state.smartFilters,
                onClick = { onToggle(filter) },
                label = { Text("${stringResource(filter.labelRes())} (${state.filterCounts[filter] ?: 0})") },
                leadingIcon = { Icon(filter.icon(), contentDescription = null, modifier = Modifier.size(18.dp)) },
            )
        }
        if (state.isFiltered) {
            item { TextButton(onClick = onClear) { Text(stringResource(R.string.filters_clear)) } }
        }
    }
}

@Composable
private fun PreviewItem(row: PreviewRow, onToggle: () -> Unit, onClick: () -> Unit) {
    val analysis = row.item.analysis
    val lowConfidence = analysis != null && (analysis.limited || analysis.confidence < 0.5f)
    val duplicate = row.item.duplicate
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
                Icon(
                    if (row.plan.inPlace) Icons.Outlined.DriveFileRenameOutline else Icons.Outlined.Folder,
                    contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.primary,
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    if (row.plan.inPlace) stringResource(R.string.preview_in_place) else row.plan.folder,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (duplicate != null) {
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        if (duplicate.keeper) Icons.Outlined.Star else Icons.Outlined.CopyAll,
                        contentDescription = null, modifier = Modifier.size(14.dp), tint = MaterialTheme.colorScheme.tertiary,
                    )
                    Spacer(Modifier.width(2.dp))
                    Text(
                        stringResource(
                            when {
                                duplicate.keeper -> R.string.duplicate_best
                                duplicate.exact -> R.string.duplicate_exact
                                else -> R.string.duplicate_similar
                            },
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                } else if (lowConfidence) {
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
private fun InfoCard(icon: ImageVector, text: String, action: String? = null, onAction: () -> Unit = {}) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, contentDescription = null)
            Spacer(Modifier.width(12.dp))
            Text(text, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
            if (action != null) TextButton(onClick = onAction) { Text(action) }
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
            if (state.mode != OperationMode.RENAME) {
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
            }
            val modes = listOf(
                Triple(OperationMode.COPY, R.string.mode_copy, Icons.Outlined.ContentCopy),
                Triple(OperationMode.MOVE, R.string.mode_move, Icons.AutoMirrored.Outlined.DriveFileMove),
                Triple(OperationMode.RENAME, R.string.mode_rename, Icons.Outlined.DriveFileRenameOutline),
            )
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                modes.forEachIndexed { index, (mode, label, icon) ->
                    SegmentedButton(
                        selected = state.mode == mode,
                        onClick = { onModeChange(mode) },
                        shape = SegmentedButtonDefaults.itemShape(index, modes.size),
                        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    ) { Text(stringResource(label), maxLines = 1) }
                }
            }
            Text(
                stringResource(state.mode.hintRes()),
                style = MaterialTheme.typography.bodySmall,
                color = if (state.mode == OperationMode.COPY) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
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
                    Text(pluralStringResource(state.mode.applyRes(), state.includedCount, state.includedCount))
                }
            }
        }
    }
}

internal fun OperationMode.hintRes(): Int = when (this) {
    OperationMode.COPY -> R.string.mode_copy_hint
    OperationMode.MOVE -> R.string.mode_move_hint
    OperationMode.RENAME -> R.string.mode_rename_hint
}

internal fun OperationMode.applyRes(): Int = when (this) {
    OperationMode.COPY -> R.plurals.preview_apply_copy
    OperationMode.MOVE -> R.plurals.preview_apply_move
    OperationMode.RENAME -> R.plurals.preview_apply_rename
}
