package com.sortora.app.domain.naming

import java.text.Normalizer

/** Makes names safe on every Android storage backend (FAT/exFAT SD cards included). */
object FileNameSanitizer {

    /** FAT/exFAT allow 255 UTF-16 units, ext4 255 bytes; keep well under both. */
    private const val MAX_NAME_BYTES = 180
    private const val MAX_FOLDER_CHARS = 60

    private val illegal = Regex("[\\\\/:*?\"<>|\\p{Cntrl}]")
    private val whitespace = Regex("\\s+")
    private val diacritics = Regex("\\p{Mn}+")
    private val repeatedSeparators = Regex("([_\\-]){2,}")
    private val edgeJunk = Regex("^[\\s._\\-]+|[\\s._\\-]+$")

    /**
     * Sanitises a base name (no extension): strips illegal characters and accents, turns spaces
     * into [separator], collapses repeated separators and trims to a safe byte length.
     */
    fun sanitizeBaseName(raw: String, separator: Char = '_'): String {
        val ascii = diacritics.replace(Normalizer.normalize(raw, Normalizer.Form.NFD), "")
        var name = illegal.replace(ascii, "")
        name = whitespace.replace(name, separator.toString())
        name = repeatedSeparators.replace(name) { it.value.first().toString() }
        name = edgeJunk.replace(name, "")
        return truncateUtf8(name, MAX_NAME_BYTES).let { edgeJunk.replace(it, "") }
    }

    /** Sanitises one folder name. Keeps spaces and "&" for readability ("Pets & Animals"). */
    fun sanitizeFolderSegment(raw: String): String {
        var name = illegal.replace(raw, "")
        name = whitespace.replace(name, " ")
        name = edgeJunk.replace(name, "")
        return name.take(MAX_FOLDER_CHARS).trim()
    }

    /** Sanitises a relative folder path such as "Pets/2025". Empty segments are dropped. */
    fun sanitizeFolderPath(raw: String): String =
        raw.split('/', '\\')
            .map(::sanitizeFolderSegment)
            .filter { it.isNotEmpty() && it != "." && it != ".." }
            .joinToString("/")

    /** Cuts [text] so its UTF-8 encoding fits in [maxBytes], never splitting a character. */
    fun truncateUtf8(text: String, maxBytes: Int): String {
        if (text.toByteArray(Charsets.UTF_8).size <= maxBytes) return text
        val out = StringBuilder()
        var bytes = 0
        var i = 0
        while (i < text.length) {
            val codePoint = text.codePointAt(i)
            val chars = Character.charCount(codePoint)
            val size = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size
            if (bytes + size > maxBytes) break
            out.appendCodePoint(codePoint)
            bytes += size
            i += chars
        }
        return out.toString()
    }
}
