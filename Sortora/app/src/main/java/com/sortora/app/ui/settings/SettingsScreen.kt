@file:OptIn(ExperimentalLayoutApi::class)

package com.sortora.app.ui.settings

import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDownward
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.FolderSpecial
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.sortora.app.BuildConfig
import com.sortora.app.R
import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.DateStyle
import com.sortora.app.domain.model.DuplicateHandling
import com.sortora.app.domain.model.NamingPreset
import com.sortora.app.domain.model.NamingSettings
import com.sortora.app.domain.model.OperationMode
import com.sortora.app.domain.model.Separator
import com.sortora.app.domain.model.ThemeMode
import com.sortora.app.domain.model.UserCategory
import com.sortora.app.domain.naming.FileNameGenerator
import com.sortora.app.domain.naming.NameInput
import com.sortora.app.ui.components.PrivacyBanner
import com.sortora.app.ui.components.icon
import com.sortora.app.ui.components.labelRes
import com.sortora.app.ui.design.Eyebrow
import com.sortora.app.ui.design.GhostButton
import com.sortora.app.ui.design.GlassTopBar
import com.sortora.app.ui.design.GroupDivider
import com.sortora.app.ui.design.IconBubble
import com.sortora.app.ui.design.ListRow
import com.sortora.app.ui.design.MonoStyle
import com.sortora.app.ui.design.Motion
import com.sortora.app.ui.design.PageTitle
import com.sortora.app.ui.design.PrimaryButton
import com.sortora.app.ui.design.Radius
import com.sortora.app.ui.design.ScreenScaffold
import com.sortora.app.ui.design.SecondaryButton
import com.sortora.app.ui.design.SectionLabel
import com.sortora.app.ui.design.SegmentedControl
import com.sortora.app.ui.design.SettingsGroup
import com.sortora.app.ui.design.Sortora
import com.sortora.app.ui.design.SortoraSheet
import com.sortora.app.ui.design.SortoraTextField
import com.sortora.app.ui.design.Space
import com.sortora.app.ui.design.ToggleRow
import com.sortora.app.ui.design.isScrolled
import com.sortora.app.ui.design.scrolledPast
import java.time.LocalDateTime
import java.time.ZoneId

