@file:OptIn(ExperimentalSharedTransitionApi::class)

package com.sortora.app.ui.preview

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.animateBounds
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.CopyAll
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.Face
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.HourglassTop
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.UnfoldLess
import androidx.compose.material.icons.rounded.UnfoldMore
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.LookaheadScope
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortora.app.R
import com.sortora.app.domain.filters.SmartFilter
import com.sortora.app.domain.model.OperationMode
import com.sortora.app.domain.model.SessionItem
import com.sortora.app.ui.components.CenteredEmptyState
import com.sortora.app.ui.components.EmptyState
import com.sortora.app.ui.components.MediaThumbnail
import com.sortora.app.ui.components.icon
import com.sortora.app.ui.components.labelRes
import com.sortora.app.ui.design.Art
import com.sortora.app.ui.design.CheckOrb
import com.sortora.app.ui.design.Eyebrow
import com.sortora.app.ui.design.FluidSpinner
import com.sortora.app.ui.design.FolderGlyph
import com.sortora.app.ui.design.GhostButton
import com.sortora.app.ui.design.GlassDock
import com.sortora.app.ui.design.GlassTopBar
import com.sortora.app.ui.design.IconBubble
import com.sortora.app.ui.design.Motion
import com.sortora.app.ui.design.NoticeCard
import com.sortora.app.ui.design.PageTitle
import com.sortora.app.ui.design.Pill
import com.sortora.app.ui.design.PrimaryButton
import com.sortora.app.ui.design.Radius
import com.sortora.app.ui.design.ScreenScaffold
import com.sortora.app.ui.design.SegmentedControl
import com.sortora.app.ui.design.Sortora
import com.sortora.app.ui.design.Space
import com.sortora.app.ui.design.hairline
import com.sortora.app.ui.design.isScrolled
import com.sortora.app.ui.design.morph
import com.sortora.app.ui.design.pressable
import com.sortora.app.ui.design.scrolledPast
import com.sortora.app.ui.design.sharedThing
import kotlin.math.min

