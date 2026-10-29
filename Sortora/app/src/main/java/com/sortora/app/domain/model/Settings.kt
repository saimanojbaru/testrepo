package com.sortora.app.domain.model

import kotlinx.serialization.Serializable

@Serializable
enum class OperationMode { COPY, MOVE }

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** Ready-made name templates. [CUSTOM] uses [NamingSettings.customTemplate]. */
enum class NamingPreset(val template: String) {
    DATE_CATEGORY_LABELS("{date}_{category}_{labels}"),
    DATE_TIME_CATEGORY_LABELS("{date}_{time}_{category}_{labels}"),
    CATEGORY_LABELS("{category}_{labels}"),
    DATE_LABELS("{date}_{labels}"),
    ORIGINAL_LABELS("{original}_{labels}"),
    CUSTOM(""),
}

enum class DateStyle(val pattern: String?) {
    ISO("yyyy-MM-dd"),
    COMPACT("yyyyMMdd"),
    NONE(null),
}

enum class Separator(val char: Char) {
    UNDERSCORE('_'),
    HYPHEN('-'),
}

data class NamingSettings(
    val preset: NamingPreset = NamingPreset.DATE_CATEGORY_LABELS,
    val customTemplate: String = NamingPreset.DATE_CATEGORY_LABELS.template,
    val dateStyle: DateStyle = DateStyle.ISO,
    val separator: Separator = Separator.UNDERSCORE,
    val lowercase: Boolean = true,
    val maxLabels: Int = 2,
    /** Name screenshots "Screenshot_<ScreenTitle>" using text read from the screen. */
    val screenshotTitles: Boolean = true,
) {
    val activeTemplate: String
        get() = if (preset == NamingPreset.CUSTOM) customTemplate else preset.template
}

/**
 * A folder the user defined, plus keywords that should land files in it.
 * Example: folder "Receipts" with keywords "receipt, invoice, total".
 */
@Serializable
data class UserCategory(
    val id: String,
    val folderName: String,
    val keywords: List<String> = emptyList(),
)

data class AnalysisOptions(
    val enableOcr: Boolean = true,
    val videoFrameCount: Int = 5,
    val includeSubfolders: Boolean = true,
)

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val defaultMode: OperationMode = OperationMode.COPY,
    val destinationUri: String? = null,
    val destinationLabel: String? = null,
    val naming: NamingSettings = NamingSettings(),
    /** Overrides for built-in folder names; missing entries use [ContentCategory.defaultFolder]. */
    val folderNames: Map<ContentCategory, String> = emptyMap(),
    val customCategories: List<UserCategory> = emptyList(),
    val groupByYear: Boolean = false,
    val writeLogFile: Boolean = true,
    val analysis: AnalysisOptions = AnalysisOptions(),
) {
    fun folderFor(category: ContentCategory): String =
        folderNames[category]?.takeIf { it.isNotBlank() } ?: category.defaultFolder
}
