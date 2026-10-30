@file:OptIn(ExperimentalLayoutApi::class)

package com.sortora.app.ui.preview

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.sortora.app.R
import com.sortora.app.domain.filters.SmartFilter
import com.sortora.app.domain.model.DateSource
import com.sortora.app.domain.model.OperationMode
import com.sortora.app.domain.naming.FileNameGenerator
import com.sortora.app.ui.components.Formatters
import com.sortora.app.ui.components.MediaThumbnail
import com.sortora.app.ui.components.labelRes
import com.sortora.app.ui.design.Depth
import com.sortora.app.ui.design.Eyebrow
import com.sortora.app.ui.design.GhostButton
import com.sortora.app.ui.design.MonoStyle
import com.sortora.app.ui.design.Motion
import com.sortora.app.ui.design.Pill
import com.sortora.app.ui.design.PrimaryButton
import com.sortora.app.ui.design.Radius
import com.sortora.app.ui.design.SecondaryButton
import com.sortora.app.ui.design.Sortora
import com.sortora.app.ui.design.SortoraDialog
import com.sortora.app.ui.design.SortoraSheet
import com.sortora.app.ui.design.SortoraTextField
import com.sortora.app.ui.design.Space
import com.sortora.app.ui.design.hairline
import com.sortora.app.ui.design.morph
import com.sortora.app.ui.design.sharedThing
import com.sortora.app.ui.design.softShadow

/**
 * The file editor. It isn't a separate window: the tapped row's card grows out of the list into
 * this floating card (and shrinks back into place on close), its thumbnail flying ahead of it.
 */
@Composable
internal fun EditOverlay(
    editing: PreviewRow?,
    duplicates: List<PreviewRow>,
    mode: OperationMode,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onSave: (PreviewRow, String, String) -> Unit,
    onReset: (PreviewRow) -> Unit,
    onKeep: (PreviewRow) -> Unit,
) {
    val c = Sortora.colors
    // Keep showing the last row while the card animates away.
    val last = remember { arrayOfNulls<PreviewRow>(1) }
    SideEffect { if (editing != null) last[0] = editing }
    val shown = editing ?: last[0]

    AnimatedVisibility(visible = editing != null, enter = fadeIn(Motion.fade(240)), exit = fadeOut(Motion.fade(220))) {
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(c.scrim)
                    // Only while open: during the fade-out, taps fall through to the list.
                    .clickable(remember { MutableInteractionSource() }, indication = null, enabled = editing != null, onClick = onDismiss),
            )
            if (shown != null) {
                BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding().padding(Space.xs), contentAlignment = Alignment.BottomCenter) {
                    EditCard(
                        row = shown,
                        duplicates = duplicates,
                        mode = mode,
                        suggestions = suggestions,
                        scope = this@AnimatedVisibility,
                        onDismiss = onDismiss,
                        onSave = { name, folder -> onSave(shown, name, folder) },
                        onReset = { onReset(shown) },
                        onKeep = onKeep,
                        modifier = Modifier
                            .morph(editKey(shown.item.id), this@AnimatedVisibility)
                            .heightIn(max = maxHeight),
                    )
                }
            }
        }
    }
}

