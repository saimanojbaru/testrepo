@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package com.sortora.app.ui.preview

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.DriveFileRenameOutline
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
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

@Composable
internal fun EditSheet(
    row: PreviewRow,
    duplicates: List<PreviewRow>,
    mode: OperationMode,
    suggestions: List<String>,
    onDismiss: () -> Unit,
    onSave: (name: String, folder: String) -> Unit,
    onReset: () -> Unit,
    onKeep: (PreviewRow) -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val extension = row.item.file.extension
    var name by remember(row.item.id) { mutableStateOf(row.plan.name.substringBeforeLast('.')) }
    var folder by remember(row.item.id) { mutableStateOf(row.plan.folder) }
    val analysis = row.item.analysis
    val duplicate = row.item.duplicate

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

            if (duplicate != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    stringResource(if (duplicate.exact) R.string.duplicate_section_exact else R.string.duplicate_section_similar),
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    stringResource(if (duplicate.keeper) R.string.duplicate_this_is_best else R.string.duplicate_this_is_extra),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    duplicates.take(5).forEach { other -> MediaThumbnail(other.item.file.uri, other.item.file.kind, size = 56.dp) }
                }
                if (!duplicate.keeper) {
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { onKeep(row) }) { Text(stringResource(R.string.duplicate_keep_this)) }
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
            if (mode != OperationMode.RENAME) {
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
                details.forEach { line ->
                    Text(line, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                if (row.plan.nameEdited || row.plan.folderEdited || row.plan.patternEdited) {
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

/** Shared frame for the bulk-action sheets: title, content, Cancel / Apply. */
@Composable
private fun ActionSheet(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    applyEnabled: Boolean,
    onDismiss: () -> Unit,
    onApply: () -> Unit,
    content: @Composable () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(12.dp))
                Text(title, style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(16.dp))
            content()
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
                Spacer(Modifier.width(8.dp))
                Button(onClick = onApply, enabled = applyEnabled) { Text(stringResource(R.string.action_apply)) }
            }
        }
    }
}

@Composable
internal fun SetFolderSheet(count: Int, suggestions: List<String>, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var folder by rememberSaveable { mutableStateOf("") }
    ActionSheet(
        title = pluralStringResource(R.plurals.bulk_set_folder_title, count, count),
        icon = Icons.Outlined.Folder,
        applyEnabled = folder.isNotBlank(),
        onDismiss = onDismiss,
        onApply = { onConfirm(folder) },
    ) {
        OutlinedTextField(
            value = folder,
            onValueChange = { folder = it },
            label = { Text(stringResource(R.string.preview_folder)) },
            supportingText = { Text(stringResource(R.string.preview_folder_hint)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.take(12).forEach { s -> FilterChip(selected = folder == s, onClick = { folder = s }, label = { Text(s) }) }
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
    var pattern by rememberSaveable { mutableStateOf("{date}_{label}_{counter}") }
    val examples = remember(pattern) { preview(pattern) }
    ActionSheet(
        title = pluralStringResource(R.plurals.bulk_rename_title, count, count),
        icon = Icons.Outlined.DriveFileRenameOutline,
        applyEnabled = pattern.isNotBlank(),
        onDismiss = onDismiss,
        onApply = { onConfirm(pattern) },
    ) {
        OutlinedTextField(
            value = pattern,
            onValueChange = { pattern = it },
            label = { Text(stringResource(R.string.settings_custom_template)) },
            singleLine = true,
            textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            modifier = Modifier.fillMaxWidth(),
        )
        Spacer(Modifier.height(8.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FileNameGenerator.TOKENS.forEach { token ->
                AssistChip(onClick = { pattern += token }, label = { Text(token, fontFamily = FontFamily.Monospace) })
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.bulk_rename_counter_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (examples.isNotEmpty()) {
            Spacer(Modifier.height(12.dp))
            Text(stringResource(R.string.settings_naming_example), style = MaterialTheme.typography.labelMedium)
            examples.forEach { (from, to) ->
                Text(from, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.MiddleEllipsis)
                Text("→ $to", style = MaterialTheme.typography.bodyMedium, fontFamily = FontFamily.Monospace, maxLines = 2, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

/** Move and rename change the originals, so both need an explicit confirmation. */
@Composable
internal fun ConfirmApplyDialog(mode: OperationMode, count: Int, onDismiss: () -> Unit, onConfirm: () -> Unit) {
    val move = mode == OperationMode.MOVE
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                if (move) Icons.Outlined.DeleteForever else Icons.Outlined.DriveFileRenameOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
            )
        },
        title = {
            Text(pluralStringResource(if (move) R.plurals.preview_move_confirm_title else R.plurals.preview_rename_confirm_title, count, count))
        },
        text = { Text(stringResource(if (move) R.string.preview_move_confirm_body else R.string.preview_rename_confirm_body)) },
        confirmButton = {
            TextButton(onClick = onConfirm, colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)) {
                Text(stringResource(if (move) R.string.preview_move_confirm_action else R.string.preview_rename_confirm_action))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
