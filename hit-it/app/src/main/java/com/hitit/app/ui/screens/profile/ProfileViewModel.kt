package com.hitit.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.repository.StatsRepository
import com.hitit.app.data.repository.TrophyRepository
import com.hitit.domain.momentum.LevelCurve
import com.hitit.domain.momentum.TierLadder
import com.hitit.domain.trophy.TrophyCatalog
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class TrophyUi(
    val emoji: String,
    val name: String,
    val description: String,
    val unlocked: Boolean,
)

data class TierRowUi(
    val name: String,
    val range: String,
    val reached: Boolean,
    val current: Boolean,
)

data class ProfileUiState(
    val tier: String = "Rookie",
    val level: Int = 1,
    val progress: Float = 0f,
    val momentum: Long = 0,
    val momentumToNext: Long = 0,
    val focusMinutes: Int = 0,
    val checkInCount: Int = 0,
    val tasksCompleted: Int = 0,
    val bestStreak: Int = 0,
    val trophies: List<TrophyUi> = emptyList(),
    val tiers: List<TierRowUi> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
    private val trophyRepository: TrophyRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    init {
        // Persist any newly-earned Trophies whenever the stats change (idempotent).
        viewModelScope.launch {
            statsRepository.observeStats(today).collect { stats ->
                trophyRepository.sync(stats, today)
            }
        }
    }

    val state: StateFlow<ProfileUiState> = combine(
        statsRepository.observeStats(today),
        trophyRepository.observeUnlocked(),
    ) { stats, unlocked ->
        val unlockedIds = unlocked.map { it.id }.toSet()
        ProfileUiState(
            tier = TierLadder.tierFor(stats.level).name,
            level = stats.level,
            progress = LevelCurve.progressToNext(stats.momentum),
            momentum = stats.momentum,
            momentumToNext = LevelCurve.momentumToNext(stats.momentum),
            focusMinutes = stats.totalFocusMinutes,
            checkInCount = stats.checkInCount,
            tasksCompleted = stats.tasksCompleted,
            bestStreak = stats.bestStreak,
            trophies = TrophyCatalog.ALL.map { def ->
                TrophyUi(def.emoji, def.name, def.description, def.id in unlockedIds)
            },
            tiers = TierLadder.TIERS.map { tier ->
                TierRowUi(
                    name = tier.name,
                    range = "Lv ${tier.minLevel}–${tier.maxLevel}",
                    reached = stats.level >= tier.minLevel,
                    current = stats.level in tier.minLevel..tier.maxLevel,
                )
            },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())
}
