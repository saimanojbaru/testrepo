@file:OptIn(ExperimentalMaterial3Api::class)

package com.sortora.app.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.FolderSpecial
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortora.app.BuildConfig
import com.sortora.app.R
import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.DateStyle
import com.sortora.app.domain.model.NamingPreset
import com.sortora.app.domain.model.NamingSettings
import com.sortora.app.domain.model.OperationMode
import com.sortora.app.domain.model.Separator
import com.sortora.app.domain.model.ThemeMode
import com.sortora.app.domain.model.UserCategory
import com.sortora.app.domain.naming.FileNameGenerator
import com.sortora.app.domain.naming.NameInput
import com.sortora.app.ui.components.PrivacyBanner
import com.sortora.app.ui.components.SectionHeader
import com.sortora.app.ui.components.labelRes
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun SettingsScreen(onBack: () -> Unit, onReplayOnboarding: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val pickDestination = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { viewModel.onDestinationPicked(it) }

    var editingCategory by remember { mutableStateOf<UserCategory?>(null) }
    var addingCategory by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<ContentCategory?>(null) }

    LaunchedEffect(Unit) {
        viewModel.destinationErrors.collect { snackbar.showSnackbar(resources.getString(R.string.error_destination_not_writable)) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.action_back)) } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (state.loading) return@Scaffold
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)) {
            // Privacy
            item { SectionHeader(stringResource(R.string.settings_section_privacy)) }
            item { PrivacyBanner(Modifier.padding(horizontal = 16.dp)) }
            item {
                Text(
                    stringResource(R.string.settings_privacy_details),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                )
            }

            // Organising
            item { SectionHeader(stringResource(R.string.settings_section_organize)) }
            item {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    Text(stringResource(R.string.settings_default_action), style = MaterialTheme.typography.bodyLarge)
                    Spacer(Modifier.height(8.dp))
                    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                        listOf(OperationMode.COPY to R.string.mode_copy, OperationMode.MOVE to R.string.mode_move).forEachIndexed { i, (mode, label) ->
                            SegmentedButton(
                                selected = settings.defaultMode == mode,
                                onClick = { viewModel.setDefaultMode(mode) },
                                shape = SegmentedButtonDefaults.itemShape(i, 2),
                            ) { Text(stringResource(label)) }
                        }
                    }
                    Text(
                        stringResource(if (settings.defaultMode == OperationMode.COPY) R.string.mode_copy_hint else R.string.mode_move_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (settings.defaultMode == OperationMode.MOVE) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 4.dp),
                    )
                }
            }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.settings_output_folder)) },
                    supportingContent = {
                        Text(
                            when {
                                settings.destinationLabel == null -> stringResource(R.string.home_destination_empty)
                                !state.destinationReady -> stringResource(R.string.home_destination_lost)
                                else -> settings.destinationLabel
                            },
                        )
                    },
                    leadingContent = { Icon(Icons.Outlined.Folder, contentDescription = null) },
                    trailingContent = { TextButton(onClick = { pickDestination.launch(null) }) { Text(stringResource(R.string.action_change)) } },
                )
            }
            item {
                SwitchRow(stringResource(R.string.settings_group_by_year), stringResource(R.string.settings_group_by_year_hint), settings.groupByYear, viewModel::setGroupByYear)
            }
            item {
                SwitchRow(stringResource(R.string.settings_write_log), stringResource(R.string.settings_write_log_hint), settings.writeLogFile, viewModel::setWriteLogFile)
            }

            // Naming
            item { SectionHeader(stringResource(R.string.settings_section_naming)) }
            item { NamingExamples(settings.naming) }
            items(NamingPreset.entries) { preset ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .selectable(selected = settings.naming.preset == preset, role = Role.RadioButton) {
                            viewModel.updateNaming { it.copy(preset = preset) }
                        }
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(selected = settings.naming.preset == preset, onClick = null)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(stringResource(presetLabel(preset)), style = MaterialTheme.typography.bodyLarge)
                        if (preset != NamingPreset.CUSTOM) {
                            Text(preset.template, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (settings.naming.preset == NamingPreset.CUSTOM) {
                item { CustomTemplateField(settings.naming.customTemplate) { value -> viewModel.updateNaming { it.copy(customTemplate = value) } } }
            }
            item {
                ChoiceRow(
                    title = stringResource(R.string.settings_date_format),
                    options = listOf(DateStyle.ISO to "2025-10-12", DateStyle.COMPACT to "20251012", DateStyle.NONE to stringResource(R.string.settings_none)),
                    selected = settings.naming.dateStyle,
                    onSelect = { style -> viewModel.updateNaming { it.copy(dateStyle = style) } },
                )
            }
            item {
                ChoiceRow(
                    title = stringResource(R.string.settings_separator),
                    options = listOf(Separator.UNDERSCORE to "a_b", Separator.HYPHEN to "a-b"),
                    selected = settings.naming.separator,
                    onSelect = { sep -> viewModel.updateNaming { it.copy(separator = sep) } },
                )
            }
            item {
                SwitchRow(stringResource(R.string.settings_lowercase), null, settings.naming.lowercase) { v -> viewModel.updateNaming { it.copy(lowercase = v) } }
            }
            item {
                SwitchRow(stringResource(R.string.settings_screenshot_titles), stringResource(R.string.settings_screenshot_titles_hint), settings.naming.screenshotTitles) { v ->
                    viewModel.updateNaming { it.copy(screenshotTitles = v) }
                }
            }
            item {
                SliderRow(
                    title = stringResource(R.string.settings_max_labels, settings.naming.maxLabels),
                    value = settings.naming.maxLabels,
                    range = 0..4,
                    onChange = { v -> viewModel.updateNaming { it.copy(maxLabels = v) } },
                )
            }

            // Custom categories
            item { SectionHeader(stringResource(R.string.settings_section_categories)) }
            item {
                Text(
                    stringResource(R.string.settings_categories_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 16.dp),
                )
            }
            items(settings.customCategories, key = { it.id }) { category ->
                val index = settings.customCategories.indexOf(category)
                ListItem(
                    modifier = Modifier.clickable { editingCategory = category },
                    headlineContent = { Text(category.folderName) },
                    supportingContent = {
                        Text(category.keywords.joinToString(", ").ifEmpty { stringResource(R.string.settings_category_no_keywords) }, maxLines = 2)
                    },
                    leadingContent = { Icon(Icons.Outlined.FolderSpecial, contentDescription = null) },
                    trailingContent = {
                        Row {
                            IconButton(onClick = { viewModel.moveCategory(category.id, up = true) }, enabled = index > 0) {
                                Icon(Icons.Outlined.ArrowUpward, stringResource(R.string.action_move_up))
                            }
                            IconButton(onClick = { viewModel.moveCategory(category.id, up = false) }, enabled = index < settings.customCategories.lastIndex) {
                                Icon(Icons.Outlined.ArrowDownward, stringResource(R.string.action_move_down))
                            }
                            IconButton(onClick = { viewModel.deleteCategory(category.id) }) { Icon(Icons.Outlined.Delete, stringResource(R.string.action_delete)) }
                        }
                    },
                )
            }
            item {
                TextButton(onClick = { addingCategory = true }, modifier = Modifier.padding(horizontal = 8.dp)) {
                    Icon(Icons.Outlined.Add, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_add_category))
                }
            }

            // Built-in folder names
            item { SectionHeader(stringResource(R.string.settings_section_folders)) }
            items(ContentCategory.entries) { category ->
                ListItem(
                    modifier = Modifier.clickable { renamingFolder = category },
                    headlineContent = { Text(stringResource(category.labelRes())) },
                    supportingContent = { Text(settings.folderFor(category)) },
                )
            }
            if (settings.folderNames.isNotEmpty()) {
                item {
                    TextButton(onClick = viewModel::resetFolderNames, modifier = Modifier.padding(horizontal = 8.dp)) {
                        Text(stringResource(R.string.settings_reset_folders))
                    }
                }
            }

            // Analysis
            item { SectionHeader(stringResource(R.string.settings_section_analysis)) }
            item {
                SwitchRow(stringResource(R.string.settings_ocr), stringResource(R.string.settings_ocr_hint), settings.analysis.enableOcr) { v ->
                    viewModel.updateAnalysis { it.copy(enableOcr = v) }
                }
            }
            item {
                SliderRow(
                    title = stringResource(R.string.settings_video_frames, settings.analysis.videoFrameCount),
                    subtitle = stringResource(R.string.settings_video_frames_hint),
                    value = settings.analysis.videoFrameCount,
                    range = 3..10,
                    onChange = { v -> viewModel.updateAnalysis { it.copy(videoFrameCount = v) } },
                )
            }
            item {
                SwitchRow(stringResource(R.string.settings_include_subfolders), null, settings.analysis.includeSubfolders) { v ->
                    viewModel.updateAnalysis { it.copy(includeSubfolders = v) }
                }
            }

            // Appearance
            item { SectionHeader(stringResource(R.string.settings_section_appearance)) }
            item {
                ChoiceRow(
                    title = stringResource(R.string.settings_theme),
                    options = listOf(
                        ThemeMode.SYSTEM to stringResource(R.string.theme_system),
                        ThemeMode.LIGHT to stringResource(R.string.theme_light),
                        ThemeMode.DARK to stringResource(R.string.theme_dark),
                    ),
                    selected = settings.themeMode,
                    onSelect = viewModel::setThemeMode,
                )
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                item { SwitchRow(stringResource(R.string.settings_dynamic_color), null, settings.dynamicColor, viewModel::setDynamicColor) }
            }

            // About
            item { SectionHeader(stringResource(R.string.settings_section_about)) }
            item {
                ListItem(
                    headlineContent = { Text(stringResource(R.string.app_name)) },
                    supportingContent = { Text(stringResource(R.string.settings_version, BuildConfig.VERSION_NAME)) },
                )
            }
            item {
                TextButton(onClick = onReplayOnboarding, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.settings_replay_intro)) }
            }
        }
    }

    if (addingCategory || editingCategory != null) {
        CategoryDialog(
            initial = editingCategory,
            onDismiss = { addingCategory = false; editingCategory = null },
            onSave = { name, keywords ->
                viewModel.saveCategory(editingCategory?.id, name, keywords)
                addingCategory = false
                editingCategory = null
            },
        )
    }

    renamingFolder?.let { category ->
        RenameFolderDialog(
            category = category,
            current = settings.folderFor(category),
            onDismiss = { renamingFolder = null },
            onSave = { name ->
                viewModel.setFolderName(category, name)
                renamingFolder = null
            },
        )
    }
}

