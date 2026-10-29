package com.sortora.app.domain.planning

import com.sortora.app.domain.classify.LabelVocabulary
import com.sortora.app.domain.classify.TextNormalizer
import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.DetectedLabel
import com.sortora.app.domain.model.UserCategory

/** What the matcher looks at for one file. */
data class MatchInput(
    val category: ContentCategory,
    val labels: List<DetectedLabel>,
    /** Lower-case words read by OCR. */
    val textKeywords: List<String>,
    val originalName: String,
)

data class CategoryMatch(
    val category: UserCategory,
    val score: Float,
    /** Human-readable reason, e.g. "label “Dog”" — shown in the preview. */
    val reason: String,
)

/**
 * Maps a file to one of the user's own folders ("Receipts", "Dogs", "Client X B-roll"...).
 *
 * Each folder's name and keywords are expanded through [LabelVocabulary.concepts], so a folder
 * called "Pets" also catches the labels "Dog", "Cat" and "Bird", and "Receipts" catches OCR words
 * like "subtotal" and "tax". A folder wins only when the evidence clears [THRESHOLD]; otherwise
 * the file goes to its built-in category folder.
 */
object CategoryMatcher {

    const val THRESHOLD = 0.55f

    private const val EXPLICIT_WEIGHT = 1f
    private const val CONCEPT_WEIGHT = 0.85f
    private const val CATEGORY_HIT = 1.2f
    private const val TEXT_HIT = 0.6f
    private const val FILENAME_HIT = 0.6f
    private const val EXTRA_EVIDENCE = 0.15f

    /** Words that appear in folder names without saying anything about the content. */
    private val ignoredWords = setOf(
        "photo", "picture", "pic", "image", "img", "video", "clip", "file", "folder", "misc",
        "other", "stuff", "new", "old", "my", "all", "random", "camera", "phone", "media", "sorted",
    )

    private sealed interface Term {
        val origin: String
        data class Words(val phrase: String, val weight: Float, override val origin: String) : Term
        data class Category(val category: ContentCategory, override val origin: String) : Term
    }

    fun bestMatch(input: MatchInput, categories: List<UserCategory>): CategoryMatch? {
        if (categories.isEmpty()) return null
        val labelStems = input.labels.map { it to LabelVocabulary.stemPhrase(it.text) }
        val textWords = input.textKeywords.map(TextNormalizer::singular).toSet()
        val nameStems = TextNormalizer.stems(input.originalName).toSet()

        return categories
            .mapNotNull { category -> score(category, input.category, labelStems, textWords, nameStems) }
            .filter { it.score >= THRESHOLD }
            .maxByOrNull { it.score } // ties keep list order: the earlier folder wins
    }

    private fun score(
        category: UserCategory,
        detected: ContentCategory,
        labels: List<Pair<DetectedLabel, String>>,
        textWords: Set<String>,
        nameStems: Set<String>,
    ): CategoryMatch? {
        val hits = expand(category).mapNotNull { term ->
            when (term) {
                is Term.Category ->
                    if (term.category == detected) CATEGORY_HIT to "category “${term.origin}”" else null

                is Term.Words -> {
                    val termStems = term.phrase.split(' ')
                    val labelHit = labels
                        .filter { (_, stem) -> termStems.all { it in stem.split(' ') } }
                        .maxByOrNull { it.first.confidence }
                    when {
                        labelHit != null -> labelHit.first.confidence * term.weight to "label “${labelHit.first.text}”"
                        termStems.all { it.length >= 3 && it in textWords } -> TEXT_HIT * term.weight to "text “${term.phrase}”"
                        termStems.all { it.length >= 3 && it in nameStems } -> FILENAME_HIT * term.weight to "file name “${term.phrase}”"
                        else -> null
                    }
                }
            }
        }
        if (hits.isEmpty()) return null
        val best = hits.maxBy { it.first }
        val extra = (hits.size - 1).coerceAtMost(3) * EXTRA_EVIDENCE
        return CategoryMatch(category, best.first + extra, best.second)
    }

    /** Folder name + keywords, plus everything the concept table knows about them. */
    private fun expand(category: UserCategory): List<Term> {
        val terms = LinkedHashMap<String, Term>()
        val sources = (listOf(category.folderName) + category.keywords).filter { it.isNotBlank() }

        for (source in sources) {
            val phrase = conceptKey(source)
            val words = phrase.split(' ').filter { it.isNotEmpty() && it !in ignoredWords && !it.all(Char::isDigit) }
            if (words.isEmpty()) continue
            val candidates = (listOf(words.joinToString(" ")) + words).distinct()

            // The user's own words always count at full weight.
            candidates.forEach { candidate ->
                val stems = LabelVocabulary.stemPhrase(candidate)
                if (stems.isNotEmpty()) terms.putIfAbsent("w:$stems", Term.Words(stems, EXPLICIT_WEIGHT, source))
            }
            // Then whatever the concept table associates with them.
            candidates.mapNotNull { LabelVocabulary.concepts[it] }.forEach { concept ->
                concept.categories.forEach { terms.putIfAbsent("c:${it.name}", Term.Category(it, source)) }
                concept.words.forEach { word ->
                    val stems = LabelVocabulary.stemPhrase(word)
                    if (stems.isNotEmpty()) terms.putIfAbsent("w:$stems", Term.Words(stems, CONCEPT_WEIGHT, source))
                }
            }
        }
        return terms.values.toList()
    }

    /** "Talking-Heads" → "talking head", matching the keys of [LabelVocabulary.concepts]. */
    private fun conceptKey(text: String): String =
        TextNormalizer.normalize(text).split(' ').filter { it.isNotEmpty() }.joinToString(" ") { TextNormalizer.singular(it) }
}
