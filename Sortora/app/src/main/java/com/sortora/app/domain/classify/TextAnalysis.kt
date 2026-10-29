package com.sortora.app.domain.classify

import com.sortora.app.domain.model.TextSummary

/** One OCR line with its bounding box in pixels. Kept free of ML Kit types so it is testable. */
data class OcrLine(val text: String, val left: Int, val top: Int, val right: Int, val bottom: Int) {
    val height: Int get() = (bottom - top).coerceAtLeast(0)
    val area: Long get() = (right - left).coerceAtLeast(0).toLong() * height
}

/** Reduces raw OCR output to the few signals the classifier and the namer need. */
object TextAnalysis {

    private const val STATUS_BAR_FRACTION = 0.07f
    private const val TITLE_REGION_FRACTION = 0.35f
    private const val MAX_KEYWORDS = 80

    private val clock = Regex("(?<!\\d)([01]?\\d|2[0-3])[:.][0-5]\\d(?!\\d)")
    private val battery = Regex("(?<!\\d)\\d{1,3}\\s?%")
    private val uiChrome = setOf(
        "search", "cancel", "done", "back", "edit", "more", "menu", "close", "ok", "share", "save",
        "next", "skip", "home", "settings and privacy", "sign in", "log in", "allow", "deny",
    )

    fun summarize(lines: List<OcrLine>, imageWidth: Int, imageHeight: Int): TextSummary {
        if (lines.isEmpty() || imageWidth <= 0 || imageHeight <= 0) return TextSummary.NONE

        val imageArea = imageWidth.toLong() * imageHeight
        val charCount = lines.sumOf { line -> line.text.count { !it.isWhitespace() } }
        val coverage = (lines.sumOf { it.area }.toFloat() / imageArea).coerceIn(0f, 1f)
        val statusBarLimit = imageHeight * STATUS_BAR_FRACTION

        val hasStatusBar = lines.any { line ->
            line.bottom <= statusBarLimit && (clock.containsMatchIn(line.text) || battery.containsMatchIn(line.text))
        }

        return TextSummary(
            charCount = charCount,
            lineCount = lines.size,
            coverage = coverage,
            hasStatusBar = hasStatusBar,
            title = findTitle(lines, imageHeight, statusBarLimit),
            keywords = keywords(lines),
        )
    }

    /**
     * The title is usually the largest short line near the top (an app bar, a heading).
     * Falls back to the largest short line anywhere for documents.
     */
    private fun findTitle(lines: List<OcrLine>, imageHeight: Int, statusBarLimit: Float): String? {
        fun candidate(line: OcrLine): String? {
            val text = line.text.trim().trim { !it.isLetterOrDigit() }
            val words = text.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (words.size !in 1..5 || text.length !in 3..40) return null
            val letters = text.count { it.isLetter() }
            if (letters < text.length * 0.6f) return null
            if (TextNormalizer.normalize(text) in uiChrome) return null
            return text
        }
        val top = lines
            .filter { it.top >= statusBarLimit && it.bottom <= imageHeight * TITLE_REGION_FRACTION }
            .mapNotNull { line -> candidate(line)?.let { line to it } }
        val pool = top.ifEmpty { lines.mapNotNull { line -> candidate(line)?.let { line to it } } }
        return pool.maxWithOrNull(compareBy<Pair<OcrLine, String>> { it.first.height }.thenBy { -it.first.top })
            ?.second
    }

    private fun keywords(lines: List<OcrLine>): List<String> =
        lines.asSequence()
            .flatMap { TextNormalizer.words(it.text).asSequence() }
            .filter { it.length >= 3 && it.all(Char::isLetter) }
            .distinct()
            .take(MAX_KEYWORDS)
            .toList()
}
