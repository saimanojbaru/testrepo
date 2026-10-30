package com.sortora.app.ui.home

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.core.content.ContextCompat
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortora.app.R
import com.sortora.app.domain.model.SessionStatus
import com.sortora.app.domain.model.SortSession
import com.sortora.app.ui.components.Formatters
import com.sortora.app.ui.components.MediaThumbnail
import com.sortora.app.ui.components.PrivacyBanner
import com.sortora.app.ui.design.Art
import com.sortora.app.ui.design.Depth
import com.sortora.app.ui.design.Eyebrow
import com.sortora.app.ui.design.FluidRing
import com.sortora.app.ui.design.FluidSpinner
import com.sortora.app.ui.design.FolderGlyph
import com.sortora.app.ui.design.GhostButton
import com.sortora.app.ui.design.GlassDock
import com.sortora.app.ui.design.GlassTopBar
import com.sortora.app.ui.design.IconBubble
import com.sortora.app.ui.design.Illustration
import com.sortora.app.ui.design.Motion
import com.sortora.app.ui.design.PrimaryButton
import com.sortora.app.ui.design.Radius
import com.sortora.app.ui.design.ScreenScaffold
import com.sortora.app.ui.design.SecondaryButton
import com.sortora.app.ui.design.SoftCard
import com.sortora.app.ui.design.Sortora
import com.sortora.app.ui.design.Space
import com.sortora.app.ui.design.ToggleRow
import com.sortora.app.ui.design.isScrolled
import com.sortora.app.ui.design.morph
import com.sortora.app.ui.design.scrolledPast

/** Shared-element key: the Home call to action becomes the Analysis progress orb. */
const val SORT_ORB_KEY = "sort-orb"

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
    val c = Sortora.colors
    val context = LocalContext.current
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val defaultLabel = stringResource(R.string.home_selected_files_label)
    var askedForNotifications by rememberSaveable { mutableStateOf(false) }
    val list = rememberLazyListState()
    val scrolled by list.isScrolled()
    val pastTitle by list.scrolledPast(1)

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

    ScreenScaffold(
        ambient = true,
        snackbar = snackbar,
        topBar = {
            GlassTopBar(
                title = stringResource(R.string.app_name),
                showTitle = pastTitle,
                scrolled = scrolled,
                actions = {
                    IconBubble(Icons.Rounded.History, stringResource(R.string.history_title), onOpenHistory)
                    IconBubble(Icons.Rounded.Settings, stringResource(R.string.settings_title), onOpenSettings)
                },
            )
        },
        bottomBar = {
            GlassDock {
                val count = state.selection?.files?.size ?: 0
                PrimaryButton(
                    text = if (count > 0) pluralStringResource(R.plurals.home_sort_cta, count, count) else stringResource(R.string.home_sort_cta_empty),
                    onClick = ::analyze,
                    enabled = state.canAnalyze,
                    loading = state.starting,
                    leading = Icons.Rounded.AutoAwesome,
                    modifier = Modifier.fillMaxWidth().morph(SORT_ORB_KEY),
                )
            }
        },
    ) { padding ->
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item(key = "title") {
                Column(Modifier.padding(horizontal = Space.gutter).padding(top = Space.xs)) {
                    Eyebrow(stringResource(R.string.home_eyebrow))
                    Spacer(Modifier.height(Space.xs))
                    Text(stringResource(R.string.home_title), style = MaterialTheme.typography.displaySmall, color = c.ink)
                }
            }
            item(key = "privacy") { PrivacyBanner(Modifier.padding(horizontal = Space.md)) }

            state.resumable?.let { session ->
                item(key = "resume") {
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
                        modifier = Modifier.animateItem(),
                    )
                }
            }

            item(key = "media") {
                Column(Modifier.padding(horizontal = Space.md)) {
                    Eyebrow(stringResource(R.string.home_step_media), Modifier.padding(start = Space.xxs, top = Space.xs, bottom = Space.xs))
                    SelectionHero(state, onClear = viewModel::clearSelection)
                    Spacer(Modifier.height(Space.sm))
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        SourceTile(
                            icon = Icons.Rounded.PhotoLibrary,
                            title = stringResource(R.string.home_pick_files),
                            hint = stringResource(R.string.home_source_files_hint),
                            onClick = { pickFiles.launch(arrayOf("image/*", "video/*")) },
                            modifier = Modifier.weight(1f),
                        )
                        SourceTile(
                            icon = Icons.Rounded.FolderOpen,
                            title = stringResource(R.string.home_pick_folder),
                            hint = stringResource(R.string.home_source_folder_hint),
                            onClick = { pickFolder.launch(null) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                    AnimatedVisibility(
                        visible = state.selection?.treeUri != null,
                        enter = fadeIn(Motion.fade()) + expandVertically(Motion.gentle()),
                        exit = fadeOut(Motion.fade(160)) + shrinkVertically(Motion.gentle()),
                    ) {
                        SoftCard(Modifier.padding(top = Space.sm).fillMaxWidth(), contentPadding = PaddingValues(vertical = Space.xxs)) {
                            ToggleRow(stringResource(R.string.settings_include_subfolders), null, state.includeSubfolders, viewModel::setIncludeSubfolders)
                        }
                    }
                }
            }

            item(key = "destination") {
                Column(Modifier.padding(horizontal = Space.md)) {
                    Eyebrow(stringResource(R.string.home_step_destination), Modifier.padding(start = Space.xxs, top = Space.xs, bottom = Space.xs))
                    DestinationCard(state, onChoose = { pickDestination.launch(null) })
                }
            }
        }
    }
}

