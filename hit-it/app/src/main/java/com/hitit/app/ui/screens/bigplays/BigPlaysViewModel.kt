package com.hitit.app.ui.screens.bigplays

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.local.entity.BigPlayWithCheckpoints
import com.hitit.app.data.repository.BigPlayRepository
import com.hitit.domain.goal.GoalProgress
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class BigPlayCardUi(
    val id: Long,
    val title: String,
    val category: String,
    val horizonLabel: String,
    val progress: Float,
    val percent: Int,
    val completed: Boolean,
)

data class BigPlaysUiState(
    val plays: List<BigPlayCardUi> = emptyList(),
    val loading: Boolean = true,
)

/** Combines checkpoint completion (preferred) or the numeric value into a [0,1] progress. */
fun BigPlayWithCheckpoints.progressFraction(): Float {
    if (checkpoints.isNotEmpty()) {
        return GoalProgress.fromCheckpoints(checkpoints.count { it.isDone }, checkpoints.size)
    }
    val target = bigPlay.targetValue
    return if (target != null) GoalProgress.fromValue(bigPlay.currentValue, target) else 0f
}

@HiltViewModel
class BigPlaysViewModel @Inject constructor(
    bigPlayRepository: BigPlayRepository,
) : ViewModel() {

    val state: StateFlow<BigPlaysUiState> = bigPlayRepository.observeAll()
        .map { plays ->
            BigPlaysUiState(
                plays = plays.map { item ->
                    val fraction = if (item.bigPlay.isCompleted) 1f else item.progressFraction()
                    BigPlayCardUi(
                        id = item.bigPlay.id,
                        title = item.bigPlay.title,
                        category = item.bigPlay.category,
                        horizonLabel = GoalCatalog.horizonLabel(item.bigPlay.horizon),
                        progress = fraction,
                        percent = GoalProgress.percent(fraction),
                        completed = item.bigPlay.isCompleted,
                    )
                },
                loading = false,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BigPlaysUiState())
}
