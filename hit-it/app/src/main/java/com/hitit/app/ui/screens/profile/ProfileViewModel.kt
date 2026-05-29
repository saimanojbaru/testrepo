package com.hitit.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.repository.ProfileRepository
import com.hitit.domain.momentum.LevelCurve
import com.hitit.domain.momentum.TierLadder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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
    val trophies: List<TrophyUi> = emptyList(),
    val tiers: List<TierRowUi> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    profileRepository: ProfileRepository,
) : ViewModel() {

    val state: StateFlow<ProfileUiState> = profileRepository
        .observeMomentumTotal()
        .map { momentum ->
            val level = LevelCurve.levelFor(momentum)
            ProfileUiState(
                tier = TierLadder.tierFor(level).name,
                level = level,
                progress = LevelCurve.progressToNext(momentum),
                momentum = momentum,
                momentumToNext = LevelCurve.momentumToNext(momentum),
                trophies = TROPHIES.map { def ->
                    TrophyUi(def.emoji, def.name, def.description, def.predicate(level, momentum))
                },
                tiers = TierLadder.TIERS.map { tier ->
                    TierRowUi(
                        name = tier.name,
                        range = "Lv ${tier.minLevel}–${tier.maxLevel}",
                        reached = level >= tier.minLevel,
                        current = level in tier.minLevel..tier.maxLevel,
                    )
                },
                loading = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ProfileUiState())

    private data class TrophyDef(
        val emoji: String,
        val name: String,
        val description: String,
        val predicate: (level: Int, momentum: Long) -> Boolean,
    )

    private companion object {
        val TROPHIES = listOf(
            TrophyDef("🌱", "First Step", "Earn your first Momentum") { _, m -> m > 0 },
            TrophyDef("🔥", "Warmed Up", "Reach Level 5") { l, _ -> l >= 5 },
            TrophyDef("💪", "Contender", "Reach Level 26") { l, _ -> l >= 26 },
            TrophyDef("⭐", "All-Star", "Reach Level 51") { l, _ -> l >= 51 },
            TrophyDef("👑", "Champion", "Reach Level 76") { l, _ -> l >= 76 },
            TrophyDef("🐐", "G.O.A.T.", "Reach Level 100") { l, _ -> l >= 100 },
        )
    }
}
