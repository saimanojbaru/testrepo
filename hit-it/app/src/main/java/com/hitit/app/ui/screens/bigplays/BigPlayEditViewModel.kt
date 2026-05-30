package com.hitit.app.ui.screens.bigplays

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.local.entity.BigPlayEntity
import com.hitit.app.data.repository.BigPlayRepository
import com.hitit.app.ui.navigation.Dest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import javax.inject.Inject

data class BigPlayEditState(
    val id: Long = 0,
    val isNew: Boolean = true,
    val title: String = "",
    val notes: String = "",
    val category: String = "Personal",
    val horizon: String = "YEAR",
    val targetText: String = "",
    val unit: String = "",
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = title.isNotBlank()
}

@HiltViewModel
class BigPlayEditViewModel @Inject constructor(
    private val bigPlayRepository: BigPlayRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val planId: Long = savedStateHandle[Dest.ARG_PLAN_ID] ?: Dest.NEW_PLAN_ID

    private val _state = MutableStateFlow(BigPlayEditState())
    val state: StateFlow<BigPlayEditState> = _state.asStateFlow()

    init {
        if (planId != Dest.NEW_PLAN_ID) {
            viewModelScope.launch {
                bigPlayRepository.getPlay(planId)?.let { play ->
                    _state.value = BigPlayEditState(
                        id = play.id,
                        isNew = false,
                        title = play.title,
                        notes = play.notes,
                        category = play.category,
                        horizon = play.horizon,
                        targetText = play.targetValue?.let { formatTarget(it) } ?: "",
                        unit = play.unit,
                    )
                }
            }
        }
    }

    fun onTitle(value: String) = _state.update { it.copy(title = value) }
    fun onNotes(value: String) = _state.update { it.copy(notes = value) }
    fun onCategory(value: String) = _state.update { it.copy(category = value) }
    fun onHorizon(value: String) = _state.update { it.copy(horizon = value) }
    fun onTarget(value: String) =
        _state.update { it.copy(targetText = value.filter { c -> c.isDigit() || c == '.' }) }
    fun onUnit(value: String) = _state.update { it.copy(unit = value) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            val existing = if (s.isNew) null else bigPlayRepository.getPlay(s.id)
            val entity = BigPlayEntity(
                id = s.id,
                title = s.title.trim(),
                notes = s.notes.trim(),
                category = s.category,
                horizon = s.horizon,
                targetValue = s.targetText.toDoubleOrNull(),
                currentValue = existing?.currentValue ?: 0.0,
                unit = s.unit.trim(),
                deadline = existing?.deadline,
                isCompleted = existing?.isCompleted ?: false,
                completedAt = existing?.completedAt,
                sortOrder = existing?.sortOrder ?: 0,
                createdAt = existing?.createdAt ?: Instant.now(),
            )
            bigPlayRepository.savePlay(entity)
            _state.update { it.copy(saved = true) }
        }
    }

    private fun formatTarget(value: Double): String =
        if (value % 1.0 == 0.0) value.toLong().toString() else value.toString()
}
