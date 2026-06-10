package com.hitit.app.ui.screens.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.repository.StatsRepository
import com.hitit.app.data.repository.TrophyRepository
import com.hitit.domain.flame.LifeFlame
import com.hitit.domain.identity.IdentityCatalog
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

data class IdentityUi(
    val emoji: String,
    val name: String,
    val description: String,
    val bonusPercent: Int,
    val unlocked: Boolean,
)

data class ProfileUiState(
    val vibeScore: Int = 0,
    val vibeAvatar: String = "😴",
    val vibeLabel: String = "",
    val habitPulse: Int? = null,
    val bodyPulse: Int? = null,
    val moneyPulse: Int? = null,
    val tier: String = "Rookie",
    val level: Int = 1,
    val progress: Float = 0f,
    val momentum: Long = 0,
    val momentumToNext: Long = 0,
    val focusMinutes: Int = 0,
    val checkInCount: Int = 0,
    val tasksCompleted: Int = 0,
    val bestStreak: Int = 0,
    val flameLevel: Int = 1,
    val trophies: List<TrophyUi> = emptyList(),
    val identities: List<IdentityUi> = emptyList(),
    val tiers: List<TierRowUi> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class ProfileViewModel @Inject constructor(
    private val statsRepository: StatsRepository,
    private val trophyRepository: TrophyRepository,
    private val identityRepository: com.hitit.app.data.repository.IdentityRepository,
    private val demoSeeder: com.hitit.app.data.DemoSeeder,
    ledgerRepository: com.hitit.app.data.repository.LedgerRepository,
    bodyRepository: com.hitit.app.data.repository.BodyRepository,
    moneyRepository: com.hitit.app.data.repository.MoneyRepository,
    checkInRepository: com.hitit.app.data.repository.CheckInRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    /** The three Vibe pillars; a pillar is null until the user has touched it (weight redistributes). */
    private data class Pillars(val habit: Int?, val body: Int?, val money: Int?)

    private val pillars = combine(
        // Habit pulse: average finalized Momentum over the last week of the strict ledger.
        ledgerRepository.observeRecent(7, today),
        combine(
            bodyRepository.observeFoodDaysLast7(today),
            bodyRepository.observeHasAnyLogs(),
            checkInRepository.observeForDate(today),
        ) { days7, anyFood, checkIn ->
            val checkedIn = checkIn != null &&
                (checkIn.morning.isNotBlank() || checkIn.evening.isNotBlank() || checkIn.mood != null)
            if (anyFood == 0 && !checkedIn) null else com.hitit.domain.vibe.VibeScore.bodyPulse(days7, checkedIn)
        },
        combine(
            moneyRepository.observeSpendDaysLast7(today),
            moneyRepository.observeWeekBurnerSpend(today),
            moneyRepository.observeHasAnyLogs(),
        ) { days7, burnerSpend, anySpend ->
            if (anySpend == 0) null
            else {
                val budget = moneyRepository.burnerBudgetPaise
                val utilization = if (budget > 0) (burnerSpend ?: 0L).toFloat() / budget else null
                com.hitit.domain.vibe.VibeScore.moneyPulse(days7, utilization)
            }
        },
    ) { ledger, body, money ->
        val habit = if (ledger.isEmpty()) null else ledger.map { it.momentumScore }.average().toInt()
        Pillars(habit, body, money)
    }

    init {
        // Persist any newly-earned Trophies and Identities whenever the stats change (idempotent).
        viewModelScope.launch {
            statsRepository.observeStats(today).collect { stats ->
                trophyRepository.sync(stats, today)
                identityRepository.sync(stats, today)
            }
        }
    }

    /** Wipe all demo/sample data for a clean slate (the "Clean Slate" reviewer flow). */
    fun clearAllData() {
        viewModelScope.launch { demoSeeder.clear() }
    }

    val state: StateFlow<ProfileUiState> = combine(
        statsRepository.observeStats(today),
        trophyRepository.observeUnlocked(),
        identityRepository.observeUnlocked(),
        pillars,
    ) { stats, unlocked, unlockedIdentities, vibe ->
        val unlockedIds = unlocked.map { it.id }.toSet()
        val identityIds = unlockedIdentities.map { it.id }.toSet()
        val vibeScore = com.hitit.domain.vibe.VibeScore.compose(vibe.habit, vibe.body, vibe.money)
        ProfileUiState(
            vibeScore = vibeScore,
            vibeAvatar = com.hitit.domain.vibe.VibeScore.avatarFor(vibeScore),
            vibeLabel = com.hitit.domain.vibe.VibeScore.label(vibeScore),
            habitPulse = vibe.habit,
            bodyPulse = vibe.body,
            moneyPulse = vibe.money,
            tier = TierLadder.tierFor(stats.level).name,
            level = stats.level,
            progress = LevelCurve.progressToNext(stats.momentum),
            momentum = stats.momentum,
            momentumToNext = LevelCurve.momentumToNext(stats.momentum),
            focusMinutes = stats.totalFocusMinutes,
            checkInCount = stats.checkInCount,
            tasksCompleted = stats.tasksCompleted,
            bestStreak = stats.bestStreak,
            flameLevel = LifeFlame.levelFor(score = 0, bestStreak = stats.bestStreak),
            trophies = TrophyCatalog.ALL.map { def ->
                TrophyUi(def.emoji, def.name, def.description, def.id in unlockedIds)
            },
            identities = IdentityCatalog.ALL.map { def ->
                IdentityUi(def.emoji, def.name, def.description, def.bonusPercent, def.id in identityIds)
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
