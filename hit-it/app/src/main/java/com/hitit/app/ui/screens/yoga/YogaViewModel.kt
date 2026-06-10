package com.hitit.app.ui.screens.yoga

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.repository.YogaRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class PoseUi(val id: Long, val name: String)

data class SessionUi(
    val poseName: String,
    val bestScore: Int,
    val holdSeconds: Int,
    val dateLabel: String,
)

data class YogaUiState(
    val poses: List<PoseUi> = emptyList(),
    val sessions: List<SessionUi> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class YogaViewModel @Inject constructor(
    private val yogaRepository: YogaRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    val state: StateFlow<YogaUiState> = combine(
        yogaRepository.observePoses(),
        yogaRepository.observeRecentSessions(),
    ) { poses, sessions ->
        val namesById = poses.associate { it.id to it.name }
        YogaUiState(
            poses = poses.map { PoseUi(it.id, it.name) },
            sessions = sessions.map { s ->
                SessionUi(
                    poseName = namesById[s.poseId] ?: "(deleted pose)",
                    bestScore = s.bestScore,
                    holdSeconds = s.holdSeconds,
                    dateLabel = if (s.date == today) "today" else "${s.date.dayOfMonth}/${s.date.monthValue}",
                )
            },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), YogaUiState())

    fun deletePose(id: Long) {
        viewModelScope.launch { yogaRepository.deletePose(id) }
    }
}
