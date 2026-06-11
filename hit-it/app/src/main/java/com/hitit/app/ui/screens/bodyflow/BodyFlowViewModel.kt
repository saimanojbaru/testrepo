package com.hitit.app.ui.screens.bodyflow

import androidx.health.connect.client.HealthConnectClient
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.repository.BodyRepository
import com.hitit.app.data.repository.CheckInRepository
import com.hitit.app.data.repository.HealthRepository
import com.hitit.domain.vibe.VibeScore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
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
    val photoPath: String? = null,
)

/** Health Connect connection states the card renders. */
enum class HcState { UNAVAILABLE, UPDATE_REQUIRED, DISCONNECTED, CONNECTED }

data class BodySignalsUi(
    val hcState: HcState = HcState.UNAVAILABLE,
    val steps: Long? = null,
    val sleepMinutes: Long? = null,
    val bpm: Long? = null,
)

data class BodyFlowUiState(
    val bodyPulse: Int = 0,
    val pulseLabel: String = "",
    val checkedInToday: Boolean = false,
    val mood: Int? = null,
    val todayKcal: Int = 0,
    val entries: List<FoodEntryUi> = emptyList(),
    val foodDaysLast7: Int = 0,
    val signals: BodySignalsUi = BodySignalsUi(),
    val started: Boolean = false,
    val loading: Boolean = true,
)

@HiltViewModel
class BodyFlowViewModel @Inject constructor(
    private val bodyRepository: BodyRepository,
    private val healthRepository: HealthRepository,
    checkInRepository: CheckInRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val signals = MutableStateFlow(BodySignalsUi())

    /** Photo staged for the NEXT food log (app-private copy already made). */
    val pendingPhotoPath = MutableStateFlow<String?>(null)

    /** Copy a picked gallery photo into private storage and stage it for the next log. */
    fun attachPhoto(uri: android.net.Uri) {
        viewModelScope.launch { pendingPhotoPath.value = bodyRepository.importPhoto(uri) }
    }

    fun clearPendingPhoto() { pendingPhotoPath.value = null }

    /** The HC permission set, for the screen's request launcher. */
    val healthPermissions: Set<String> get() = healthRepository.permissions

    val state: StateFlow<BodyFlowUiState> = combine(
        bodyRepository.observeToday(today),
        bodyRepository.observeFoodDaysLast7(today),
        bodyRepository.observeHasAnyLogs(),
        checkInRepository.observeForDate(today),
        signals,
    ) { entries, days7, total, checkIn, hc ->
        val checkedIn = checkIn != null &&
            (checkIn.morning.isNotBlank() || checkIn.evening.isNotBlank() || checkIn.mood != null)
        val pulse = VibeScore.bodyPulse(days7, checkedIn)
        BodyFlowUiState(
            bodyPulse = pulse,
            pulseLabel = VibeScore.label(pulse),
            checkedInToday = checkedIn,
            mood = checkIn?.mood,
            todayKcal = entries.sumOf { it.kcal },
            entries = entries.map { FoodEntryUi(it.id, it.description, it.kcal, it.photoPath) },
            foodDaysLast7 = days7,
            signals = hc,
            started = total > 0,
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BodyFlowUiState())

    init {
        refreshSignals()
    }

    /** Re-check HC availability/permissions and read fresh signals (also called after a grant). */
    fun refreshSignals() {
        viewModelScope.launch {
            val status = healthRepository.sdkStatus()
            signals.value = when (status) {
                HealthConnectClient.SDK_AVAILABLE -> {
                    if (healthRepository.hasAllPermissions()) {
                        val s = healthRepository.readSignals(today)
                        BodySignalsUi(HcState.CONNECTED, s.stepsToday, s.sleepMinutesLastNight, s.latestBpm)
                    } else {
                        BodySignalsUi(HcState.DISCONNECTED)
                    }
                }
                HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> BodySignalsUi(HcState.UPDATE_REQUIRED)
                else -> BodySignalsUi(HcState.UNAVAILABLE)
            }
        }
    }

    fun logFood(description: String, kcal: Int) {
        if (description.isBlank() || kcal <= 0) return
        val photo = pendingPhotoPath.value
        pendingPhotoPath.value = null
        viewModelScope.launch { bodyRepository.log(description, kcal, today, photo) }
    }

    fun deleteEntry(id: Long) {
        viewModelScope.launch { bodyRepository.delete(id) }
    }
}
