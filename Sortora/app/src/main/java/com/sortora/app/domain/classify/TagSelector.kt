package com.sortora.app.domain.classify

import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.DetectedLabel
import com.sortora.app.domain.model.FaceSummary
import com.sortora.app.domain.model.TextSummary

/**
 * Picks short descriptive keywords for a file name, e.g. ["person", "desk"] for a talking-head
 * clip recorded at a desk. Returned tags are lower-case words separated by spaces.
 */
object TagSelector {

    private const val MIN_CONFIDENCE = 0.6f
    private const val MAX_CANDIDATES = 6

    /** Tags that would just repeat the category token in the name. */
    private val redundantForCategory: Map<ContentCategory, Set<String>> = mapOf(
        ContentCategory.FOOD to setOf("food", "dish", "cuisine", "meal"),
        ContentCategory.PET to setOf("pet", "animal"),
        ContentCategory.LANDSCAPE to setOf("landscape", "nature"),
        ContentCategory.CITY to setOf("city"),
        ContentCategory.PRODUCT to setOf("product"),
        ContentCategory.DOCUMENT to setOf("document", "text", "paper"),
        ContentCategory.PEOPLE to setOf("people", "person"),
        ContentCategory.GROUP to setOf("people", "person", "crowd"),
        ContentCategory.SELFIE to setOf("person", "face"),
    )

    private val personCategories = setOf(ContentCategory.TALKING_HEAD, ContentCategory.B_ROLL)

    fun select(
        category: ContentCategory,
        labels: List<DetectedLabel>,
        faces: FaceSummary,
        text: TextSummary,
        hasFacesInVideo: Boolean = false,
    ): List<String> {
        val result = LinkedHashSet<String>()

        // Videos: say who is in it first ("talkinghead_person_desk").
        if (category in personCategories && (hasFacesInVideo || faces.count > 0)) result += "person"

        // Documents and screenshots: the title on the page is the most useful keyword.
        if (category == ContentCategory.DOCUMENT || category == ContentCategory.SCREENSHOT) {
            text.title?.let { title ->
                TextNormalizer.words(title)
                    .filter { it.length >= 3 && it.all(Char::isLetter) }
                    .take(2)
                    .joinToString(" ")
                    .takeIf { it.isNotEmpty() }
                    ?.let(result::add)
            }
        }

        val redundant = redundantForCategory[category].orEmpty() + category.nameToken
        labels
            .filter { it.confidence >= MIN_CONFIDENCE }
            .sortedByDescending { it.confidence }
            .mapNotNull { LabelVocabulary.tagFor(it.text) }
            .filter { it !in redundant }
            .forEach { if (result.size < MAX_CANDIDATES) result += it }

        return result.toList()
    }
}
