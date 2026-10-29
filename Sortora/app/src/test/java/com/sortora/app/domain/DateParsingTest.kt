package com.sortora.app.domain

import com.sortora.app.domain.naming.DateParsing
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class DateParsingTest {

    private val today = LocalDate.of(2026, 1, 1)

    private fun fromName(name: String) = DateParsing.parseFromFileName(name, UTC, today)

    @Test
    fun `camera file names`() {
        assertEquals(millis(2025, 10, 12, 14, 30, 15), fromName("IMG_20251012_143015.jpg"))
        assertEquals(millis(2025, 10, 12, 14, 30, 15), fromName("PXL_20251012_143015123.jpg"))
        assertEquals(millis(2025, 10, 12, 14, 30, 15), fromName("VID_20251012_143015.mp4"))
    }

    @Test
    fun `screenshot file names`() {
        assertEquals(millis(2025, 10, 12, 14, 30, 15), fromName("Screenshot_2025-10-12-14-30-15-123_com.android.settings.png"))
        assertEquals(millis(2025, 10, 12, 14, 30, 15), fromName("Screenshot_20251012-143015_Chrome.jpg"))
    }

    @Test
    fun `date only names use midday`() {
        assertEquals(millis(2025, 10, 12), fromName("IMG-20251012-WA0001.jpg"))
    }

    @Test
    fun `invalid or future dates are rejected`() {
        assertNull(fromName("IMG_1234.jpg"))
        assertNull(fromName("file_20251399.jpg"))
        assertNull(fromName("VID_20990101_000000.mp4"))
    }

    @Test
    fun `exif and container dates`() {
        assertEquals(millis(2025, 10, 12, 14, 30, 15), DateParsing.parseExif("2025:10:12 14:30:15", UTC))
        assertEquals(millis(2025, 10, 12, 14, 30, 15), DateParsing.parseContainerDate("20251012T143015.000Z"))
        assertNull(DateParsing.parseContainerDate("19040101T000000.000Z"))
        assertNull(DateParsing.parseExif("0000:00:00 00:00:00", UTC))
    }
}