@Composable
fun SettingsScreen(onBack: () -> Unit, onReplayOnboarding: () -> Unit, viewModel: SettingsViewModel = hiltViewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val settings = state.settings
    val c = Sortora.colors
    val resources = LocalResources.current
    val snackbar = remember { SnackbarHostState() }
    val pickDestination = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { viewModel.onDestinationPicked(it) }
    val list = rememberLazyListState()
    val scrolled by list.isScrolled()
    val pastTitle by list.scrolledPast(1)

    var editingCategory by remember { mutableStateOf<UserCategory?>(null) }
    var addingCategory by remember { mutableStateOf(false) }
    var renamingFolder by remember { mutableStateOf<ContentCategory?>(null) }

    LaunchedEffect(Unit) {
        viewModel.destinationErrors.collect { snackbar.showSnackbar(resources.getString(R.string.error_destination_not_writable)) }
    }

    ScreenScaffold(
        snackbar = snackbar,
        topBar = {
            GlassTopBar(
                title = stringResource(R.string.settings_title),
                showTitle = pastTitle,
                scrolled = scrolled,
                navigation = { IconBubble(Icons.AutoMirrored.Rounded.ArrowBack, stringResource(R.string.action_back), onBack) },
            )
        },
    ) { padding ->
        if (state.loading) return@ScreenScaffold
        LazyColumn(
            state = list,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + Space.xxl),
        ) {
            item { PageTitle(stringResource(R.string.settings_title), eyebrow = stringResource(R.string.settings_eyebrow)) }

            // Privacy
            item { SectionLabel(stringResource(R.string.settings_section_privacy)) }
            item { PrivacyBanner(Modifier.padding(horizontal = Space.md)) }
            item {
                Text(
                    stringResource(R.string.settings_privacy_details),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkSecondary,
                    modifier = Modifier.padding(horizontal = Space.gutter + Space.xxs, vertical = Space.sm),
                )
            }

            // Organising
            item { SectionLabel(stringResource(R.string.settings_section_organize)) }
            item {
                SettingsGroup {
                    Column(Modifier.padding(Space.md)) {
                        Text(stringResource(R.string.settings_default_action), style = MaterialTheme.typography.bodyLarge, color = c.ink)
                        Spacer(Modifier.height(Space.sm))
                        SegmentedControl(
                            options = listOf(OperationMode.COPY, OperationMode.MOVE, OperationMode.RENAME),
                            selected = settings.defaultMode,
                            onSelect = viewModel::setDefaultMode,
                            label = {
                                stringResource(
                                    when (it) {
                                        OperationMode.COPY -> R.string.mode_copy
                                        OperationMode.MOVE -> R.string.mode_move
                                        OperationMode.RENAME -> R.string.mode_rename
                                    },
                                )
                            },
                        )
                        Text(
                            stringResource(
                                when (settings.defaultMode) {
                                    OperationMode.COPY -> R.string.mode_copy_hint
                                    OperationMode.MOVE -> R.string.mode_move_hint
                                    OperationMode.RENAME -> R.string.mode_rename_hint
                                },
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = if (settings.defaultMode == OperationMode.COPY) c.inkSecondary else c.warning,
                            modifier = Modifier.padding(top = Space.sm),
                        )
                    }
                    GroupDivider()
                    ListRow(
                        title = stringResource(R.string.settings_output_folder),
                        subtitle = when {
                            settings.destinationLabel == null -> stringResource(R.string.home_destination_empty)
                            !state.destinationReady -> stringResource(R.string.home_destination_lost)
                            else -> settings.destinationLabel
                        },
                        leading = Icons.Rounded.Folder,
                        trailing = { GhostButton(stringResource(R.string.action_change), { pickDestination.launch(null) }) },
                    )
                    GroupDivider()
                    ToggleRow(stringResource(R.string.settings_group_by_year), stringResource(R.string.settings_group_by_year_hint), settings.groupByYear, viewModel::setGroupByYear)
                    GroupDivider()
                    ToggleRow(stringResource(R.string.settings_write_log), stringResource(R.string.settings_write_log_hint), settings.writeLogFile, viewModel::setWriteLogFile)
                }
            }

            // Naming
            item { SectionLabel(stringResource(R.string.settings_section_naming)) }
            item { NamingExamples(settings.naming) }
            item {
                SettingsGroup(Modifier.padding(top = Space.sm)) {
                    NamingPreset.entries.forEachIndexed { i, preset ->
                        if (i > 0) GroupDivider()
                        RadioRow(
                            title = stringResource(presetLabel(preset)),
                            subtitle = preset.template.takeIf { preset != NamingPreset.CUSTOM },
                            mono = true,
                            selected = settings.naming.preset == preset,
                            onClick = { viewModel.updateNaming { it.copy(preset = preset) } },
                        )
                    }
                    AnimatedVisibility(
                        settings.naming.preset == NamingPreset.CUSTOM,
                        enter = fadeIn(Motion.fade()) + expandVertically(Motion.gentle()),
                        exit = fadeOut(Motion.fade(160)) + shrinkVertically(Motion.gentle()),
                    ) {
                        CustomTemplateField(settings.naming.customTemplate) { value -> viewModel.updateNaming { it.copy(customTemplate = value) } }
                    }
                }
            }
            item {
                SettingsGroup(Modifier.padding(top = Space.sm)) {
                    ChoiceRow(
                        title = stringResource(R.string.settings_date_format),
                        options = listOf(DateStyle.ISO to "2025-10-12", DateStyle.COMPACT to "20251012", DateStyle.NONE to stringResource(R.string.settings_none)),
                        selected = settings.naming.dateStyle,
                        onSelect = { style -> viewModel.updateNaming { it.copy(dateStyle = style) } },
                    )
                    GroupDivider()
                    ChoiceRow(
                        title = stringResource(R.string.settings_separator),
                        options = listOf(Separator.UNDERSCORE to "a_b", Separator.HYPHEN to "a-b"),
                        selected = settings.naming.separator,
                        onSelect = { sep -> viewModel.updateNaming { it.copy(separator = sep) } },
                    )
                    GroupDivider()
                    ToggleRow(stringResource(R.string.settings_lowercase), null, settings.naming.lowercase) { v -> viewModel.updateNaming { it.copy(lowercase = v) } }
                    GroupDivider()
                    ToggleRow(stringResource(R.string.settings_screenshot_titles), stringResource(R.string.settings_screenshot_titles_hint), settings.naming.screenshotTitles) { v ->
                        viewModel.updateNaming { it.copy(screenshotTitles = v) }
                    }
                    GroupDivider()
                    SliderRow(
                        title = stringResource(R.string.settings_max_labels, settings.naming.maxLabels),
                        value = settings.naming.maxLabels,
                        range = 0..4,
                        onChange = { v -> viewModel.updateNaming { it.copy(maxLabels = v) } },
                    )
                }
            }

            // Custom categories
            item { SectionLabel(stringResource(R.string.settings_section_categories)) }
            item {
                Text(
                    stringResource(R.string.settings_categories_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = c.inkSecondary,
                    modifier = Modifier.padding(start = Space.gutter + Space.xxs, end = Space.gutter, bottom = Space.sm),
                )
            }
            item {
                SettingsGroup {
                    settings.customCategories.forEachIndexed { index, category ->
                        if (index > 0) GroupDivider()
                        ListRow(
                            title = category.folderName,
                            subtitle = category.keywords.joinToString(", ").ifEmpty { stringResource(R.string.settings_category_no_keywords) },
                            leading = Icons.Rounded.FolderSpecial,
                            onClick = { editingCategory = category },
                            trailing = {
                                Row {
                                    IconBubble(Icons.Rounded.ArrowUpward, stringResource(R.string.action_move_up), { viewModel.moveCategory(category.id, up = true) }, filled = false, enabled = index > 0)
                                    IconBubble(
                                        Icons.Rounded.ArrowDownward, stringResource(R.string.action_move_down), { viewModel.moveCategory(category.id, up = false) },
                                        filled = false, enabled = index < settings.customCategories.lastIndex,
                                    )
                                    IconBubble(Icons.Rounded.Delete, stringResource(R.string.action_delete), { viewModel.deleteCategory(category.id) }, filled = false, tint = c.danger)
                                }
                            },
                        )
                    }
                    if (settings.customCategories.isNotEmpty()) GroupDivider()
                    ListRow(
                        title = stringResource(R.string.settings_add_category),
                        leading = Icons.Rounded.Add,
                        onClick = { addingCategory = true },
                        titleStyle = MaterialTheme.typography.titleSmall,
                    )
                }
            }

            // Built-in folder names
            item { SectionLabel(stringResource(R.string.settings_section_folders)) }
            item {
                SettingsGroup {
                    ContentCategory.entries.forEachIndexed { i, category ->
                        if (i > 0) GroupDivider(inset = 60.dp)
                        ListRow(
                            title = stringResource(category.labelRes()),
                            subtitle = settings.folderFor(category),
                            leading = category.icon(),
                            onClick = { renamingFolder = category },
                        )
                    }
                }
            }
            if (settings.folderNames.isNotEmpty()) {
                item {
                    GhostButton(stringResource(R.string.settings_reset_folders), viewModel::resetFolderNames, Modifier.padding(horizontal = Space.sm, vertical = Space.xxs))
                }
            }

            // Analysis
            item { SectionLabel(stringResource(R.string.settings_section_analysis)) }
            item {
                SettingsGroup {
                    ToggleRow(stringResource(R.string.settings_ocr), stringResource(R.string.settings_ocr_hint), settings.analysis.enableOcr) { v ->
                        viewModel.updateAnalysis { it.copy(enableOcr = v) }
                    }
                    GroupDivider()
                    SliderRow(
                        title = stringResource(R.string.settings_video_frames, settings.analysis.videoFrameCount),
                        subtitle = stringResource(R.string.settings_video_frames_hint),
                        value = settings.analysis.videoFrameCount,
                        range = 3..10,
                        onChange = { v -> viewModel.updateAnalysis { it.copy(videoFrameCount = v) } },
                    )
                    GroupDivider()
                    ToggleRow(stringResource(R.string.settings_include_subfolders), null, settings.analysis.includeSubfolders) { v ->
                        viewModel.updateAnalysis { it.copy(includeSubfolders = v) }
                    }
                    GroupDivider()
                    ToggleRow(stringResource(R.string.settings_face_grouping), stringResource(R.string.settings_face_grouping_hint), settings.analysis.faceGrouping) { v ->
                        viewModel.updateAnalysis { it.copy(faceGrouping = v) }
                    }
                }
            }

            // Duplicates
            item { SectionLabel(stringResource(R.string.settings_section_duplicates)) }
            item {
                SettingsGroup {
                    ToggleRow(stringResource(R.string.settings_detect_duplicates), stringResource(R.string.settings_detect_duplicates_hint), settings.analysis.detectDuplicates) { v ->
                        viewModel.updateAnalysis { it.copy(detectDuplicates = v) }
                    }
                    AnimatedVisibility(
                        settings.analysis.detectDuplicates,
                        enter = fadeIn(Motion.fade()) + expandVertically(Motion.gentle()),
                        exit = fadeOut(Motion.fade(160)) + shrinkVertically(Motion.gentle()),
                    ) {
                        Column {
                            DuplicateHandling.entries.forEach { handling ->
                                GroupDivider()
                                RadioRow(
                                    title = stringResource(handling.labelRes()),
                                    subtitle = stringResource(handling.hintRes()),
                                    selected = settings.duplicateHandling == handling,
                                    onClick = { viewModel.setDuplicateHandling(handling) },
                                )
                            }
                        }
                    }
                }
            }

            // Appearance
            item { SectionLabel(stringResource(R.string.settings_section_appearance)) }
            item {
                SettingsGroup {
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
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        GroupDivider()
                        ToggleRow(stringResource(R.string.settings_dynamic_color), null, settings.dynamicColor, viewModel::setDynamicColor)
                    }
                }
            }

            // About
            item { SectionLabel(stringResource(R.string.settings_section_about)) }
            item {
                SettingsGroup {
                    ListRow(title = stringResource(R.string.app_name), subtitle = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME), leading = Icons.Rounded.Info)
                    GroupDivider(inset = 60.dp)
                    ListRow(title = stringResource(R.string.settings_replay_intro), leading = Icons.Rounded.Replay, onClick = onReplayOnboarding)
                }
            }
            item {
                Column(Modifier.padding(horizontal = Space.gutter + Space.xxs, vertical = Space.md)) {
                    Eyebrow(stringResource(R.string.settings_section_licenses))
                    Spacer(Modifier.height(Space.xxs))
                    Text(stringResource(R.string.settings_licenses_body), style = MaterialTheme.typography.bodySmall, color = c.inkTertiary)
                }
            }
        }
    }

    if (addingCategory || editingCategory != null) {
        CategorySheet(
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
        RenameFolderSheet(
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

private fun DuplicateHandling.labelRes(): Int = when (this) {
    DuplicateHandling.SKIP_EXTRAS -> R.string.duplicates_skip
    DuplicateHandling.SEPARATE_FOLDER -> R.string.duplicates_folder
    DuplicateHandling.KEEP_ALL -> R.string.duplicates_keep
}

private fun DuplicateHandling.hintRes(): Int = when (this) {
    DuplicateHandling.SKIP_EXTRAS -> R.string.duplicates_skip_hint
    DuplicateHandling.SEPARATE_FOLDER -> R.string.duplicates_folder_hint
    DuplicateHandling.KEEP_ALL -> R.string.duplicates_keep_hint
}

private fun presetLabel(preset: NamingPreset): Int = when (preset) {
    NamingPreset.DATE_CATEGORY_LABELS -> R.string.preset_date_category_labels
    NamingPreset.DATE_TIME_CATEGORY_LABELS -> R.string.preset_date_time_category_labels
    NamingPreset.CATEGORY_LABELS -> R.string.preset_category_labels
    NamingPreset.DATE_LABELS -> R.string.preset_date_labels
    NamingPreset.ORIGINAL_LABELS -> R.string.preset_original_labels
    NamingPreset.LABEL_COUNTER -> R.string.preset_label_counter
    NamingPreset.CUSTOM -> R.string.preset_custom
}

/** Live examples so users see exactly what their settings produce. */
@Composable
private fun NamingExamples(naming: NamingSettings) {
    val c = Sortora.colors
    val examples = remember(naming) {
        val generator = FileNameGenerator()
        val date = LocalDateTime.of(2025, 10, 12, 14, 30, 15).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        listOf(
            NameInput("VID_20251012_143015", "mp4", date, ContentCategory.TALKING_HEAD, listOf("person", "desk"), null),
            NameInput("IMG_4821", "jpg", date, ContentCategory.PET, listOf("dog", "beach"), null, counter = 7),
            NameInput("Screenshot_20251012-143015", "png", date, ContentCategory.SCREENSHOT, emptyList(), "Settings"),
        ).map { generator.generate(it, naming) }
    }
    Column(
        Modifier
            .padding(horizontal = Space.md)
            .fillMaxWidth()
            .clip(Radius.Lg)
            .background(c.sunken)
            .padding(Space.md),
        verticalArrangement = Arrangement.spacedBy(Space.xxs),
    ) {
        Eyebrow(stringResource(R.string.settings_naming_example))
        Spacer(Modifier.height(Space.xxs))
        examples.forEach { Text(it, style = MonoStyle, color = c.ink) }
    }
}

@Composable
private fun CustomTemplateField(value: String, onChange: (String) -> Unit) {
    // Local state is the source of truth while typing; saving each keystroke must not reset it.
    var text by remember { mutableStateOf(value) }
    Column(Modifier.padding(Space.md)) {
        SortoraTextField(
            value = text,
            onValueChange = { text = it; onChange(it) },
            label = stringResource(R.string.settings_custom_template),
            supporting = stringResource(R.string.settings_custom_template_hint),
            mono = true,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun RadioRow(title: String, subtitle: String?, selected: Boolean, onClick: () -> Unit, mono: Boolean = false) {
    val c = Sortora.colors
    Row(
        Modifier
            .fillMaxWidth()
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = c.ink)
            if (subtitle != null) Text(subtitle, style = if (mono) MonoStyle.copy(fontSize = MaterialTheme.typography.bodySmall.fontSize) else MaterialTheme.typography.bodySmall, color = c.inkSecondary)
        }
        AnimatedVisibility(selected, enter = scaleIn(Motion.bouncy()) + fadeIn(), exit = scaleOut() + fadeOut()) {
            Icon(Icons.Rounded.Check, contentDescription = null, tint = c.accent, modifier = Modifier.padding(start = Space.sm).size(20.dp))
        }
    }
}

@Composable
private fun SliderRow(title: String, value: Int, range: IntRange, onChange: (Int) -> Unit, subtitle: String? = null) {
    val c = Sortora.colors
    var position by remember(value) { mutableFloatStateOf(value.toFloat()) }
    Column(Modifier.padding(horizontal = Space.md, vertical = Space.sm)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = c.ink)
        subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = c.inkSecondary) }
        Slider(
            value = position,
            onValueChange = { position = it },
            onValueChangeFinished = { onChange(position.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            steps = (range.last - range.first - 1).coerceAtLeast(0),
            colors = SliderDefaults.colors(
                thumbColor = c.accent,
                activeTrackColor = c.accent,
                inactiveTrackColor = c.sunken,
                activeTickColor = c.onAccent.copy(alpha = 0.6f),
                inactiveTickColor = c.inkTertiary.copy(alpha = 0.6f),
            ),
        )
    }
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    val c = Sortora.colors
    Column(Modifier.padding(Space.md)) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = c.ink)
        Spacer(Modifier.height(Space.sm))
        SegmentedControl(
            options = options.map { it.first },
            selected = selected,
            onSelect = onSelect,
            label = { value -> options.first { it.first == value }.second },
        )
    }
}

@Composable
private fun SheetActions(saveEnabled: Boolean, onDismiss: () -> Unit, onSave: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(top = Space.xl), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
        SecondaryButton(stringResource(R.string.action_cancel), onDismiss, compact = true)
        Spacer(Modifier.width(Space.xs))
        PrimaryButton(stringResource(R.string.action_save), onSave, enabled = saveEnabled)
    }
}

