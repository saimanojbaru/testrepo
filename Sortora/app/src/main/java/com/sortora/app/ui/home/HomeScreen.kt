@file:OptIn(ExperimentalMaterial3Api::class)

package com.sortora.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CreateNewFolder
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.PermMedia
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortora.app.R
import com.sortora.app.domain.model.SessionStatus
import com.sortora.app.domain.model.SortSession
import com.sortora.app.ui.components.EmptyState
import com.sortora.app.ui.components.Formatters
import com.sortora.app.ui.components.PrivacyBanner

@Composable
fun HomeScreen(
    onOpenSession: (Long) -> Unit,
    onOpenPreview: (Long) -> Unit,
    onOpenApply: (Long) -> Unit,
    onOpenHistory: () -> Unit,
    onOpenSettings: () -> Unit,
    viewModel: HomeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val defaultLabel = stringResource(R.string.home_selected_files_label)
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }

    val pickFiles = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { viewModel.onFilesPicked(it) }
    val pickFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { viewModel.onFolderPicked(it) }
    val pickDestination = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { viewModel.onDestinationPicked(it) }
    // Notifications are optional: whatever the answer, analysis starts.
    val requestNotifications = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        viewModel.startAnalysis(defaultLabel)
    }

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is HomeEvent.SessionStarted -> onOpenSession(event.sessionId)
                is HomeEvent.Message -> snackbar.showSnackbar(resources.getString(event.text))
            }
        }
    }

    fun analyze() {
        val needsPermission = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        if (needsPermission && !askedForNotifications) {
            askedForNotifications = true
            requestNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.startAnalysis(defaultLabel)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_name), fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onOpenHistory) { Icon(Icons.Outlined.History, stringResource(R.string.history_title)) }
                    IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, stringResource(R.string.settings_title)) }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Button(
                    onClick = ::analyze,
                    enabled = state.canAnalyze,
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(16.dp)
                        .height(52.dp),
                ) {
                    if (state.starting) {
                        CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        Icon(Icons.Outlined.AutoAwesome, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        val count = state.selection?.files?.size ?: 0
                        Text(
                            if (count > 0) pluralStringResource(R.plurals.home_analyze_count, count, count)
                            else stringResource(R.string.home_analyze),
                        )
                    }
                }
            }
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item { PrivacyBanner() }

            state.resumable?.let { session ->
                item {
                    ResumeCard(
                        session = session,
                        onResume = {
                            when (session.status) {
                                SessionStatus.ANALYZING -> onOpenSession(session.id)
                                SessionStatus.APPLYING -> onOpenApply(session.id)
                                else -> onOpenPreview(session.id)
                            }
                        },
                        onDiscard = viewModel::discardResumable,
                    )
                }
            }

            item {
                StepCard(number = 1, title = stringResource(R.string.home_step_media)) {
                    val selection = state.selection
                    when {
                        state.scanning -> Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                            Spacer(Modifier.width(12.dp))
                            Text(stringResource(R.string.home_scanning))
                        }
                        selection == null || selection.files.isEmpty() -> EmptyState(
                            icon = Icons.Outlined.PhotoLibrary,
                            title = stringResource(R.string.home_empty_title),
                            message = stringResource(R.string.home_empty_body),
                        )
                        else -> SelectionSummary(selection, onClear = viewModel::clearSelection)
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilledTonalButton(onClick = { pickFiles.launch(arrayOf("image/*", "video/*")) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.PermMedia, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.home_pick_files))
                        }
                        FilledTonalButton(onClick = { pickFolder.launch(null) }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Outlined.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.home_pick_folder))
                        }
                    }
                    if (state.selection?.treeUri != null) {
                        Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(stringResource(R.string.settings_include_subfolders), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            Switch(checked = state.includeSubfolders, onCheckedChange = viewModel::setIncludeSubfolders)
                        }
                    }
                }
            }

            item {
                StepCard(number = 2, title = stringResource(R.string.home_step_destination)) {
                    if (state.destinationReady) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Folder, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Text(
                                state.destinationLabel ?: stringResource(R.string.home_destination_unnamed),
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            TextButton(onClick = { pickDestination.launch(null) }) { Text(stringResource(R.string.action_change)) }
                        }
                        Text(
                            stringResource(R.string.home_destination_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        if (state.destinationLabel != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.WarningAmber, contentDescription = null, tint = MaterialTheme.colorScheme.error)
                                Spacer(Modifier.width(8.dp))
                                Text(stringResource(R.string.home_destination_lost), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                            }
                            Spacer(Modifier.height(8.dp))
                        } else {
                            Text(stringResource(R.string.home_destination_empty), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(12.dp))
                        }
                        OutlinedButton(onClick = { pickDestination.launch(null) }) {
                            Icon(Icons.Outlined.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(stringResource(R.string.home_choose_destination))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepCard(number: Int, title: String, content: @Composable () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)) {
        Column(Modifier.padding(16.dp)) {
            Text(
                stringResource(R.string.home_step_format, number, title),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
private fun SelectionSummary(selection: SourceSelection, onClear: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(
                pluralStringResource(R.plurals.home_items_selected, selection.files.size, selection.files.size),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                pluralStringResource(R.plurals.home_photos, selection.photoCount, selection.photoCount) + " · " +
                    pluralStringResource(R.plurals.home_videos, selection.videoCount, selection.videoCount) +
                    (if (selection.label.isNotBlank()) " · ${selection.label}" else ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onClear) { Icon(Icons.Outlined.Close, stringResource(R.string.action_clear)) }
    }
}

@Composable
private fun ResumeCard(session: SortSession, onResume: () -> Unit, onDiscard: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Restore, contentDescription = null)
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_resume_title), style = MaterialTheme.typography.titleSmall)
                    Text(
                        stringResource(R.string.home_resume_body, session.analyzedItems, session.totalItems, Formatters.dateTime(session.createdAt)),
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (session.status != SessionStatus.APPLYING) {
                    TextButton(onClick = onDiscard) { Text(stringResource(R.string.action_discard)) }
                }
                TextButton(onClick = onResume) { Text(stringResource(R.string.action_resume)) }
            }
        }
    }
}
