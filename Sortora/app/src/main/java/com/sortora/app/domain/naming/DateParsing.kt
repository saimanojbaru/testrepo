package com.sortora.app.domain.naming

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException

/** Parses the many date formats found in EXIF, video containers and camera file names. */
object DateParsing {

    private const val MIN_YEAR = 1990

    private val exifFormat = DateTimeFormatter.ofPattern("yyyy:MM:dd HH:mm:ss")

    // IMG_20251012_143015, PXL_20251012_143015123, Screenshot_2025-10-12-14-30-15, VID-20251012-WA0001
    private val dateTimeInName = Regex(
        "(?<!\\d)((?:19|20)\\d{2})[-_.]?(\\d{2})[-_.]?(\\d{2})[-_ T.]?(\\d{2})[-_.:h]?(\\d{2})[-_.:m]?(\\d{2})",
    )
    private val dateInName = Regex("(?<!\\d)((?:19|20)\\d{2})[-_.]?(\\d{2})[-_.]?(\\d{2})(?!\\d)")

    /** EXIF "2025:10:12 14:30:15", interpreted in [zone] (EXIF local time has no zone). */
    fun parseExif(value: String?, zone: ZoneId = ZoneId.systemDefault()): Long? {
        if (value.isNullOrBlank()) return null
        return try {
            val dateTime = LocalDateTime.parse(value.trim().take(19), exifFormat)
            dateTime.takeIf { it.year >= MIN_YEAR }?.atZone(zone)?.toInstant()?.toEpochMilli()
        } catch (_: DateTimeParseException) {
            null
        }
    }

    /**
     * MediaMetadataRetriever METADATA_KEY_DATE, e.g. "20251012T143015.000Z" (UTC).
     * Many devices write "19040101T000000.000Z" when unknown; such values are ignored.
     */
    fun parseContainerDate(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val digits = value.filter(Char::isDigit)
        if (digits.length < 14) return null
        return try {
            val dateTime = LocalDateTime.of(
                digits.substring(0, 4).toInt(), digits.substring(4, 6).toInt(), digits.substring(6, 8).toInt(),
                digits.substring(8, 10).toInt(), digits.substring(10, 12).toInt(), digits.substring(12, 14).toInt(),
            )
            dateTime.takeIf { it.year >= MIN_YEAR }?.toInstant(ZoneOffset.UTC)?.toEpochMilli()
        } catch (_: RuntimeException) {
            null
        }
    }

    /** Extracts a date (and time when present) from typical camera / app file names. */
    fun parseFromFileName(name: String, zone: ZoneId = ZoneId.systemDefault(), now: LocalDate = LocalDate.now(zone)): Long? {
        dateTimeInName.find(name)?.let { match ->
            val (y, mo, d, h, mi, s) = match.destructured
            buildDateTime(y, mo, d, h, mi, s, now)?.let { return it.atZone(zone).toInstant().toEpochMilli() }
        }
        dateInName.find(name)?.let { match ->
            val (y, mo, d) = match.destructured
            buildDateTime(y, mo, d, "12", "00", "00", now)?.let { return it.atZone(zone).toInstant().toEpochMilli() }
        }
        return null
    }

    private fun buildDateTime(y: String, mo: String, d: String, h: String, mi: String, s: String, now: LocalDate): LocalDateTime? {
        return try {
            val dateTime = LocalDateTime.of(y.toInt(), mo.toInt(), d.toInt(), h.toInt(), mi.toInt(), s.toInt())
            dateTime.takeIf { it.year >= MIN_YEAR && !it.toLocalDate().isAfter(now.plusDays(1)) }
        } catch (_: RuntimeException) {
            null
        }
    }
}
