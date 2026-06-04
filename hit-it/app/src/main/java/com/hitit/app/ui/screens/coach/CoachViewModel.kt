package com.hitit.app.ui.screens.coach

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.repository.CoachRepository
import com.hitit.domain.coach.CoachInsight
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class CoachUiState(
    val nudge: CoachInsight? = null,
    val weekly: List<CoachInsight> = emptyList(),
    val llmSummary: String? = null,
    val llmActive: Boolean = false,
    val loading: Boolean = true,
)

@HiltViewModel
class CoachViewModel @Inject constructor(
    private val coachRepository: CoachRepository,
    private val appPreferences: com.hitit.app.data.local.AppPreferences,
) : ViewModel() {

    private val _state = MutableStateFlow(CoachUiState())
    val state: StateFlow<CoachUiState> = _state.asStateFlow()

    val llmEnabled: Boolean get() = appPreferences.llmCoachEnabled
    val modelPath: String get() = appPreferences.llmModelPath.orEmpty()

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            val report = coachRepository.review()
            _state.value = CoachUiState(
                nudge = report.nudge,
                weekly = report.weekly,
                llmSummary = report.llmSummary,
                llmActive = report.llmActive,
                loading = false,
            )
        }
    }

    fun setLlmEnabled(enabled: Boolean) {
        appPreferences.llmCoachEnabled = enabled
        refresh()
    }

    fun setModelPath(path: String) {
        appPreferences.llmModelPath = path.trim().ifBlank { null }
        refresh()
    }
}
