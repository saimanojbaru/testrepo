package com.sortora.app.domain.planning

import com.sortora.app.domain.model.AnalysisResult
import com.sortora.app.domain.model.AnalysisStatus
import com.sortora.app.domain.model.AppSettings
import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.DuplicateInfo
import com.sortora.app.domain.model.OperationMode
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
    /** Target folder under the output folder; for rename-in-place, the folder the file is in now. */
    val folder: String,
    val category: ContentCategory,
    /** Set when one of the user's own folders matched. */
    val userCategoryMatch: CategoryMatch?,
    val nameEdited: Boolean,
    val folderEdited: Boolean,
    val patternEdited: Boolean = false,
    /** Rename mode: the file keeps its folder and only gets a new name. */
    val inPlace: Boolean = false,
    val duplicate: DuplicateInfo? = null,
)

/** Extra inputs that don't live on the item itself. */
data class PlanOptions(
    val mode: OperationMode = OperationMode.COPY,
    /** Names the user gave to faces, per item. */
    val people: Map<Long, List<String>> = emptyMap(),
)

/**
 * Turns analysed items + settings into concrete names and folders.
 *
 * Pure and deterministic: the preview and the apply step produce identical results from the same
 * input. Name collisions inside a folder (case-insensitive, as on most Android storage) get
 * "_2", "_3"… suffixes. Collisions with files already present in the destination are resolved
 * again at apply time, because only then do we look at the destination's contents.
 *
 * `{counter}` numbers files per folder and pattern, in capture-time order, so a batch rename
 * like "Bali_{counter}" gives Bali_001, Bali_002… in the order the photos were taken.
 */
class SortPlanner @Inject constructor() {

    private val nameGenerator = FileNameGenerator()

    private class Prepared(
        val item: SessionItem,
        val analysis: AnalysisResult,
        val match: CategoryMatch?,
        val folder: String,
        /** Where names must be unique: the target folder, or the source folder for rename mode. */
        val uniquenessKey: String,
        val pattern: String?,
        val tags: List<String>,
        val people: List<String>,
    )

    fun plan(
        items: List<SessionItem>,
        settings: AppSettings,
        options: PlanOptions = PlanOptions(),
        zone: ZoneId = ZoneId.systemDefault(),
    ): Map<Long, PlannedFile> {
        val generator = if (zone == ZoneId.systemDefault()) nameGenerator else FileNameGenerator(zone)
        val inPlace = options.mode == OperationMode.RENAME

        // Pass 1: folders, patterns and tags.
        val prepared = items.sortedBy { it.id }.mapNotNull { item ->
            val analysis = item.analysis ?: return@mapNotNull null
            if (item.analysisStatus != AnalysisStatus.DONE) return@mapNotNull null
            val match = CategoryMatcher.bestMatch(
                MatchInput(analysis.category, analysis.labels, analysis.text.keywords, item.file.baseName),
                settings.customCategories,
            )
            val folder = when {
                inPlace -> item.file.pathHint.orEmpty()
                else -> item.customFolder?.let(FileNameSanitizer::sanitizeFolderPath)?.takeIf { it.isNotEmpty() }
                    ?: suggestedFolder(match, analysis.category, analysis.metadata.captureTimeMillis, settings, zone)
            }
            val people = options.people[item.id].orEmpty()
            Prepared(
                item = item,
                analysis = analysis,
                match = match,
                folder = folder,
                uniquenessKey = if (inPlace) item.file.parentUri ?: item.file.pathHint ?: "" else folder,
                pattern = item.customPattern?.takeIf { it.isNotBlank() },
                tags = (people + analysis.tags).distinctBy { it.lowercase() },
                people = people,
            )
        }

        // Pass 2: counters per (folder, pattern), in capture order. Only files being written count.
        val counters = HashMap<Long, Int>()
        prepared.filter { it.item.included }
            .groupBy { it.uniquenessKey.lowercase() to it.pattern }
            .values
            .forEach { group ->
                group.sortedWith(
                    compareBy<Prepared> { it.analysis.metadata.captureTimeMillis ?: Long.MAX_VALUE }
                        .thenBy { it.item.file.displayName.lowercase() }
                        .thenBy { it.item.id },
                ).forEachIndexed { index, p -> counters[p.item.id] = index + 1 }
            }

        // Pass 3: names, de-duplicated per folder.
        val taken = HashMap<String, MutableSet<String>>()
        val result = LinkedHashMap<Long, PlannedFile>()
        for (p in prepared) {
            val item = p.item
            val baseName = item.customName?.takeIf { it.isNotBlank() }
                ?.let { generator.sanitizeUserName(it, item.file.extension, settings.naming) }
                ?: generator.generate(
                    NameInput(
                        originalBaseName = item.file.baseName,
                        extension = item.file.extension,
                        captureTimeMillis = p.analysis.metadata.captureTimeMillis,
                        category = p.analysis.category,
                        tags = p.tags,
                        title = p.analysis.text.title,
                        counter = counters[item.id],
                        folder = p.folder,
                        people = p.people,
                        camera = p.analysis.metadata.cameraModel,
                    ),
                    settings.naming,
                    p.pattern,
                )
            val name = if (item.included) {
                reserveName(baseName, settings.naming.separator.char, taken.getOrPut(p.uniquenessKey.lowercase()) { HashSet() })
            } else {
                baseName
            }
            result[item.id] = PlannedFile(
                itemId = item.id,
                name = name,
                folder = p.folder,
                category = p.analysis.category,
                userCategoryMatch = p.match,
                nameEdited = !item.customName.isNullOrBlank(),
                folderEdited = !item.customFolder.isNullOrBlank(),
                patternEdited = p.pattern != null,
                inPlace = inPlace,
                duplicate = item.duplicate,
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
        /** Folder used for extra copies when duplicates are set aside. */
        const val DUPLICATES_FOLDER = "Duplicates"

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
