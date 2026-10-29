package com.sortora.app.domain.classify

import java.text.Normalizer

/** Small, dependency-free helpers for comparing labels, keywords and file names. */
object TextNormalizer {

    private val diacritics = Regex("\\p{Mn}+")
    private val nonWord = Regex("[^\\p{L}\\p{N}]+")
    private val stopWords = setOf("and", "the", "of", "my", "for", "with", "a", "an", "to", "in", "on")

    /** Lower-case, strip accents, and collapse anything that is not a letter/digit to one space. */
    fun normalize(text: String): String {
        val decomposed = Normalizer.normalize(text, Normalizer.Form.NFD)
        return diacritics.replace(decomposed, "")
            .lowercase()
            .replace(nonWord, " ")
            .trim()
    }

    /** Normalised words, without stop words. */
    fun words(text: String): List<String> =
        normalize(text).split(' ').filter { it.isNotEmpty() && it !in stopWords }

    /** Very small English singulariser: "puppies" → "puppy", "boxes" → "box", "dogs" → "dog". */
    fun singular(word: String): String = when {
        word.length <= 3 -> word
        word.endsWith("ies") -> word.dropLast(3) + "y"
        word.endsWith("sses") -> word.dropLast(2)
        word.endsWith("ches") || word.endsWith("shes") || word.endsWith("xes") -> word.dropLast(2)
        word.endsWith("ss") || word.endsWith("us") || word.endsWith("is") -> word
        word.endsWith("s") -> word.dropLast(1)
        else -> word
    }

    /** Normalised, singularised words of a phrase. */
    fun stems(text: String): List<String> = words(text).map(::singular)

    /** True if every stem of [term] appears among the stems of [phrase]. */
    fun phraseContains(phrase: String, term: String): Boolean {
        val termStems = stems(term)
        if (termStems.isEmpty()) return false
        val phraseStems = stems(phrase).toSet()
        return termStems.all { it in phraseStems }
    }
}