@Composable
private fun CategorySheet(initial: UserCategory?, onDismiss: () -> Unit, onSave: (String, String) -> Unit) {
    val c = Sortora.colors
    var name by remember { mutableStateOf(initial?.folderName.orEmpty()) }
    var keywords by remember { mutableStateOf(initial?.keywords?.joinToString(", ").orEmpty()) }
    SortoraSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = Space.xl).padding(bottom = Space.xl)) {
            Text(stringResource(if (initial == null) R.string.settings_add_category else R.string.settings_edit_category), style = MaterialTheme.typography.headlineSmall, color = c.ink)
            Spacer(Modifier.height(Space.lg))
            SortoraTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.settings_category_folder),
                placeholder = stringResource(R.string.settings_category_folder_placeholder),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(Space.sm))
            SortoraTextField(
                value = keywords,
                onValueChange = { keywords = it },
                label = stringResource(R.string.settings_category_keywords),
                placeholder = stringResource(R.string.settings_category_keywords_placeholder),
                supporting = stringResource(R.string.settings_category_keywords_hint),
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
            SheetActions(name.isNotBlank(), onDismiss) { onSave(name, keywords) }
        }
    }
}

@Composable
private fun RenameFolderSheet(category: ContentCategory, current: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    val c = Sortora.colors
    var name by remember { mutableStateOf(current) }
    SortoraSheet(onDismiss) {
        Column(Modifier.fillMaxWidth().padding(horizontal = Space.xl).padding(bottom = Space.xl)) {
            Text(stringResource(R.string.settings_rename_folder, stringResource(category.labelRes())), style = MaterialTheme.typography.headlineSmall, color = c.ink)
            Spacer(Modifier.height(Space.lg))
            SortoraTextField(
                value = name,
                onValueChange = { name = it },
                label = stringResource(R.string.preview_folder),
                supporting = stringResource(R.string.settings_rename_folder_hint, category.defaultFolder),
                modifier = Modifier.fillMaxWidth(),
            )
            SheetActions(true, onDismiss) { onSave(name) }
        }
    }
}
