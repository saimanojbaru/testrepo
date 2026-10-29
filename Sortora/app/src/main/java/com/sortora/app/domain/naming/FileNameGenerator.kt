package com.sortora.app.domain.naming

import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.DateStyle
import com.sortora.app.domain.model.NamingPreset
import com.sortora.app.domain.model.NamingSettings
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/** Everything the generator needs to know about one file. */
data class NameInput(
    val originalBaseName: String,
    /** Lower-case extension without the dot, e.g. "mp4". */
    val extension: String,
    val captureTimeMillis: Long?,
    val category: ContentCategory,
    val tags: List<String>,
    /** Title read from the screen / page, if any. */
    val title: String?,
)

/**
 * Renders file names from templates such as `{date}_{category}_{labels}`.
 *
 * Tokens:
 *  - `{date}` capture date in the chosen style, `{time}` HHmmss
 *  - `{category}` e.g. "talkinghead", `{labels}` top N tags joined by the separator
 *  - `{text}` on-screen / page title in PascalCase, `{original}` the original name
 *
 * Empty tokens disappear together with their separator, so `{date}_{category}_{labels}` for an
 * uncategorised photo becomes `2025-10-12_dog` rather than `2025-10-12__dog`.
 */
class FileNameGenerator(private val zone: ZoneId = ZoneId.systemDefault()) {

    private val tokenRegex = Regex("\\{(\\w+)\\}")
    private val repeatedSeparators = Regex("([_\\-]){2,}")

    fun generate(input: NameInput, settings: NamingSettings): String {
        val sep = settings.separator.char
        val template = templateFor(input, settings)
        val rendered = tokenRegex.replace(template) { match -> renderToken(match.groupValues[1], input, settings) }
        val collapsed = repeatedSeparators.replace(rendered) { it.value.first().toString() }

        var base = FileNameSanitizer.sanitizeBaseName(collapsed, sep)
        if (base.isEmpty()) base = FileNameSanitizer.sanitizeBaseName(input.originalBaseName, sep)
        if (base.isEmpty()) base = "file"
        return withExtension(base, input.extension)
    }

    /** Sanitises a name the user typed in the preview and keeps the original extension. */
    fun sanitizeUserName(typed: String, extension: String, settings: NamingSettings): String {
        val withoutExt = if (extension.isNotEmpty() && typed.endsWith(".$extension", ignoreCase = true)) {
            typed.dropLast(extension.length + 1)
        } else {
            typed
        }
        val base = FileNameSanitizer.sanitizeBaseName(withoutExt, settings.separator.char).ifEmpty { "file" }
        return withExtension(base, extension)
    }

    /** Template actually used for this input (screenshots get their own readable pattern). */
    private fun templateFor(input: NameInput, settings: NamingSettings): String {
        if (input.category == ContentCategory.SCREENSHOT && settings.screenshotTitles) {
            val sep = settings.separator.char
            return when {
                !input.title.isNullOrBlank() -> "Screenshot$sep{text}"
                input.captureTimeMillis != null -> "Screenshot$sep{date}$sep{time}"
                else -> "Screenshot$sep{original}"
            }
        }
        val sep = settings.separator.char
        return when {
            settings.preset == NamingPreset.CUSTOM && settings.customTemplate.isNotBlank() -> settings.customTemplate
            settings.preset == NamingPreset.CUSTOM -> NamingPreset.DATE_CATEGORY_LABELS.template.replace('_', sep)
            // Presets are written with "_"; token names contain no underscores, so this is safe.
            else -> settings.preset.template.replace('_', sep)
        }
    }

    private fun renderToken(token: String, input: NameInput, settings: NamingSettings): String {
        val sep = settings.separator.char
        val value = when (token.lowercase()) {
            "date" -> formatDate(input.captureTimeMillis, settings.dateStyle)
            "time" -> input.captureTimeMillis?.let { TIME_FORMAT.format(Instant.ofEpochMilli(it).atZone(zone)) }.orEmpty()
            "category" -> input.category.nameToken
            "labels" -> input.tags
                .take(settings.maxLabels.coerceIn(0, 6))
                .map { phrase(it, wordJoiner(sep)) }
                .filter { it.isNotEmpty() }
                .joinToString(sep.toString())
            "text" -> pascalCase(input.title.orEmpty())
            "original" -> input.originalBaseName
            else -> ""
        }
        val lowercaseToken = token.lowercase() in setOf("category", "labels", "original")
        return if (settings.lowercase && lowercaseToken) value.lowercase() else value
    }

    private fun formatDate(millis: Long?, style: DateStyle): String {
        val pattern = style.pattern ?: return ""
        millis ?: return ""
        return DateTimeFormatter.ofPattern(pattern).format(Instant.ofEpochMilli(millis).atZone(zone))
    }

    /** Multi-word tags use the "other" separator so tags stay visually distinct. */
    private fun wordJoiner(sep: Char): String = if (sep == '_') "-" else "_"

    private fun phrase(tag: String, joiner: String): String =
        tag.split(WORD_SPLIT).filter { it.isNotEmpty() }.joinToString(joiner)

    private fun pascalCase(text: String): String =
        text.split(WORD_SPLIT)
            .filter { it.isNotEmpty() }
            .take(4)
            .joinToString("") { word -> word.lowercase().replaceFirstChar { it.titlecase() } }

    private fun withExtension(base: String, extension: String): String =
        if (extension.isEmpty()) base else "$base.${extension.lowercase()}"

    private companion object {
        val TIME_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("HHmmss")
        val WORD_SPLIT = Regex("[^\\p{L}\\p{N}]+")
    }
}
