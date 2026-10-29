package com.sortora.app.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.sortora.app.domain.model.AnalysisOptions
import com.sortora.app.domain.model.AppSettings
import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.DateStyle
import com.sortora.app.domain.model.DuplicateHandling
import com.sortora.app.domain.model.NamingPreset
import com.sortora.app.domain.model.NamingSettings
import com.sortora.app.domain.model.OperationMode
import com.sortora.app.domain.model.Separator
import com.sortora.app.domain.model.ThemeMode
import com.sortora.app.domain.model.UserCategory
import com.sortora.app.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** Settings stored with Jetpack DataStore (Preferences). Complex values are kept as JSON strings. */
@Singleton
class DataStoreSettingsRepository @Inject constructor(
    private val store: DataStore<Preferences>,
    private val json: Json,
) : SettingsRepository {

    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val theme = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val defaultMode = stringPreferencesKey("default_mode")
        val destinationUri = stringPreferencesKey("destination_uri")
        val destinationLabel = stringPreferencesKey("destination_label")
        val namingPreset = stringPreferencesKey("naming_preset")
        val customTemplate = stringPreferencesKey("custom_template")
        val dateStyle = stringPreferencesKey("date_style")
        val separator = stringPreferencesKey("separator")
        val lowercase = booleanPreferencesKey("lowercase")
        val maxLabels = intPreferencesKey("max_labels")
        val screenshotTitles = booleanPreferencesKey("screenshot_titles")
        val folderNames = stringPreferencesKey("folder_names_json")
        val customCategories = stringPreferencesKey("custom_categories_json")
        val groupByYear = booleanPreferencesKey("group_by_year")
        val writeLogFile = booleanPreferencesKey("write_log_file")
        val enableOcr = booleanPreferencesKey("enable_ocr")
        val videoFrames = intPreferencesKey("video_frames")
        val includeSubfolders = booleanPreferencesKey("include_subfolders")
        val faceGrouping = booleanPreferencesKey("face_grouping")
        val detectDuplicates = booleanPreferencesKey("detect_duplicates")
        val duplicateHandling = stringPreferencesKey("duplicate_handling")
    }

    private val folderMapSerializer = MapSerializer(String.serializer(), String.serializer())
    private val categoriesSerializer = ListSerializer(UserCategory.serializer())

    override val settings: Flow<AppSettings> = store.data
        .catch { error -> if (error is IOException) emit(emptyPreferences()) else throw error }
        .map(::toSettings)

    override suspend fun current(): AppSettings = settings.first()

    override suspend fun setOnboardingComplete(complete: Boolean) = edit { it[Keys.onboarding] = complete }
    override suspend fun setThemeMode(mode: ThemeMode) = edit { it[Keys.theme] = mode.name }
    override suspend fun setDynamicColor(enabled: Boolean) = edit { it[Keys.dynamicColor] = enabled }
    override suspend fun setDefaultMode(mode: OperationMode) = edit { it[Keys.defaultMode] = mode.name }

    override suspend fun setDestination(uri: String?, label: String?) = edit {
        if (uri == null) it.remove(Keys.destinationUri) else it[Keys.destinationUri] = uri
        if (label == null) it.remove(Keys.destinationLabel) else it[Keys.destinationLabel] = label
    }

    override suspend fun updateNaming(transform: (NamingSettings) -> NamingSettings) = edit { prefs ->
        val updated = transform(naming(prefs))
        prefs[Keys.namingPreset] = updated.preset.name
        prefs[Keys.customTemplate] = updated.customTemplate
        prefs[Keys.dateStyle] = updated.dateStyle.name
        prefs[Keys.separator] = updated.separator.name
        prefs[Keys.lowercase] = updated.lowercase
        prefs[Keys.maxLabels] = updated.maxLabels.coerceIn(0, 6)
        prefs[Keys.screenshotTitles] = updated.screenshotTitles
    }

    override suspend fun setFolderName(category: ContentCategory, name: String?) = edit { prefs ->
        val map = folderNames(prefs).toMutableMap()
        if (name.isNullOrBlank() || name.trim() == category.defaultFolder) map.remove(category) else map[category] = name.trim()
        prefs[Keys.folderNames] = json.encodeToString(folderMapSerializer, map.mapKeys { it.key.name })
    }

    override suspend fun resetFolderNames() = edit { it.remove(Keys.folderNames) }

    override suspend fun upsertCustomCategory(category: UserCategory) = editCategories { list ->
        val cleaned = category.copy(
            folderName = category.folderName.trim(),
            keywords = category.keywords.map { it.trim() }.filter { it.isNotEmpty() }.distinct(),
        )
        val index = list.indexOfFirst { it.id == category.id }
        if (index >= 0) list[index] = cleaned else list += cleaned
    }

    override suspend fun deleteCustomCategory(id: String) = editCategories { list -> list.removeAll { it.id == id } }

    override suspend fun moveCustomCategory(id: String, up: Boolean) = editCategories { list ->
        val index = list.indexOfFirst { it.id == id }
        val target = if (up) index - 1 else index + 1
        if (index >= 0 && target in list.indices) {
            val item = list.removeAt(index)
            list.add(target, item)
        }
    }

    override suspend fun setGroupByYear(enabled: Boolean) = edit { it[Keys.groupByYear] = enabled }
    override suspend fun setWriteLogFile(enabled: Boolean) = edit { it[Keys.writeLogFile] = enabled }

    override suspend fun updateAnalysis(transform: (AnalysisOptions) -> AnalysisOptions) = edit { prefs ->
        val updated = transform(analysis(prefs))
        prefs[Keys.enableOcr] = updated.enableOcr
        prefs[Keys.videoFrames] = updated.videoFrameCount.coerceIn(3, 12)
        prefs[Keys.includeSubfolders] = updated.includeSubfolders
        prefs[Keys.faceGrouping] = updated.faceGrouping
        prefs[Keys.detectDuplicates] = updated.detectDuplicates
    }

    override suspend fun setDuplicateHandling(handling: DuplicateHandling) = edit { it[Keys.duplicateHandling] = handling.name }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        store.edit { block(it) }
    }

    private suspend fun editCategories(block: (MutableList<UserCategory>) -> Unit) = edit { prefs ->
        val list = customCategories(prefs).toMutableList()
        block(list)
        prefs[Keys.customCategories] = json.encodeToString(categoriesSerializer, list)
    }

    private fun toSettings(prefs: Preferences): AppSettings {
        val defaults = AppSettings()
        return AppSettings(
            onboardingComplete = prefs[Keys.onboarding] ?: false,
            themeMode = enumOrDefault(prefs[Keys.theme].orEmpty(), defaults.themeMode),
            dynamicColor = prefs[Keys.dynamicColor] ?: defaults.dynamicColor,
            defaultMode = enumOrDefault(prefs[Keys.defaultMode].orEmpty(), defaults.defaultMode),
            destinationUri = prefs[Keys.destinationUri],
            destinationLabel = prefs[Keys.destinationLabel],
            naming = naming(prefs),
            folderNames = folderNames(prefs),
            customCategories = customCategories(prefs),
            groupByYear = prefs[Keys.groupByYear] ?: defaults.groupByYear,
            writeLogFile = prefs[Keys.writeLogFile] ?: defaults.writeLogFile,
            analysis = analysis(prefs),
            duplicateHandling = enumOrDefault(prefs[Keys.duplicateHandling].orEmpty(), defaults.duplicateHandling),
        )
    }

    private fun naming(prefs: Preferences): NamingSettings {
        val d = NamingSettings()
        return NamingSettings(
            preset = enumOrDefault(prefs[Keys.namingPreset].orEmpty(), d.preset),
            customTemplate = prefs[Keys.customTemplate] ?: d.customTemplate,
            dateStyle = enumOrDefault(prefs[Keys.dateStyle].orEmpty(), d.dateStyle),
            separator = enumOrDefault(prefs[Keys.separator].orEmpty(), d.separator),
            lowercase = prefs[Keys.lowercase] ?: d.lowercase,
            maxLabels = prefs[Keys.maxLabels] ?: d.maxLabels,
            screenshotTitles = prefs[Keys.screenshotTitles] ?: d.screenshotTitles,
        )
    }

    private fun analysis(prefs: Preferences): AnalysisOptions {
        val d = AnalysisOptions()
        return AnalysisOptions(
            enableOcr = prefs[Keys.enableOcr] ?: d.enableOcr,
            videoFrameCount = prefs[Keys.videoFrames] ?: d.videoFrameCount,
            includeSubfolders = prefs[Keys.includeSubfolders] ?: d.includeSubfolders,
            faceGrouping = prefs[Keys.faceGrouping] ?: d.faceGrouping,
            detectDuplicates = prefs[Keys.detectDuplicates] ?: d.detectDuplicates,
        )
    }

    private fun folderNames(prefs: Preferences): Map<ContentCategory, String> {
        val raw = prefs[Keys.folderNames] ?: return emptyMap()
        return runCatching { json.decodeFromString(folderMapSerializer, raw) }.getOrDefault(emptyMap())
            .mapNotNull { (key, value) -> ContentCategory.entries.firstOrNull { it.name == key }?.let { it to value } }
            .toMap()
    }

    private fun customCategories(prefs: Preferences): List<UserCategory> {
        val raw = prefs[Keys.customCategories] ?: return emptyList()
        return runCatching { json.decodeFromString(categoriesSerializer, raw) }.getOrDefault(emptyList())
    }
}