@Composable
private fun EditCard(
    row: PreviewRow,
    duplicates: List<PreviewRow>,
    mode: OperationMode,
    suggestions: List<String>,
    scope: AnimatedVisibilityScope,
    onDismiss: () -> Unit,
    onSave: (name: String, folder: String) -> Unit,
    onReset: () -> Unit,
    onKeep: (PreviewRow) -> Unit,
    modifier: Modifier = Modifier,
) {
    val c = Sortora.colors
    val extension = row.item.file.extension
    var name by remember(row.item.id) { mutableStateOf(row.plan.name.substringBeforeLast('.')) }
    var folder by remember(row.item.id) { mutableStateOf(row.plan.folder) }
    val analysis = row.item.analysis
    val duplicate = row.item.duplicate

    Column(
        modifier
            .fillMaxWidth()
            .softShadow(Radius.Xxl, Depth.High, c)
            .clip(Radius.Xxl)
            .background(c.raised)
            .hairline(Radius.Xxl, c.hairline)
            .clickable(remember { MutableInteractionSource() }, indication = null) {} // swallow taps
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Space.xl)
            .padding(top = Space.sm, bottom = Space.xl),
    ) {
        Box(Modifier.align(Alignment.CenterHorizontally).size(width = 36.dp, height = 4.dp).clip(Radius.Pill).background(c.inkTertiary.copy(alpha = 0.35f)))
        Spacer(Modifier.height(Space.lg))
        Row(verticalAlignment = Alignment.CenterVertically) {
            MediaThumbnail(row.item.file.uri, row.item.file.kind, size = 84.dp, radius = Radius.md, modifier = Modifier.sharedThing(thumbKey(row.item.id), scope))
            Spacer(Modifier.width(Space.md))
            Column {
                Text(row.item.file.displayName, style = MaterialTheme.typography.titleSmall, color = c.ink, maxLines = 2, overflow = TextOverflow.MiddleEllipsis)
                Text(
                    "${stringResource(row.plan.category.labelRes())} · ${Formatters.bytes(row.item.file.sizeBytes)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkSecondary,
                )
                row.plan.userCategoryMatch?.let { match ->
                    Text(
                        stringResource(R.string.preview_matched_folder, match.category.folderName, match.reason),
                        style = MaterialTheme.typography.bodySmall,
                        color = c.accent,
                    )
                }
            }
        }

        if (duplicate != null) {
            Spacer(Modifier.height(Space.lg))
            Eyebrow(stringResource(if (duplicate.exact) R.string.duplicate_section_exact else R.string.duplicate_section_similar))
            Spacer(Modifier.height(Space.xxs))
            Text(
                stringResource(if (duplicate.keeper) R.string.duplicate_this_is_best else R.string.duplicate_this_is_extra),
                style = MaterialTheme.typography.bodySmall,
                color = c.inkSecondary,
            )
            Spacer(Modifier.height(Space.sm))
            Row(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                duplicates.take(5).forEach { other -> MediaThumbnail(other.item.file.uri, other.item.file.kind, size = 52.dp, radius = Radius.sm) }
            }
            if (!duplicate.keeper) {
                Spacer(Modifier.height(Space.sm))
                SecondaryButton(stringResource(R.string.duplicate_keep_this), { onKeep(row) }, compact = true)
            }
        }

        Spacer(Modifier.height(Space.lg))
        SortoraTextField(
            value = name,
            onValueChange = { name = it },
            label = stringResource(R.string.preview_new_name),
            suffix = if (extension.isNotEmpty()) ".$extension" else null,
            modifier = Modifier.fillMaxWidth(),
        )
        if (mode != OperationMode.RENAME) {
            Spacer(Modifier.height(Space.sm))
            SortoraTextField(
                value = folder,
                onValueChange = { folder = it },
                label = stringResource(R.string.preview_folder),
                supporting = stringResource(R.string.preview_folder_hint),
                leading = Icons.Rounded.Folder,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.xs))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                suggestions.take(16).forEach { suggestion -> Pill(suggestion, folder == suggestion, { folder = suggestion }) }
            }
        }

        if (analysis != null) {
            Spacer(Modifier.height(Space.xl))
            Eyebrow(stringResource(R.string.preview_detected))
            Spacer(Modifier.height(Space.xs))
            if (analysis.labels.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    analysis.labels.take(8).forEach { label ->
                        Row(
                            Modifier.clip(Radius.Pill).background(c.sunken).padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(label.text, style = MaterialTheme.typography.labelMedium, color = c.ink)
                            Spacer(Modifier.width(5.dp))
                            Text(Formatters.percent(label.confidence), style = MaterialTheme.typography.labelMedium, color = c.inkTertiary)
                        }
                    }
                }
                Spacer(Modifier.height(Space.sm))
            }
            val details = buildList {
                add(stringResource(R.string.detail_confidence, Formatters.percent(analysis.confidence)))
                if (analysis.faces.count > 0) add(pluralStringResource(R.plurals.detail_faces, analysis.faces.count, analysis.faces.count))
                analysis.text.title?.let { add(stringResource(R.string.detail_text_title, it)) }
                analysis.sharpness?.let {
                    add(stringResource(if (SmartFilter.isBlurry(it)) R.string.detail_blurry else R.string.detail_sharp))
                }
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
            details.forEach { line -> Text(line, style = MaterialTheme.typography.bodySmall, color = c.inkSecondary, modifier = Modifier.padding(vertical = 1.dp)) }
        }

        Spacer(Modifier.height(Space.xl))
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (row.plan.nameEdited || row.plan.folderEdited || row.plan.patternEdited) {
                GhostButton(stringResource(R.string.preview_reset), onReset, color = c.inkSecondary)
            }
            Spacer(Modifier.weight(1f))
            SecondaryButton(stringResource(R.string.action_cancel), onDismiss, compact = true)
            Spacer(Modifier.width(Space.xs))
            PrimaryButton(stringResource(R.string.action_save), { onSave(name, folder) })
        }
    }
}