private fun presetLabel(preset: NamingPreset): Int = when (preset) {
    NamingPreset.DATE_CATEGORY_LABELS -> R.string.preset_date_category_labels
    NamingPreset.DATE_TIME_CATEGORY_LABELS -> R.string.preset_date_time_category_labels
    NamingPreset.CATEGORY_LABELS -> R.string.preset_category_labels
    NamingPreset.DATE_LABELS -> R.string.preset_date_labels
    NamingPreset.ORIGINAL_LABELS -> R.string.preset_original_labels
    NamingPreset.CUSTOM -> R.string.preset_custom
}

/** Live examples so users see exactly what their settings produce. */
@Composable
private fun NamingExamples(naming: NamingSettings) {
    val examples = remember(naming) {
        val generator = FileNameGenerator()
        val date = LocalDateTime.of(2025, 10, 12, 14, 30, 15).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        listOf(
            NameInput("VID_20251012_143015", "mp4", date, ContentCategory.TALKING_HEAD, listOf("person", "desk"), null),
            NameInput("IMG_4821", "jpg", date, ContentCategory.PET, listOf("dog", "beach"), null),
            NameInput("Screenshot_20251012-143015", "png", date, ContentCategory.SCREENSHOT, emptyList(), "Settings"),
        ).map { generator.generate(it, naming) }
    }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerHigh),
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(stringResource(R.string.settings_naming_example), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            examples.forEach { Text(it, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}

@Composable
private fun CustomTemplateField(value: String, onChange: (String) -> Unit) {
    // Local state is the source of truth while typing; saving each keystroke must not reset it.
    var text by remember { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = { text = it; onChange(it) },
        label = { Text(stringResource(R.string.settings_custom_template)) },
        supportingText = { Text(stringResource(R.string.settings_custom_template_hint)) },
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    )
}

@Composable
private fun SwitchRow(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    ListItem(
        modifier = Modifier.clickable { onChange(!checked) },
        headlineContent = { Text(title) },
        supportingContent = subtitle?.let { { Text(it) } },
        trailingContent = { Switch(checked = checked, onCheckedChange = onChange) },
    )
}

@Composable
private fun SliderRow(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit, subtitle: String? = null) {
    var position by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        Slider(
            value = position,
            onValueChange = { position = it },
            onValueChangeFinished = { onChange(position.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
        )
    }
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            options.forEachIndexed { index, (value, label) ->
                SegmentedButton(
                    selected = value == selected,
                    onClick = { onSelect(value) },
                    shape = SegmentedButtonDefaults.itemShape(index, options.size),
                ) { Text(label, maxLines = 1) }
            }
        }
    }
}

@Composable
private fun CategoryDialog(initial: UserCategory?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember { mutableStateOf(initial?.folderName.orEmpty()) }
    var keywords by remember { mutableStateOf(initial?.keywords?.joinToString(", ").orEmpty()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(if (initial == null) R.string.settings_add_category else R.string.settings_edit_category)) },
        text = {
            Column {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.settings_category_folder)) },
                    placeholder = { Text(stringResource(R.string.settings_category_folder_placeholder)) },
                    singleLine = true,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = keywords,
                    onValueChange = { keywords = it },
                    label = { Text(stringResource(R.string.settings_category_keywords)) },
                    placeholder = { Text(stringResource(R.string.settings_category_keywords_placeholder)) },
                    supportingText = { Text(stringResource(R.string.settings_category_keywords_hint)) },
                    minLines = 2,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, keywords) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@Composable
private fun RenameFolderDialog(category: ContentCategory, current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var name by remember { mutableStateOf(current) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.settings_rename_folder, stringResource(category.labelRes()))) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.preview_folder)) },
                supportingText = { Text(stringResource(R.string.settings_rename_folder_hint, category.defaultFolder)) },
                singleLine = true,
            )
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text(stringResource(R.string.action_save)) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}
