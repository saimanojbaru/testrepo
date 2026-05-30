package com.hitit.domain.grid

import java.time.LocalDate
import java.time.temporal.WeekFields

/**
 * Turns per-day activity into intensity buckets and lays them out as ISO-week columns for
 * "The Grid" (the GitHub-style year heatmap). Pure and deterministic — `today` is passed in.
 */
object GridAggregator {
    const val MAX_INTENSITY = 4

    /** Buckets a count of distinct Reps hit on a day into an intensity 0..[MAX_INTENSITY]. */
    fun bucket(distinctReps: Int): Int = when {
        distinctReps <= 0 -> 0
        distinctReps >= MAX_INTENSITY -> MAX_INTENSITY
        else -> distinctReps
    }

    fun intensityByDate(distinctRepsByDate: Map<LocalDate, Int>): Map<LocalDate, Int> =
        distinctRepsByDate.mapValues { bucket(it.value) }

    /** A single square in the heatmap. */
    data class GridCell(
        val date: LocalDate,
        val intensity: Int,
        val inYear: Boolean,
        val isFuture: Boolean,
    )

    /**
     * Lays out [year] as a list of week columns, each holding 7 cells (Mon..Sun). The first
     * column starts on the Monday on/before Jan 1 and the last ends on the Sunday on/after
     * Dec 31, so the grid is always rectangular. Cells outside the year or in the future
     * carry intensity 0 and are flagged accordingly.
     */
    fun yearColumns(
        year: Int,
        intensityByDate: Map<LocalDate, Int>,
        today: LocalDate,
    ): List<List<GridCell>> {
        val iso = WeekFields.ISO
        val firstOfYear = LocalDate.of(year, 1, 1)
        val lastOfYear = LocalDate.of(year, 12, 31)
        val start = firstOfYear.with(iso.dayOfWeek(), 1L) // Monday on/before Jan 1
        val end = lastOfYear.with(iso.dayOfWeek(), 7L)    // Sunday on/after Dec 31

        val columns = ArrayList<List<GridCell>>()
        var weekStart = start
        while (!weekStart.isAfter(end)) {
            val column = ArrayList<GridCell>(7)
            for (i in 0 until 7) {
                val date = weekStart.plusDays(i.toLong())
                val inYear = !date.isBefore(firstOfYear) && !date.isAfter(lastOfYear)
                val isFuture = date.isAfter(today)
                val intensity = if (inYear && !isFuture) (intensityByDate[date] ?: 0) else 0
                column.add(GridCell(date, intensity, inYear, isFuture))
            }
            columns.add(column)
            weekStart = weekStart.plusWeeks(1)
        }
        return columns
    }

    /** A month label anchored to the column index where that month first appears. */
    data class MonthLabel(val columnIndex: Int, val label: String)

    private val MONTHS = listOf(
        "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec",
    )

    /**
     * For a grid produced by [yearColumns], returns one label per month, anchored to the first
     * column whose in-year top cell (the column's Monday week) falls in that month. Used to render
     * the month axis above The Grid.
     */
    fun monthLabels(columns: List<List<GridCell>>): List<MonthLabel> {
        val labels = ArrayList<MonthLabel>()
        var lastMonth = -1
        columns.forEachIndexed { index, column ->
            val firstInYear = column.firstOrNull { it.inYear } ?: return@forEachIndexed
            val month = firstInYear.date.monthValue
            if (month != lastMonth) {
                labels.add(MonthLabel(index, MONTHS[month - 1]))
                lastMonth = month
            }
        }
        return labels
    }
}
