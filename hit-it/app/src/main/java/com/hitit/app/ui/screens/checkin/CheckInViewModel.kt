package com.hitit.app.ui.screens.checkin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.repository.CheckInRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class CheckInForm(
    val morning: String = "",
    val evening: String = "",
    val mood: Int = 5,
    val energy: Int = 5,
    val loaded: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class CheckInViewModel @Inject constructor(
    private val checkInRepository: CheckInRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    val dateLabel: String = today.format(DateTimeFormatter.ofPattern("EEE, MMM d"))

    private val _form = MutableStateFlow(CheckInForm())
    val form: StateFlow<CheckInForm> = _form.asStateFlow()

    init {
        viewModelScope.launch {
            val existing = checkInRepository.getForDate(today)
            _form.value = if (existing == null) {
                CheckInForm(loaded = true)
            } else {
                CheckInForm(
                    morning = existing.morning,
                    evening = existing.evening,
                    mood = existing.mood ?: 5,
                    energy = existing.energy ?: 5,
                    loaded = true,
                )
            }
        }
    }

    fun onMorning(value: String) = _form.update { it.copy(morning = value) }
    fun onEvening(value: String) = _form.update { it.copy(evening = value) }
    fun onMood(value: Int) = _form.update { it.copy(mood = value.coerceIn(1, 10)) }
    fun onEnergy(value: Int) = _form.update { it.copy(energy = value.coerceIn(1, 10)) }

    fun save() {
        val f = _form.value
        viewModelScope.launch {
            checkInRepository.save(today, f.morning, f.evening, f.mood, f.energy)
            _form.update { it.copy(saved = true) }
        }
    }
}
