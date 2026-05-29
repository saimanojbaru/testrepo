package com.hitit.app.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.mapper.toCore
import com.hitit.app.data.repository.ProfileRepository
import com.hitit.app.data.repository.RepRepository
import com.hitit.app.data.repository.TaskRepository
import com.hitit.app.ui.model.RepUi
import com.hitit.domain.model.ScheduleEvaluator
import com.hitit.domain.momentum.LevelCurve
import com.hitit.domain.momentum.TierLadder
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class TodayRepUi(
    val id: Long,
    val emoji: String,
    val name: String,
    val colorHex: String,
    val streak: Int,
    val progressText: String,
    val met: Boolean,
)

data class MainTargetUi(
    val id: Long,
    val title: String,
    val priority: String,
    val done: Boolean,
)

data class TodayUiState(
    val tier: String = "Rookie",
    val level: Int = 1,
    val progress: Float = 0f,
    val momentum: Long = 0,
    val dateLabel: String = "",
    val mainTarget: MainTargetUi? = null,
    val reps: List<TodayRepUi> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repRepository: RepRepository,
    private val taskRepository: TaskRepository,
    profileRepository: ProfileRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d")

    val state: StateFlow<TodayUiState> = combine(
        repRepository.observeActiveReps(),
        repRepository.observeHitsBetween(today.minusDays(WINDOW_DAYS), today),
        profileRepository.observeMomentumTotal(),
        taskRepository.observeMainTarget(today),
    ) { reps, hits, momentum, mainTarget ->
        val hitsByRep = hits.groupBy { it.repId }
        val todayReps = reps
            .filter { ScheduleEvaluator.isActiveOn(it.toCore(), today) }
            .map { rep ->
                val repHits = hitsByRep[rep.id].orEmpty()
                val streak = repRepository.streakFor(rep, repHits, today)
                TodayRepUi(
                    id = rep.id,
                    emoji = rep.emoji,
                    name = rep.name,
                    colorHex = rep.colorHex,
                    streak = streak.currentStreak,
                    progressText = RepUi.progressText(rep, repHits, today),
                    met = RepUi.buttonMet(rep, repHits, today),
                )
            }
        val level = LevelCurve.levelFor(momentum)
        TodayUiState(
            tier = TierLadder.tierFor(level).name,
            level = level,
            progress = LevelCurve.progressToNext(momentum),
            momentum = momentum,
            dateLabel = today.format(dateFormat),
            mainTarget = mainTarget?.let {
                MainTargetUi(id = it.id, title = it.title, priority = it.priority, done = it.isDone)
            },
            reps = todayReps,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun toggle(rep: TodayRepUi) {
        viewModelScope.launch {
            if (rep.met) repRepository.clearHit(rep.id, today) else repRepository.logHit(rep.id, today)
        }
    }

    fun toggleMainTarget(target: MainTargetUi) {
        viewModelScope.launch { taskRepository.setDone(target.id, !target.done, today) }
    }

    private companion object {
        const val WINDOW_DAYS = 400L
    }
}
