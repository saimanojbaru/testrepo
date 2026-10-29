package com.sortora.app.domain.planning

import com.sortora.app.domain.model.AnalysisStatus
import com.sortora.app.domain.model.AppSettings
import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.SessionItem
import com.sortora.app.domain.naming.FileNameGenerator
import com.sortora.app.domain.naming.FileNameSanitizer
import com.sortora.app.domain.naming.NameInput
import java.time.Instant
import java.time.ZoneId
import javax.inject.Inject

/** The proposed outcome for one file, as shown in the preview. */
data class PlannedFile(
    val itemId: Long,
    val name: String,
    val folder: String,
    val category: ContentCategory,
    /** Set when one of the user's own folders matched. */
    val userCategoryMatch: CategoryMatch?,
    val nameEdited: Boolean,
    val folderEdited: Boolean,
)

/**
 * Turns analysed items + settings into concrete names and folders.
 *
 * Pure and deterministic: the preview and the apply step produce identical results from the same
 * input. Name collisions inside a folder (case-insensitive, as on most Android storage) get
 * "_2", "_3"… suffixes. Collisions with files already present in the destination are resolved
 * again at apply time, because only then do we look at the destination's contents.
 */
class SortPlanner @Inject constructor() {

    private val nameGenerator = FileNameGenerator()

    fun plan(items: List<SessionItem>, settings: AppSettings, zone: ZoneId = ZoneId.systemDefault()): Map<Long, PlannedFile> {
        val generator = if (zone == ZoneId.systemDefault()) nameGenerator else FileNameGenerator(zone)
        val taken = HashMap<String, MutableSet<String>>() // lower-case folder → lower-case names
        val result = LinkedHashMap<Long, PlannedFile>()

        for (item in items.sortedBy { it.id }) {
            val analysis = item.analysis ?: continue
            if (item.analysisStatus != AnalysisStatus.DONE) continue

            val match = CategoryMatcher.bestMatch(
                MatchInput(
                    category = analysis.category,
                    labels = analysis.labels,
                    textKeywords = analysis.text.keywords,
                    originalName = item.file.baseName,
                ),
                settings.customCategories,
            )

            val folder = item.customFolder?.let(FileNameSanitizer::sanitizeFolderPath)?.takeIf { it.isNotEmpty() }
                ?: suggestedFolder(match, analysis.category, analysis.metadata.captureTimeMillis, settings, zone)

            val baseName = item.customName?.takeIf { it.isNotBlank() }
                ?.let { generator.sanitizeUserName(it, item.file.extension, settings.naming) }
                ?: generator.generate(
                    NameInput(
                        originalBaseName = item.file.baseName,
                        extension = item.file.extension,
                        captureTimeMillis = analysis.metadata.captureTimeMillis,
                        category = analysis.category,
                        tags = analysis.tags,
                        title = analysis.text.title,
                    ),
                    settings.naming,
                )

            // Only files that will actually be written reserve a name.
            val name = if (item.included) {
                reserveName(baseName, settings.naming.separator.char, taken.getOrPut(folder.lowercase()) { HashSet() })
            } else {
                baseName
            }

            result[item.id] = PlannedFile(
                itemId = item.id,
                name = name,
                folder = folder,
                category = analysis.category,
                userCategoryMatch = match,
                nameEdited = !item.customName.isNullOrBlank(),
                folderEdited = !item.customFolder.isNullOrBlank(),
            )
        }
        return result
    }

    private fun suggestedFolder(
        match: CategoryMatch?,
        category: ContentCategory,
        captureTime: Long?,
        settings: AppSettings,
        zone: ZoneId,
    ): String {
        val root = FileNameSanitizer.sanitizeFolderSegment(match?.category?.folderName ?: settings.folderFor(category))
            .ifEmpty { ContentCategory.OTHER.defaultFolder }
        if (!settings.groupByYear || captureTime == null) return root
        val year = Instant.ofEpochMilli(captureTime).atZone(zone).year
        return "$root/$year"
    }

    companion object {
        /**
         * Returns [name], or "name_2.ext", "name_3.ext"… if it is already in [takenLowercase]
         * (compared case-insensitively), and records the returned name as taken.
         */
        fun reserveName(name: String, separator: Char, takenLowercase: MutableSet<String>): String {
            val base = if ('.' in name) name.substringBeforeLast('.') else name
            val ext = if ('.' in name) "." + name.substringAfterLast('.') else ""
            var candidate = name
            var counter = 2
            while (!takenLowercase.add(candidate.lowercase())) {
                candidate = "$base$separator$counter$ext"
                counter++
            }
            return candidate
        }
    }
}
