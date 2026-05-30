package com.hitit.app.ui.screens.bigplays

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.local.entity.BigPlayEntity
import com.hitit.app.data.repository.BigPlayRepository
import com.hitit.domain.goal.GoalProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.hitit.app.ui.navigation.Dest
import javax.inject.Inject

data class CheckpointUi(val id: Long, val title: String, val done: Boolean)

data class BigPlayDetailState(
    val loaded: Boolean = false,
    val exists: Boolean = false,
    val id: Long = 0,
    val title: String = "",
    val notes: String = "",
    val category: String = "",
    val horizonLabel: String = "",
    val progress: Float = 0f,
    val percent: Int = 0,
    val hasTarget: Boolean = false,
    val currentValue: Double = 0.0,
    val targetValue: Double = 0.0,
    val unit: String = "",
    val checkpoints: List<CheckpointUi> = emptyList(),
    val completed: Boolean = false,
)

@HiltViewModel
class BigPlayDetailViewModel @Inject constructor(
    private val bigPlayRepository: BigPlayRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val planId: Long = savedStateHandle[Dest.ARG_PLAN_ID] ?: Dest.NEW_PLAN_ID

    private var latest: BigPlayEntity? = null

    val state: StateFlow<BigPlayDetailState> = bigPlayRepository.observeOne(planId)
        .map { item ->
            if (item == null) {
                latest = null
                BigPlayDetailState(loaded = true, exists = false)
            } else {
                latest = item.bigPlay
                val fraction = if (item.bigPlay.isCompleted) 1f else item.progressFraction()
                BigPlayDetailState(
                    loaded = true,
                    exists = true,
                    id = item.bigPlay.id,
                    title = item.bigPlay.title,
                    notes = item.bigPlay.notes,
                    category = item.bigPlay.category,
                    horizonLabel = GoalCatalog.horizonLabel(item.bigPlay.horizon),
                    progress = fraction,
                    percent = GoalProgress.percent(fraction),
                    hasTarget = item.bigPlay.targetValue != null,
                    currentValue = item.bigPlay.currentValue,
                    targetValue = item.bigPlay.targetValue ?: 0.0,
                    unit = item.bigPlay.unit,
                    checkpoints = item.checkpoints.map { CheckpointUi(it.id, it.title, it.isDone) },
                    completed = item.bigPlay.isCompleted,
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BigPlayDetailState())

    fun toggleCheckpoint(id: Long, done: Boolean) {
        viewModelScope.launch { bigPlayRepository.toggleCheckpoint(id, done) }
    }

    fun addCheckpoint(title: String) {
        if (title.isBlank()) return
        viewModelScope.launch { bigPlayRepository.addCheckpoint(planId, title) }
    }

    fun removeCheckpoint(id: Long) {
        viewModelScope.launch { bigPlayRepository.removeCheckpoint(id) }
    }

    fun changeValue(delta: Double) {
        val play = latest ?: return
        val next = (play.currentValue + delta).coerceAtLeast(0.0)
        viewModelScope.launch { bigPlayRepository.setCurrentValue(play, next) }
    }

    fun toggleCompleted() {
        val play = latest ?: return
        viewModelScope.launch { bigPlayRepository.toggleCompleted(play, !play.isCompleted) }
    }

    fun deletePlay() {
        val play = latest ?: return
        viewModelScope.launch { bigPlayRepository.deletePlay(play) }
    }
}