/** Shared frame for the bulk-action sheets: title, content, Cancel / Apply. */
@Composable
private fun ActionSheet(
    title: String,
    icon: ImageVector,
    applyEnabled: Boolean,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    val c = Sortora.colors
    SortoraSheet(onDismiss) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = Space.xl)
                .padding(bottom = Space.xl),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(36.dp).clip(Radius.Sm).background(c.accentSoft), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.width(Space.sm))
                Text(title, style = MaterialTheme.typography.headlineSmall, color = c.ink)
            }
            Spacer(Modifier.height(Space.lg))
            content()
            Spacer(Modifier.height(Space.xl))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
                SecondaryButton(stringResource(R.string.action_cancel), onDismiss, compact = true)
                Spacer(Modifier.width(Space.xs))
                PrimaryButton(stringResource(R.string.action_apply), onApply, enabled = applyEnabled)
            }
        }
    }
}

@Composable
internal fun SetFolderSheet(count: Int, suggestions: List<String>, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var folder by rememberSaveable { mutableStateOf("") }
    ActionSheet(
        title = pluralStringResource(R.plurals.bulk_set_folder_title, count, count),
        icon = Icons.Rounded.Folder,
        applyEnabled = folder.isNotBlank(),
        onDismiss = onDismiss,
        onApply = { onConfirm(folder) },
    ) {
        SortoraTextField(
            value = folder,
            onValueChange = { folder = it },
            label = stringResource(R.string.preview_folder),
            supporting = stringResource(R.string.preview_folder_hint),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.xs))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            suggestions.take(12).forEach { s -> Pill(s, folder == s, { folder = s }) }
        }
    }
}

/** Batch rename: a pattern with tokens, and a live preview of the first few results. */
@Composable
internal fun PatternSheet(
    count: Int,
    preview: (String) -> List<Pair<String, String>>,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val c = Sortora.colors
    var pattern by rememberSaveable { mutableStateOf("{date}_{label}_{counter}") }
    val examples = remember(pattern) { preview(pattern) }
    ActionSheet(
        title = pluralStringResource(R.plurals.bulk_rename_title, count, count),
        icon = Icons.Rounded.DriveFileRenameOutline,
        applyEnabled = pattern.isNotBlank(),
        onDismiss = onDismiss,
        onApply = { onConfirm(pattern) },
    ) {
        SortoraTextField(
            value = pattern,
            onValueChange = { pattern = it },
            label = stringResource(R.string.settings_custom_template),
            mono = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(Space.sm))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            FileNameGenerator.TOKENS.forEach { token ->
                Box(
                    Modifier
                        .clip(Radius.Pill)
                        .background(c.sunken)
                        .clickable { pattern += token }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                ) { Text(token, style = MonoStyle, color = c.ink) }
            }
        }
        Spacer(Modifier.height(Space.sm))
        Text(stringResource(R.string.bulk_rename_counter_hint), style = MaterialTheme.typography.bodySmall, color = c.inkSecondary)
        if (examples.isNotEmpty()) {
            Spacer(Modifier.height(Space.lg))
            Eyebrow(stringResource(R.string.settings_naming_example))
            Spacer(Modifier.height(Space.xs))
            Column(
                Modifier.fillMaxWidth().clip(Radius.Md).background(c.sunken).padding(Space.md),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                examples.forEach { (from, to) ->
                    Column {
                        Text(from, style = MaterialTheme.typography.bodySmall, color = c.inkTertiary, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                        Text(to, style = MonoStyle, color = c.ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        }
    }
}

/** Move and rename change the originals, so both need an explicit confirmation. */
@Composable
internal fun ConfirmApplyDialog(mode: OperationMode, count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val move = mode == OperationMode.MOVE
    SortoraDialog(
        onDismiss = onDismiss,
        icon = if (move) Icons.Rounded.DeleteForever else Icons.Rounded.DriveFileRenameOutline,
        title = pluralStringResource(if (move) R.plurals.preview_move_confirm_title else R.plurals.preview_rename_confirm_title, count, count),
        text = stringResource(if (move) R.string.preview_move_confirm_body else R.string.preview_rename_confirm_body),
        confirm = stringResource(if (move) R.string.preview_move_confirm_action else R.string.preview_rename_confirm_action),
        onConfirm = onConfirm,
        dismiss = stringResource(R.string.action_cancel),
        destructive = true,
    )
}