@Composable
fun PreviewScreen(
    onBack: () -> Unit,
    onApplied: (Long) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenFaces: (Long) -> Unit,
    viewModel: PreviewViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val c = Sortora.colors
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    var confirmApply by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var folderDialog by rememberSaveable { mutableStateOf(false) }
    var patternDialog by rememberSaveable { mutableStateOf(false) }
    val expandedStacks = remember { mutableStateMapOf<Long, Boolean>() }
    val pickDestination = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { viewModel.onDestinationPicked(it) }
    val list = rememberLazyListState()
    val scrolled by list.isScrolled()
    val pastTitle by list.scrolledPast(1)

    LaunchedEffect(Unit) {
        viewModel.eventFlow.collect { event ->
            when (event) {
                is PreviewEvent.Applied -> onApplied(event.sessionId)
                is PreviewEvent.Message -> snackbar.showSnackbar(resources.getString(event.text))
            }
        }
    }
    BackHandler(enabled = state.editing != null) { viewModel.stopEditing() }

    val editingId = state.editing?.item?.id
    val summary = if (state.mode == OperationMode.RENAME) {
        pluralStringResource(R.plurals.preview_summary_rename, state.includedCount, state.includedCount)
    } else {
        pluralStringResource(R.plurals.preview_summary, state.includedCount, state.includedCount, state.folders.size)
    }

    Box(Modifier.fillMaxSize()) {
        ScreenScaffold(
            snackbar = snackbar,
            topBar = {
                GlassTopBar(
                    title = stringResource(R.string.preview_title),
                    showTitle = pastTitle,
                    scrolled = scrolled,
                    navigation = { IconBubble(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), onBack) },
                    actions = {
                        if (state.faceCount > 0) {
                            IconBubble(Icons.Rounded.Face, stringResource(R.string.faces_title), { onOpenFaces(viewModel.sessionId) })
                        }
                        Box {
                            IconBubble(Icons.Rounded.MoreHoriz, stringResource(R.string.action_more), { menuOpen = true })
                            BulkMenu(
                                expanded = menuOpen,
                                state = state,
                                onDismiss = { menuOpen = false },
                                onInclude = { viewModel.includeVisible(it) },
                                onRename = { patternDialog = true },
                                onSetFolder = { folderDialog = true },
                                onReset = viewModel::resetVisible,
                                onNamingSettings = onOpenSettings,
                            )
                        }
                    },
                )
            },
            bottomBar = {
                if (!state.loading) {
                    ApplyDock(
                        state = state,
                        onModeChange = viewModel::setMode,
                        onChooseDestination = { pickDestination.launch(null) },
                        onApply = { if (state.mode == OperationMode.COPY) viewModel.apply() else confirmApply = true },
                    )
                }
            },
        ) { padding ->
            when {
                state.loading -> Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { FluidSpinner(size = 34.dp) }
                state.totalAnalyzed == 0 && state.failed.isEmpty() -> CenteredEmptyState(
                    art = Art.Search,
                    title = stringResource(R.string.preview_empty_title),
                    message = stringResource(R.string.preview_empty_body),
                    modifier = Modifier.padding(padding),
                )
                else -> LazyColumn(
                    state = list,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Space.md),
                    verticalArrangement = Arrangement.spacedBy(Space.xs),
                ) {
                    item(key = "title") { PageTitle(stringResource(R.string.preview_title), eyebrow = stringResource(R.string.preview_eyebrow), subtitle = summary) }
                    if (state.stillAnalyzing > 0) {
                        item(key = "analyzing") {
                            NoticeCard(
                                Icons.Rounded.HourglassTop,
                                pluralStringResource(R.plurals.preview_still_analyzing, state.stillAnalyzing, state.stillAnalyzing),
                                Modifier.padding(horizontal = Space.md).animateItem(),
                            )
                        }
                    }
                    if (state.duplicateGroups > 0 && SmartFilter.DUPLICATE !in state.smartFilters) {
                        item(key = "dupes") {
                            NoticeCard(
                                Icons.Rounded.CopyAll,
                                pluralStringResource(R.plurals.preview_duplicates_found, state.duplicateGroups, state.duplicateGroups),
                                Modifier.padding(horizontal = Space.md).animateItem(),
                                action = stringResource(R.string.action_review),
                                onAction = { viewModel.toggleSmartFilter(SmartFilter.DUPLICATE) },
                            )
                        }
                    }
                    if (state.mode != OperationMode.RENAME) {
                        item(key = "folders") { FolderPills(state, viewModel::setFolderFilter) }
                    }
                    item(key = "filters") { SmartFilterPills(state, viewModel::toggleSmartFilter, viewModel::clearFilters) }
                    if (state.rows.isEmpty()) {
                        item(key = "nomatch") {
                            EmptyState(
                                art = Art.Search,
                                title = stringResource(R.string.preview_no_matches_title),
                                message = stringResource(R.string.preview_no_matches_body),
                            ) { GhostButton(stringResource(R.string.filters_clear), viewModel::clearFilters) }
                        }
                    }

                    if (SmartFilter.DUPLICATE in state.smartFilters) {
                        val groups = state.rows.groupBy { it.item.duplicate?.groupId ?: -it.item.id }
                        groups.forEach { (groupId, members) ->
                            item(key = "dup-$groupId") {
                                val ordered = members.sortedByDescending { it.item.duplicate?.keeper == true }
                                DuplicateStack(
                                    members = ordered,
                                    expanded = expandedStacks[groupId] == true,
                                    onToggleExpanded = { expandedStacks[groupId] = expandedStacks[groupId] != true },
                                    editingId = editingId,
                                    onEdit = viewModel::startEditing,
                                    onToggleIncluded = viewModel::toggleIncluded,
                                    modifier = Modifier.animateItem(),
                                )
                            }
                        }
                    } else {
                        items(state.rows, key = { it.item.id }) { row ->
                            MorphingRow(
                                row = row,
                                hidden = editingId == row.item.id,
                                onClick = { viewModel.startEditing(row) },
                                onToggle = { viewModel.toggleIncluded(row) },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }

                    if (state.failed.isNotEmpty() && !state.isFiltered) {
                        item(key = "failedHeader") {
                            Eyebrow(
                                pluralStringResource(R.plurals.preview_failed_header, state.failed.size, state.failed.size),
                                Modifier.padding(start = Space.gutter + Space.xxs, top = Space.xl, bottom = Space.xxs),
                                color = c.danger,
                            )
                        }
                        items(state.failed, key = { "failed-${it.id}" }) { item -> FailedRow(item) }
                    }
                }
            }
        }

        EditOverlay(
            editing = state.editing,
            duplicates = state.editingDuplicates,
            mode = state.mode,
            suggestions = state.folderSuggestions,
            onDismiss = viewModel::stopEditing,
            onSave = { row, name, folder -> viewModel.saveEdit(row, name, folder) },
            onReset = { viewModel.resetEdit(it) },
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

internal fun editKey(id: Long) = "edit-$id"
internal fun thumbKey(id: Long) = "thumb-$id"

/**
 * A preview row that can morph into the editor. While its editor is open the row keeps its
 * space (an invisible copy) so the list doesn't shift underneath.
 */
@Composable
private fun MorphingRow(row: PreviewRow, hidden: Boolean, onClick: () -> Unit, onToggle: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedContent(
        targetState = hidden,
        transitionSpec = { fadeIn(Motion.fade(200)) togetherWith fadeOut(Motion.fade(120)) using SizeTransform(clip = false) },
        modifier = modifier,
        label = "row",
    ) { isHidden ->
        if (isHidden) {
            PreviewRowCard(row, onClick = {}, onToggle = {}, modifier = Modifier.alpha(0f))
        } else {
            PreviewRowCard(row, onClick, onToggle, scope = this, modifier = Modifier.morph(editKey(row.item.id), this))
        }
    }
}

@Composable
internal fun PreviewRowCard(
    row: PreviewRow,
    onClick: () -> Unit,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    scope: AnimatedVisibilityScope? = null,
    elevated: Boolean = false,
) {
    val c = Sortora.colors
    val analysis = row.item.analysis
    val lowConfidence = analysis != null && (analysis.limited || analysis.confidence < 0.5f)
    val duplicate = row.item.duplicate
    val included = row.item.included
    val contentAlpha by animateFloatAsState(if (included) 1f else 0.5f, Motion.fade(), label = "included")
    Row(
        Modifier
            .padding(horizontal = Space.md)
            .then(modifier)
            .fillMaxWidth()
            .pressable(onClick, pressedScale = 0.985f)
            .clip(Radius.Lg)
            .background(if (elevated) c.raised else c.surface)
            .hairline(Radius.Lg, c.hairline)
            .padding(start = Space.sm, top = Space.sm, bottom = Space.sm, end = Space.xxs),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        MediaThumbnail(
            row.item.file.uri, row.item.file.kind, size = 60.dp, radius = Radius.sm,
            modifier = Modifier.alpha(contentAlpha).then(if (scope != null) Modifier.sharedThing(thumbKey(row.item.id), scope) else Modifier),
        )
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f).alpha(contentAlpha)) {
            Text(row.plan.name, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Text(
                stringResource(R.string.preview_was, row.item.file.displayName),
                style = MaterialTheme.typography.bodySmall,
                color = c.inkTertiary,
                maxLines = 1,
                overflow = TextOverflow.MiddleEllipsis,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Tag(
                    icon = if (row.plan.inPlace) Icons.Rounded.DriveFileRenameOutline else Icons.Rounded.Folder,
                    text = if (row.plan.inPlace) stringResource(R.string.preview_in_place) else row.plan.folder,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (duplicate != null) {
                    Spacer(Modifier.width(6.dp))
                    Tag(
                        icon = if (duplicate.keeper) Icons.Rounded.Star else Icons.Rounded.CopyAll,
                        text = stringResource(
                            when {
                                duplicate.keeper -> R.string.duplicate_best
                                duplicate.exact -> R.string.duplicate_exact
                                else -> R.string.duplicate_similar
                            },
                        ),
                        tint = if (duplicate.keeper) c.accent else c.warning,
                    )
                } else if (lowConfidence) {
                    Spacer(Modifier.width(6.dp))
                    Tag(Icons.Rounded.WarningAmber, stringResource(R.string.preview_low_confidence), tint = c.warning)
                }
            }
        }
        CheckOrb(included, onToggle, contentDescription = stringResource(R.string.cd_include_file))
    }
}

@Composable
private fun Tag(icon: androidx.compose.ui.graphics.vector.ImageVector, text: String, modifier: Modifier = Modifier, tint: Color = Sortora.colors.inkSecondary) {
    val c = Sortora.colors
    Row(
        modifier
            .clip(Radius.Pill)
            .background(if (tint == c.inkSecondary) c.sunken else androidx.compose.ui.graphics.lerp(c.surface, tint, 0.12f))
            .padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(12.dp))
        Spacer(Modifier.width(4.dp))
        Text(text, style = MaterialTheme.typography.labelSmall.copy(letterSpacing = MaterialTheme.typography.labelMedium.letterSpacing), color = if (tint == c.inkSecondary) c.inkSecondary else tint, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/**
 * Copies of one photo. Collapsed, they sit as a neat stack with the suggested keeper on top;
 * expanded, they slide apart into a list. Each card glides between the two layouts.
 */
@Composable
private fun DuplicateStack(
    members: List<PreviewRow>,
    expanded: Boolean,
    onToggleExpanded: () -> Unit,
    editingId: Long?,
    onEdit: (PreviewRow) -> Unit,
    onToggleIncluded: (PreviewRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val spread by animateFloatAsState(if (expanded) 1f else 0f, Motion.gentle(), label = "spread")
    val exact = members.firstOrNull()?.item?.duplicate?.exact == true
    Column(modifier.fillMaxWidth().padding(bottom = Space.sm)) {
        Row(Modifier.fillMaxWidth().padding(start = Space.gutter + Space.xxs, end = Space.sm), verticalAlignment = Alignment.CenterVertically) {
            Eyebrow(
                pluralStringResource(R.plurals.duplicate_stack_title, members.size, members.size) + " · " +
                    stringResource(if (exact) R.string.duplicate_exact else R.string.duplicate_similar),
                Modifier.weight(1f),
            )
            if (members.size > 1) {
                GhostButton(
                    stringResource(if (expanded) R.string.duplicate_stack_collapse else R.string.duplicate_stack_expand),
                    onToggleExpanded,
                    leading = if (expanded) Icons.Rounded.UnfoldLess else Icons.Rounded.UnfoldMore,
                )
            }
        }
        LookaheadScope {
            val peek = 10.dp
            val gap = Space.xs
            Layout(
                content = {
                    members.forEachIndexed { i, row ->
                        Box(Modifier.animateBounds(this@LookaheadScope)) {
                            MorphingStackCard(
                                row = row,
                                hidden = editingId == row.item.id,
                                // Collapsed, a tap fans the stack out; expanded, it opens that copy.
                                onClick = { if (!expanded && members.size > 1) onToggleExpanded() else onEdit(row) },
                                onToggle = { onToggleIncluded(row) },
                            )
                        }
                    }
                },
            ) { measurables, constraints ->
                val placeables = measurables.map { it.measure(constraints.copy(minHeight = 0)) }
                val gapPx = gap.roundToPx()
                val peekPx = peek.roundToPx()
                if (placeables.isEmpty()) return@Layout layout(constraints.maxWidth, 0) {}
                if (expanded) {
                    val height = placeables.sumOf { it.height } + gapPx * (placeables.size - 1)
                    layout(constraints.maxWidth, height) {
                        var y = 0
                        placeables.forEach { p ->
                            p.place(0, y)
                            y += p.height + gapPx
                        }
                    }
                } else {
                    val shown = min(placeables.size, 3)
                    val height = placeables[0].height + peekPx * (shown - 1)
                    layout(constraints.maxWidth, height) {
                        placeables.forEachIndexed { i, p ->
                            val depth = min(i, 2)
                            p.placeWithLayer(0, depth * peekPx, zIndex = (placeables.size - i).toFloat()) {
                                val s = 1f - 0.045f * depth * (1f - spread)
                                scaleX = s
                                scaleY = s
                                alpha = if (i < 3) 1f - 0.18f * depth * (1f - spread) else spread
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MorphingStackCard(row: PreviewRow, hidden: Boolean, onClick: () -> Unit, onToggle: () -> Unit) {
    AnimatedContent(
        targetState = hidden,
        transitionSpec = { fadeIn(Motion.fade(200)) togetherWith fadeOut(Motion.fade(120)) using SizeTransform(clip = false) },
        label = "stackCard",
    ) { isHidden ->
        if (isHidden) PreviewRowCard(row, {}, {}, Modifier.alpha(0f))
        else PreviewRowCard(row, onClick, onToggle, scope = this, modifier = Modifier.morph(editKey(row.item.id), this))
    }
}

@Composable
private fun FailedRow(item: SessionItem) {
    val c = Sortora.colors
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.gutter, vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Rounded.ErrorOutline, contentDescription = null, tint = c.danger, modifier = Modifier.size(20.dp))
        Spacer(Modifier.width(Space.md))
        Column {
            Text(item.file.displayName, style = MaterialTheme.typography.bodyMedium, color = c.ink, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
            Text(item.error.orEmpty(), style = MaterialTheme.typography.bodySmall, color = c.inkSecondary)
        }
    }
}

@Composable
private fun FolderPills(state: PreviewUiState, onSelect: (String?) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = Space.md), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        item(key = "all") { Pill(stringResource(R.string.filter_all), state.folderFilter == null, { onSelect(null) }, count = state.includedCount) }
        items(state.folders, key = { it.folder }) { group ->
            val selected = state.folderFilter == group.folder
            Pill(
                label = group.folder,
                selected = selected,
                onClick = { onSelect(if (selected) null else group.folder) },
                count = group.count,
                leading = { _ -> FolderGlyph(width = 18.dp, open = selected, active = selected) },
            )
        }
    }
}

@Composable
private fun SmartFilterPills(state: PreviewUiState, onToggle: (SmartFilter) -> Unit, onClear: () -> Unit) {
    val available = SmartFilter.entries.filter { (state.filterCounts[it] ?: 0) > 0 || it in state.smartFilters }
    if (available.isEmpty()) return
    LazyRow(contentPadding = PaddingValues(horizontal = Space.md), horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
        items(available, key = { it.name }) { filter ->
            Pill(
                label = stringResource(filter.labelRes()),
                selected = filter in state.smartFilters,
                onClick = { onToggle(filter) },
                count = state.filterCounts[filter] ?: 0,
                leading = { tint -> Icon(filter.icon(), contentDescription = null, tint = tint, modifier = Modifier.size(16.dp)) },
            )
        }
        if (state.isFiltered) {
            item(key = "clear") { GhostButton(stringResource(R.string.filters_clear), onClear) }
        }
    }
}

@Composable
private fun BulkMenu(
    expanded: Boolean,
    state: PreviewUiState,
    onDismiss: () -> Unit,
    onInclude: (Boolean) -> Unit,
    onRename: () -> Unit,
    onSetFolder: () -> Unit,
    onReset: () -> Unit,
    onNamingSettings: () -> Unit,
) {
    val c = Sortora.colors
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        shape = Radius.Lg,
        containerColor = c.raised,
        tonalElevation = 0.dp,
        shadowElevation = 12.dp,
        border = androidx.compose.foundation.BorderStroke(Dp.Hairline, c.hairline),
    ) {
        val scope = if (state.isFiltered) R.plurals.bulk_scope_shown else R.plurals.bulk_scope_all
        Eyebrow(pluralStringResource(scope, state.rows.size, state.rows.size), Modifier.padding(horizontal = Space.md, vertical = Space.xs))
        val colors = MenuDefaults.itemColors(textColor = c.ink)
        DropdownMenuItem(text = { Text(stringResource(R.string.bulk_include)) }, onClick = { onDismiss(); onInclude(true) }, colors = colors)
        DropdownMenuItem(text = { Text(stringResource(R.string.bulk_exclude)) }, onClick = { onDismiss(); onInclude(false) }, colors = colors)
        DropdownMenuItem(text = { Text(stringResource(R.string.bulk_rename)) }, onClick = { onDismiss(); onRename() }, colors = colors)
        if (state.mode != OperationMode.RENAME) {
            DropdownMenuItem(text = { Text(stringResource(R.string.bulk_set_folder)) }, onClick = { onDismiss(); onSetFolder() }, colors = colors)
        }
        DropdownMenuItem(text = { Text(stringResource(R.string.bulk_reset)) }, onClick = { onDismiss(); onReset() }, colors = colors)
        HorizontalDivider(color = c.hairline, thickness = Dp.Hairline)
        DropdownMenuItem(text = { Text(stringResource(R.string.preview_naming_settings)) }, onClick = { onDismiss(); onNamingSettings() }, colors = colors)
    }
}

@Composable
private fun ApplyDock(
    state: PreviewUiState,
    onModeChange: (OperationMode) -> Unit,
    onChooseDestination: () -> Unit,
    onApply: () -> Unit,
) {
    val c = Sortora.colors
    GlassDock {
        AnimatedVisibility(state.mode != OperationMode.RENAME) {
            Row(Modifier.fillMaxWidth().padding(bottom = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                FolderGlyph(width = 28.dp, open = state.destinationReady, active = state.destinationReady)
                Spacer(Modifier.width(Space.sm))
                if (!state.destinationReady) {
                    Text(stringResource(R.string.preview_no_destination), style = MaterialTheme.typography.bodySmall, color = c.danger, maxLines = 2, modifier = Modifier.weight(1f))
                    GhostButton(stringResource(R.string.action_choose), onChooseDestination)
                } else {
                    Text(
                        stringResource(R.string.preview_destination, state.destinationLabel.orEmpty()),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.inkSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    GhostButton(stringResource(R.string.action_change), onChooseDestination)
                }
            }
        }
        SegmentedControl(
            options = listOf(OperationMode.COPY, OperationMode.MOVE, OperationMode.RENAME),
            selected = state.mode,
            onSelect = onModeChange,
            label = { stringResource(it.labelRes()) },
            icon = { it.iconOf() },
        )
        AnimatedContent(state.mode, transitionSpec = { fadeIn(Motion.fade()) togetherWith fadeOut(Motion.fade(120)) using SizeTransform(clip = false) }, label = "hint") { mode ->
            Text(
                stringResource(mode.hintRes()),
                style = MaterialTheme.typography.bodySmall,
                color = if (mode == OperationMode.COPY) c.inkSecondary else c.warning,
                modifier = Modifier.fillMaxWidth().padding(vertical = Space.sm),
            )
        }
        PrimaryButton(
            text = pluralStringResource(state.mode.applyRes(), state.includedCount, state.includedCount),
            onClick = onApply,
            enabled = state.includedCount > 0,
            loading = state.applying,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

private fun OperationMode.labelRes(): Int = when (this) {
    OperationMode.COPY -> R.string.mode_copy
    OperationMode.MOVE -> R.string.mode_move
    OperationMode.RENAME -> R.string.mode_rename
}

private fun OperationMode.iconOf() = when (this) {
    OperationMode.COPY -> Icons.Rounded.ContentCopy
    OperationMode.MOVE -> Icons.AutoMirrored.Rounded.DriveFileMove
    OperationMode.RENAME -> Icons.Rounded.DriveFileRenameOutline
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
