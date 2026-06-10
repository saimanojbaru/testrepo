package com.hitit.app.ui.screens.reps

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.local.entity.RepEntity
import com.hitit.app.data.repository.RepRepository
import com.hitit.app.ui.model.RepUi
import com.hitit.app.ui.navigation.Dest
import com.hitit.domain.grid.GridAggregator
import com.hitit.domain.grid.GridAggregator.GridCell
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class RepDetailState(
    val loaded: Boolean = false,
    val exists: Boolean = false,
    val id: Long = 0,
    val name: String = "",
    val emoji: String = "",
    val colorHex: String = "#33E1FF",
    val scheduleLabel: String = "",
    val currentStreak: Int = 0,
    val longestStreak: Int = 0,
    val isResting: Boolean = false,
    val columns: List<List<GridCell>> = emptyList(),
    val isArchived: Boolean = false,
    val sacrificeEligible: Boolean = false,
    val sacrificeXp: Long = 0,
    val sacrificeRelicEmoji: String = "",
    val sacrificeRitualCopy: String = "",
)

@HiltViewModel
class RepDetailViewModel @Inject constructor(
    private val repRepository: RepRepository,
    private val sacrificeRepository: com.hitit.app.data.repository.SacrificeRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val repId: Long = savedStateHandle[Dest.ARG_REP_ID] ?: Dest.NEW_REP_ID
    private val today: LocalDate = LocalDate.now()

    private var latestRep: RepEntity? = null

    val state: StateFlow<RepDetailState> = combine(
        repRepository.observeRep(repId),
        repRepository.observeHits(repId),
        sacrificeRepository.lastSacrificeMonth,
    ) { rep, hits, _ ->
        latestRep = rep
        if (rep == null) {
            RepDetailState(loaded = true, exists = false)
        } else {
            val streak = repRepository.streakFor(rep, hits, today)
            val intensityByDate = hits.associate { hit ->
                hit.date to if (hit.hitCount >= rep.targetCount) 4 else 2
            }
            RepDetailState(
                loaded = true,
                exists = true,
                id = rep.id,
                name = rep.name,
                emoji = rep.emoji,
                colorHex = rep.colorHex,
                scheduleLabel = RepUi.scheduleLabel(rep),
                currentStreak = streak.currentStreak,
                longestStreak = streak.longestStreak,
                isResting = streak.isResting,
                columns = GridAggregator.yearColumns(today.year, intensityByDate, today),
                isArchived = rep.isArchived,
                sacrificeEligible = sacrificeRepository.isEligible(streak.currentStreak, today),
                sacrificeXp = com.hitit.domain.sacrifice.SacrificeEngine.offerFor(streak.currentStreak).xp,
                sacrificeRelicEmoji = com.hitit.domain.sacrifice.SacrificeEngine.offerFor(streak.currentStreak).relicEmoji,
                sacrificeRitualCopy = com.hitit.domain.sacrifice.SacrificeEngine.ritualCopy(
                    streak.currentStreak,
                    com.hitit.domain.sacrifice.SacrificeEngine.offerFor(streak.currentStreak),
                ),
            )
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RepDetailState())

    fun toggleRestMode() {
        val rep = latestRep ?: return
        viewModelScope.launch {
            val active = rep.restModeStart != null &&
                (rep.restModeEnd == null || !today.isAfter(rep.restModeEnd))
            if (active) repRepository.setRestMode(rep.id, null, null)
            else repRepository.setRestMode(rep.id, today, null)
        }
    }

    fun sacrifice() {
        val rep = latestRep ?: return
        viewModelScope.launch {
            sacrificeRepository.sacrifice(rep.id, state.value.currentStreak, today)
        }
    }

    fun setArchived(archived: Boolean) {
        val rep = latestRep ?: return
        viewModelScope.launch { repRepository.setArchived(rep.id, archived) }
    }
}
