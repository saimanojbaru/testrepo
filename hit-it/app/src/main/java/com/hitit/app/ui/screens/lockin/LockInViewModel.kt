package com.hitit.app.ui.screens.lockin

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.repository.LockInRepository
import com.hitit.app.data.repository.RepRepository
import com.hitit.app.lockin.FocusZones
import com.hitit.app.lockin.LockInEngine
import com.hitit.app.lockin.LockInRunState
import com.hitit.app.lockin.LockInService
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class RepChoice(val id: Long, val name: String, val emoji: String)

data class LockInSetupState(
    val presetMinutes: Int = 25,
    val zone: String = FocusZones.DEFAULT,
    val repId: Long? = null,
    val reps: List<RepChoice> = emptyList(),
)

@HiltViewModel
class LockInViewModel @Inject constructor(
    @ApplicationContext private val context: Context,
    lockInRepository: LockInRepository,
    repRepository: RepRepository,
) : ViewModel() {

    val presets = listOf(15, 25, 45, 60, 90)

    private val _setup = MutableStateFlow(LockInSetupState())
    val setup: StateFlow<LockInSetupState> = _setup.asStateFlow()

    val run: StateFlow<LockInRunState> = LockInEngine.state

    val totalFocusMinutes: StateFlow<Int> = lockInRepository.observeTotalFocusMinutes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    val sessionCount: StateFlow<Int> = lockInRepository.observeSessionCount()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)

    init {
        viewModelScope.launch {
            val reps = repRepository.observeActiveReps().first()
                .map { RepChoice(it.id, it.name, it.emoji) }
            _setup.update { it.copy(reps = reps) }
        }
    }

    fun selectPreset(minutes: Int) = _setup.update { it.copy(presetMinutes = minutes) }
    fun selectZone(zone: String) = _setup.update { it.copy(zone = zone) }
    fun toggleRep(id: Long) = _setup.update { it.copy(repId = if (it.repId == id) null else id) }

    fun start() {
        val s = _setup.value
        val label = s.reps.firstOrNull { it.id == s.repId }?.name.orEmpty()
        LockInService.start(context, s.presetMinutes, s.zone, s.repId, null, label)
    }

    fun pause() = LockInService.send(context, LockInService.ACTION_PAUSE)
    fun resume() = LockInService.send(context, LockInService.ACTION_RESUME)
    fun stop() = LockInService.send(context, LockInService.ACTION_STOP)
    fun acknowledgeFinish() = LockInEngine.reset()
}
