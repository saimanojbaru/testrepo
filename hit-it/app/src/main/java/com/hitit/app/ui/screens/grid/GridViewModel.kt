package com.hitit.app.ui.screens.grid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.repository.RepRepository
import com.hitit.domain.grid.GridAggregator
import com.hitit.domain.grid.GridAggregator.GridCell
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
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
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val yearStart: LocalDate = LocalDate.of(today.year, 1, 1)

    val state: StateFlow<GridUiState> = repRepository
        .observeHitsBetween(yearStart, today)
        .map { hits ->
            val distinctRepsByDate = hits
                .groupBy { it.date }
                .mapValues { (_, dayHits) -> dayHits.map { it.repId }.distinct().size }
            val intensity = GridAggregator.intensityByDate(distinctRepsByDate)
            GridUiState(
                year = today.year,
                columns = GridAggregator.yearColumns(today.year, intensity, today),
                totalHits = hits.sumOf { it.hitCount },
                activeDays = distinctRepsByDate.keys.size,
                loading = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GridUiState())
}
