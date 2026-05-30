package com.hitit.domain.grid

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GridAggregatorTest {

    @Test
    fun bucketsClampToRange() {
        assertEquals(0, GridAggregator.bucket(-1))
        assertEquals(0, GridAggregator.bucket(0))
        assertEquals(1, GridAggregator.bucket(1))
        assertEquals(2, GridAggregator.bucket(2))
        assertEquals(3, GridAggregator.bucket(3))
        assertEquals(4, GridAggregator.bucket(4))
        assertEquals(4, GridAggregator.bucket(9))
    }

    @Test
    fun intensityByDateBuckets() {
        val src = mapOf(
            LocalDate.of(2026, 1, 2) to 1,
            LocalDate.of(2026, 1, 3) to 6,
        )
        val out = GridAggregator.intensityByDate(src)
        assertEquals(1, out[LocalDate.of(2026, 1, 2)])
        assertEquals(4, out[LocalDate.of(2026, 1, 3)])
    }

    @Test
    fun yearColumnsAreRectangularSevenRows() {
        val cols = GridAggregator.yearColumns(2026, emptyMap(), LocalDate.of(2026, 5, 29))
        assertTrue(cols.isNotEmpty())
        assertTrue(cols.all { it.size == 7 })
    }

    @Test
    fun firstColumnStartsOnMondayOnOrBeforeJan1() {
        val cols = GridAggregator.yearColumns(2026, emptyMap(), LocalDate.of(2026, 5, 29))
        // Jan 1 2026 is a Thursday -> first Monday is Dec 29 2025.
        val firstCell = cols.first().first()
        assertEquals(LocalDate.of(2025, 12, 29), firstCell.date)
        assertFalse(firstCell.inYear)
    }

    @Test
    fun knownDateGetsIntensityAndFutureIsBlanked() {
        val today = LocalDate.of(2026, 5, 29)
        val marchDay = LocalDate.of(2026, 3, 15)
        val cols = GridAggregator.yearColumns(2026, mapOf(marchDay to 4), today)
        val cells = cols.flatten()

        val march = cells.first { it.date == marchDay }
        assertEquals(4, march.intensity)
        assertTrue(march.inYear)
        assertFalse(march.isFuture)

        val dec31 = cells.first { it.date == LocalDate.of(2026, 12, 31) }
        assertTrue(dec31.isFuture)
        assertEquals(0, dec31.intensity)
    }

    @Test
    fun monthLabelsStartWithJanAndAreOrdered() {
        val columns = GridAggregator.yearColumns(2026, emptyMap(), LocalDate.of(2026, 12, 31))
        val labels = GridAggregator.monthLabels(columns)
        assertEquals(12, labels.size)
        assertEquals("Jan", labels.first().label)
        assertEquals("Dec", labels.last().label)
        // Column indices are strictly increasing.
        val indices = labels.map { it.columnIndex }
        assertEquals(indices.sorted(), indices)
        assertEquals(indices.toSet().size, indices.size)
    }

    @Test
    fun monthLabelsEmptyForNoColumns() {
        assertTrue(GridAggregator.monthLabels(emptyList()).isEmpty())
    }
}