/** Big, quiet area showing what's selected: an illustration when empty, a fan of thumbnails after. */
@Composable
private fun SelectionHero(state: HomeUiState, onClear: () -> Unit) {
    val c = Sortora.colors
    val selection = state.selection
    val mode = when {
        state.scanning -> 1
        selection != null && selection.files.isNotEmpty() -> 2
        else -> 0
    }
    SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Space.lg)) {
        AnimatedContent(
            targetState = mode,
            transitionSpec = {
                (fadeIn(Motion.fade(320)) + scaleIn(Motion.gentle(), initialScale = 0.96f)) togetherWith fadeOut(Motion.fade(160)) using SizeTransform(clip = false)
            },
            label = "selection",
        ) { m ->
            when (m) {
                1 -> Row(Modifier.fillMaxWidth().height(96.dp), verticalAlignment = Alignment.CenterVertically) {
                    FluidSpinner(size = 26.dp)
                    Spacer(Modifier.width(Space.md))
                    Text(stringResource(R.string.home_scanning), style = MaterialTheme.typography.bodyMedium, color = c.inkSecondary)
                }
                2 -> if (selection != null) SelectionSummary(selection, onClear)
                else -> Row(verticalAlignment = Alignment.CenterVertically) {
                    Illustration(Art.Photos, size = 104.dp)
                    Spacer(Modifier.width(Space.md))
                    Column(Modifier.weight(1f)) {
                        Text(stringResource(R.string.home_empty_title), style = MaterialTheme.typography.titleMedium, color = c.ink)
                        Spacer(Modifier.height(Space.xxs))
                        Text(stringResource(R.string.home_empty_body), style = MaterialTheme.typography.bodySmall, color = c.inkSecondary)
                    }
                }
            }
        }
    }
}

