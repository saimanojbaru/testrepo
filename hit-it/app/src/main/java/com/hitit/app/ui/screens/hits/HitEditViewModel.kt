package com.hitit.app.ui.screens.hits

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.local.entity.HitTaskEntity
import com.hitit.app.data.repository.RepRepository
import com.hitit.app.data.repository.TaskRepository
import com.hitit.app.ui.navigation.Dest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject

enum class DueOption { NONE, TODAY, TOMORROW, WEEK }

data class RepChoice(val id: Long, val name: String, val emoji: String)

data class HitEditState(
    val id: Long = 0,
    val isNew: Boolean = true,
    val title: String = "",
    val notes: String = "",
    val priority: String = "MEDIUM",
    val due: DueOption = DueOption.NONE,
    val isMainTarget: Boolean = false,
    val linkedRepId: Long? = null,
    val reps: List<RepChoice> = emptyList(),
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = title.isNotBlank()
}

@HiltViewModel
class HitEditViewModel @Inject constructor(
    private val taskRepository: TaskRepository,
    private val repRepository: RepRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val taskId: Long = savedStateHandle[Dest.ARG_TASK_ID] ?: Dest.NEW_TASK_ID
    private val today: LocalDate = LocalDate.now()

    private val _state = MutableStateFlow(HitEditState())
    val state: StateFlow<HitEditState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val reps = repRepository.observeActiveReps().first()
                .map { RepChoice(it.id, it.name, it.emoji) }
            val existing = if (taskId != Dest.NEW_TASK_ID) taskRepository.getTask(taskId) else null
            _state.value = if (existing == null) {
                HitEditState(reps = reps)
            } else {
                HitEditState(
                    id = existing.id,
                    isNew = false,
                    title = existing.title,
                    notes = existing.notes,
                    priority = existing.priority,
                    due = dateToDue(existing.dueDate),
                    isMainTarget = existing.mainTargetDate == today,
                    linkedRepId = existing.repId,
                    reps = reps,
                )
            }
        }
    }

    fun onTitle(value: String) = update { it.copy(title = value) }
    fun onNotes(value: String) = update { it.copy(notes = value) }
    fun onPriority(value: String) = update { it.copy(priority = value) }
    fun onDue(value: DueOption) = update { it.copy(due = value) }
    fun onMainTarget(value: Boolean) = update { it.copy(isMainTarget = value) }
    fun onLinkRep(id: Long) = update { it.copy(linkedRepId = if (it.linkedRepId == id) null else id) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            val existing = if (s.isNew) null else taskRepository.getTask(s.id)
            val entity = HitTaskEntity(
                id = s.id,
                title = s.title.trim(),
                notes = s.notes.trim(),
                priority = s.priority,
                dueDate = dueToDate(s.due),
                tagsCsv = existing?.tagsCsv ?: "",
                isDone = existing?.isDone ?: false,
                completedAt = existing?.completedAt,
                mainTargetDate = existing?.mainTargetDate,
                repId = s.linkedRepId,
                sortOrder = existing?.sortOrder ?: 0,
                createdAt = existing?.createdAt ?: Instant.now(),
            )
            val id = taskRepository.saveTask(entity)
            if (s.isMainTarget) taskRepository.makeMainTarget(id, today)
            else taskRepository.clearMainTarget(id)
            _state.value = _state.value.copy(saved = true)
        }
    }

    private fun dueToDate(due: DueOption): LocalDate? = when (due) {
        DueOption.NONE -> null
        DueOption.TODAY -> today
        DueOption.TOMORROW -> today.plusDays(1)
        DueOption.WEEK -> today.plusDays(7)
    }

    private fun dateToDue(date: LocalDate?): DueOption = when {
        date == null -> DueOption.NONE
        date == today -> DueOption.TODAY
        date == today.plusDays(1) -> DueOption.TOMORROW
        else -> DueOption.WEEK
    }

    private inline fun update(block: (HitEditState) -> HitEditState) {
        _state.value = block(_state.value)
    }
}
