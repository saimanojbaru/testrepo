package com.hitit.app.ui.screens.grid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.repository.CheckInRepository
import com.hitit.app.data.repository.RepRepository
import com.hitit.domain.grid.GridAggregator
import com.hitit.domain.grid.GridAggregator.GridCell
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class GridUiState(
    val year: Int = LocalDate.now().year,
    val columns: List<List<GridCell>> = emptyList(),
    val totalHits: Int = 0,
    val activeDays: Int = 0,
    val loading: Boolean = true,
)

@HiltViewModel
class GridViewModel @Inject constructor(
    repRepository: RepRepository,
    checkInRepository: CheckInRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val yearStart: LocalDate = LocalDate.of(today.year, 1, 1)

    val state: StateFlow<GridUiState> = combine(
        repRepository.observeHitsBetween(yearStart, today),
        checkInRepository.observeActiveDates(),
    ) { hits, checkInDates ->
        // Per-day activity = distinct Reps hit + a point for a Check-In that day.
        val activityByDate = HashMap<LocalDate, Int>()
        hits.groupBy { it.date }.forEach { (date, dayHits) ->
            activityByDate[date] = dayHits.map { it.repId }.distinct().size
        }
        checkInDates
            .filter { !it.isBefore(yearStart) && !it.isAfter(today) }
            .forEach { date -> activityByDate[date] = (activityByDate[date] ?: 0) + 1 }

        val intensity = GridAggregator.intensityByDate(activityByDate)
        GridUiState(
            year = today.year,
            columns = GridAggregator.yearColumns(today.year, intensity, today),
            totalHits = hits.sumOf { it.hitCount },
            activeDays = activityByDate.keys.size,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GridUiState())
}