@Composable
private fun SelectionSummary(selection: SourceSelection, onClear: () -> Unit) {
    val c = Sortora.colors
    Row(verticalAlignment = Alignment.CenterVertically) {
        // A small fanned stack of the first few files.
        Box(Modifier.size(width = 104.dp, height = 96.dp), contentAlignment = Alignment.Center) {
            val preview = selection.files.take(3)
            preview.forEachIndexed { i, file ->
                val fromCenter = i - (preview.size - 1) / 2f
                MediaThumbnail(
                    file.uri, file.kind, size = 64.dp, radius = Radius.sm,
                    modifier = Modifier
                        .zIndex(if (fromCenter == 0f) 2f else 1f - kotlin.math.abs(fromCenter))
                        .graphicsLayer {
                            translationX = fromCenter * 20.dp.toPx()
                            rotationZ = fromCenter * 9f
                            translationY = kotlin.math.abs(fromCenter) * 4.dp.toPx()
                        }
                        .clip(Radius.Sm)
                        .background(c.surface)
                        .padding(2.dp),
                )
            }
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(
                pluralStringResource(R.plurals.home_items_selected, selection.files.size, selection.files.size),
                style = MaterialTheme.typography.titleMedium,
                color = c.ink,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                pluralStringResource(R.plurals.home_photos, selection.photoCount, selection.photoCount) + " · " +
                    pluralStringResource(R.plurals.home_videos, selection.videoCount, selection.videoCount),
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSecondary,
            )
            if (selection.label.isNotBlank()) {
                Text(selection.label, style = MaterialTheme.typography.bodySmall, color = c.inkTertiary, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        IconBubble(Icons.Rounded.Close, stringResource(R.string.action_clear), onClear)
    }
}

@Composable
private fun SourceTile(icon: ImageVector, title: String, hint: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val c = Sortora.colors
    SoftCard(modifier, onClick = onClick, contentPadding = PaddingValues(Space.md)) {
        Box(Modifier.size(38.dp).clip(Radius.Sm).background(c.accentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.height(Space.sm))
        Text(title, style = MaterialTheme.typography.titleSmall, color = c.ink)
        Text(hint, style = MaterialTheme.typography.bodySmall, color = c.inkSecondary, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

@Composable
private fun DestinationCard(state: HomeUiState, onChoose: () -> Unit) {
    val c = Sortora.colors
    SoftCard(Modifier.fillMaxWidth(), contentPadding = PaddingValues(Space.md)) {
        if (state.destinationReady) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                FolderGlyph(width = 46.dp, open = true, active = true)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text(stringResource(R.string.home_destination_title), style = MaterialTheme.typography.labelMedium, color = c.inkSecondary)
                    Text(
                        state.destinationLabel ?: stringResource(R.string.home_destination_unnamed),
                        style = MaterialTheme.typography.titleMedium,
                        color = c.ink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                GhostButton(stringResource(R.string.action_change), onChoose)
            }
            Spacer(Modifier.height(Space.xs))
            Text(stringResource(R.string.home_destination_hint), style = MaterialTheme.typography.bodySmall, color = c.inkTertiary)
        } else {
            Row(verticalAlignment = Alignment.Top) {
                FolderGlyph(width = 46.dp)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    if (state.destinationLabel != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Rounded.WarningAmber, contentDescription = null, tint = c.danger, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(stringResource(R.string.home_destination_lost), color = c.danger, style = MaterialTheme.typography.bodySmall)
                        }
                    } else {
                        Text(stringResource(R.string.home_destination_empty), style = MaterialTheme.typography.bodySmall, color = c.inkSecondary)
                    }
                    Spacer(Modifier.height(Space.sm))
                    SecondaryButton(stringResource(R.string.home_choose_destination), onChoose, leading = Icons.Rounded.CreateNewFolder, compact = true)
                }
            }
        }
    }
}

@Composable
private fun ResumeCard(session: SortSession, onResume: () -> Unit, onDiscard: () -> Unit, modifier: Modifier = Modifier) {
    val c = Sortora.colors
    val progress = if (session.totalItems == 0) 0f else session.analyzedItems.toFloat() / session.totalItems
    SoftCard(modifier.padding(horizontal = Space.md).fillMaxWidth(), depth = Depth.Mid, contentPadding = PaddingValues(Space.md)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            FluidRing(progress, size = 52.dp, strokeWidth = 4.dp) {
                Text("${(progress * 100).toInt()}", style = MaterialTheme.typography.labelSmall, color = c.ink)
            }
            Spacer(Modifier.width(Space.sm))
            Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.home_resume_title), style = MaterialTheme.typography.titleSmall, color = c.ink)
                Text(
                    stringResource(R.string.home_resume_body, session.analyzedItems, session.totalItems, Formatters.dateTime(session.createdAt)),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkSecondary,
                )
            }
        }
        Spacer(Modifier.height(Space.sm))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            if (session.status != SessionStatus.APPLYING) GhostButton(stringResource(R.string.action_discard), onDiscard, color = c.inkSecondary)
            Spacer(Modifier.width(Space.xs))
            SecondaryButton(stringResource(R.string.action_resume), onResume, compact = true)
        }
    }
}
