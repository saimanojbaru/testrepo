package com.hitit.app.ui.screens.today

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.mapper.toCore
import com.hitit.app.data.repository.CheckInRepository
import com.hitit.app.data.repository.LockInRepository
import com.hitit.app.data.repository.ProfileRepository
import com.hitit.app.data.repository.RepRepository
import com.hitit.app.data.repository.TaskRepository
import com.hitit.app.ui.model.RepUi
import com.hitit.domain.grid.GridAggregator
import com.hitit.domain.grid.GridAggregator.GridCell
import com.hitit.domain.model.ScheduleEvaluator
import com.hitit.domain.momentum.LevelCurve
import com.hitit.domain.momentum.MomentumScore
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
    val momentumScore: Int = 0,
    val momentumLabel: String = "",
    val dateLabel: String = "",
    val checkedIn: Boolean = false,
    val checkInMood: Int? = null,
    val repsDone: Int = 0,
    val repsTotal: Int = 0,
    val hitsDoneToday: Int = 0,
    val focusMinutesToday: Int = 0,
    val miniGrid: List<List<GridCell>> = emptyList(),
    val mainTarget: MainTargetUi? = null,
    val reps: List<TodayRepUi> = emptyList(),
    val loading: Boolean = true,
)

/** One-shot celebration signal for the UI (haptics + confetti). */
data class Celebration(val perfectDay: Boolean, val seq: Long)

@HiltViewModel
class TodayViewModel @Inject constructor(
    private val repRepository: RepRepository,
    private val taskRepository: TaskRepository,
    profileRepository: ProfileRepository,
    private val checkInRepository: CheckInRepository,
    lockInRepository: LockInRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val dateFormat = DateTimeFormatter.ofPattern("EEE, MMM d")

    val state: StateFlow<TodayUiState> = combine(
        repRepository.observeActiveReps(),
        repRepository.observeHitsBetween(today.minusDays(WINDOW_DAYS), today),
        profileRepository.observeMomentumTotal(),
        taskRepository.observeMainTarget(today),
        combine(
            checkInRepository.observeForDate(today),
            taskRepository.observeAll(),
            lockInRepository.observeFocusMinutesOn(today),
        ) { checkIn, tasks, focusMin ->
            Triple(checkIn, tasks, focusMin)
        },
    ) { reps, hits, momentum, mainTarget, (checkIn, tasks, focusMin) ->
        val hitsByRep = hits.groupBy { it.repId }
        val active = reps.filter { ScheduleEvaluator.isActiveOn(it.toCore(), today) }
        val todayReps = active.map { rep ->
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
        val repsDone = todayReps.count { it.met }
        val level = LevelCurve.levelFor(momentum)
        val checkedIn = checkIn != null &&
            (checkIn.morning.isNotBlank() || checkIn.evening.isNotBlank() || checkIn.mood != null)
        val hitsDoneToday = tasks.count { it.isDone }

        // Mini-grid: last ~16 weeks of overall activity (reps hit per day).
        val intensity = GridAggregator.intensityByDate(
            hits.groupBy { it.date }.mapValues { (_, d) -> d.map { it.repId }.distinct().size },
        )
        val allColumns = GridAggregator.yearColumns(today.year, intensity, today)
        val mini = allColumns.takeLast(MINI_WEEKS)

        val score = MomentumScore.score(
            repsScheduled = active.size,
            repsMet = repsDone,
            checkedIn = checkedIn,
            hitsCompletedToday = hitsDoneToday,
            focusMinutesToday = focusMin,
        )

        TodayUiState(
            tier = TierLadder.tierFor(level).name,
            level = level,
            progress = LevelCurve.progressToNext(momentum),
            momentum = momentum,
            momentumScore = score,
            momentumLabel = MomentumScore.label(score),
            dateLabel = today.format(dateFormat),
            checkedIn = checkedIn,
            checkInMood = checkIn?.mood,
            repsDone = repsDone,
            repsTotal = active.size,
            hitsDoneToday = hitsDoneToday,
            focusMinutesToday = focusMin,
            miniGrid = mini,
            mainTarget = mainTarget?.let {
                MainTargetUi(id = it.id, title = it.title, priority = it.priority, done = it.isDone)
            },
            reps = todayReps,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    private val _celebration = kotlinx.coroutines.flow.MutableStateFlow<Celebration?>(null)
    val celebration: StateFlow<Celebration?> = _celebration
    private var seq = 0L

    fun toggle(rep: TodayRepUi) {
        viewModelScope.launch {
            if (rep.met) {
                repRepository.clearHit(rep.id, today)
            } else {
                val result = repRepository.logHit(rep.id, today)
                if (result.met) _celebration.value = Celebration(result.perfectDay, seq++)
            }
        }
    }

    fun consumeCelebration() { _celebration.value = null }

    fun toggleMainTarget(target: MainTargetUi) {
        viewModelScope.launch { taskRepository.setDone(target.id, !target.done, today) }
    }

    private companion object {
        const val WINDOW_DAYS = 400L
        const val MINI_WEEKS = 16
    }
}
