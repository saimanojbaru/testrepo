package com.hitit.app.ui.screens.bodyflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.repository.BodyRepository
import com.hitit.app.data.repository.CheckInRepository
import com.hitit.domain.vibe.VibeScore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class FoodEntryUi(
    val id: Long,
    val description: String,
    val kcal: Int,
)

data class BodyFlowUiState(
    val bodyPulse: Int = 0,
    val pulseLabel: String = "",
    val checkedInToday: Boolean = false,
    val mood: Int? = null,
    val todayKcal: Int = 0,
    val entries: List<FoodEntryUi> = emptyList(),
    val foodDaysLast7: Int = 0,
    val started: Boolean = false,
    val loading: Boolean = true,
)

@HiltViewModel
class BodyFlowViewModel @Inject constructor(
    private val bodyRepository: BodyRepository,
    checkInRepository: CheckInRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    val state: StateFlow<BodyFlowUiState> = combine(
        bodyRepository.observeToday(today),
        bodyRepository.observeFoodDaysLast7(today),
        bodyRepository.observeHasAnyLogs(),
        checkInRepository.observeForDate(today),
    ) { entries, days7, total, checkIn ->
        val checkedIn = checkIn != null &&
            (checkIn.morning.isNotBlank() || checkIn.evening.isNotBlank() || checkIn.mood != null)
        val pulse = VibeScore.bodyPulse(days7, checkedIn)
        BodyFlowUiState(
            bodyPulse = pulse,
            pulseLabel = VibeScore.label(pulse),
            checkedInToday = checkedIn,
            mood = checkIn?.mood,
            todayKcal = entries.sumOf { it.kcal },
            entries = entries.map { FoodEntryUi(it.id, it.description, it.kcal) },
            foodDaysLast7 = days7,
            started = total > 0,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyFlowUiState())

    fun logFood(description: String, kcal: Int) {
        if (description.isBlank() || kcal <= 0) return
        viewModelScope.launch { bodyRepository.log(description, kcal, today) }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { bodyRepository.delete(id) }
    }
}
