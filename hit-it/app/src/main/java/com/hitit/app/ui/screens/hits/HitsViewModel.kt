package com.hitit.app.ui.screens.hits

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.local.entity.HitTaskEntity
import com.hitit.app.data.repository.TaskRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class HitUi(
    val id: Long,
    val title: String,
    val priority: String,
    val done: Boolean,
    val isMainTarget: Boolean,
    val subtitle: String,
)

data class HitsUiState(
    val open: List<HitUi> = emptyList(),
    val done: List<HitUi> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class HitsViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    val state: StateFlow<HitsUiState> = taskRepository.observeAll()
        .map { tasks ->
            val uis = tasks.map { it.toUi(today) }
            HitsUiState(
                open = uis.filterNot { it.done },
                done = uis.filter { it.done },
                loading = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HitsUiState())

    fun toggleDone(hit: HitUi) {
        viewModelScope.launch { taskRepository.setDone(hit.id, !hit.done, today) }
    }

    fun toggleMainTarget(hit: HitUi) {
        viewModelScope.launch {
            if (hit.isMainTarget) taskRepository.clearMainTarget(hit.id)
            else taskRepository.makeMainTarget(hit.id, today)
        }
    }

    private fun HitTaskEntity.toUi(today: LocalDate): HitUi = HitUi(
        id = id,
        title = title,
        priority = priority,
        done = isDone,
        isMainTarget = mainTargetDate == today,
        subtitle = buildString {
            append(priority.lowercase().replaceFirstChar { it.uppercase() })
            dueDate?.let { append(" · due ").append(it.format(DUE_FORMAT)) }
        },
    )

    private companion object {
        val DUE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d")
    }
}
