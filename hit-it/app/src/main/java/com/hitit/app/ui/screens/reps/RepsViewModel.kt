package com.hitit.app.ui.screens.reps

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.repository.RepRepository
import com.hitit.app.ui.model.RepUi
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class RepListItemUi(
    val id: Long,
    val emoji: String,
    val name: String,
    val colorHex: String,
    val streak: Int,
    val subtitle: String,
    val met: Boolean,
)

data class RepsUiState(
    val reps: List<RepListItemUi> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class RepsViewModel @Inject constructor(
    private val repRepository: RepRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    val state: StateFlow<RepsUiState> = combine(
        repRepository.observeActiveReps(),
        repRepository.observeHitsBetween(today.minusDays(WINDOW_DAYS), today),
    ) { reps, hits ->
        val hitsByRep = hits.groupBy { it.repId }
        val items = reps.map { rep ->
            val repHits = hitsByRep[rep.id].orEmpty()
            val streak = repRepository.streakFor(rep, repHits, today)
            RepListItemUi(
                id = rep.id,
                emoji = rep.emoji,
                name = rep.name,
                colorHex = rep.colorHex,
                streak = streak.currentStreak,
                subtitle = "${RepUi.scheduleLabel(rep)} · ${RepUi.progressText(rep, repHits, today)}",
                met = RepUi.buttonMet(rep, repHits, today),
            )
        }
        RepsUiState(reps = items, loading = false)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RepsUiState())

    fun toggle(item: RepListItemUi) {
        viewModelScope.launch {
            if (item.met) repRepository.clearHit(item.id, today) else repRepository.logHit(item.id, today)
        }
    }

    private companion object {
        const val WINDOW_DAYS = 400L
    }
}
