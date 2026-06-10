package com.hitit.app.ui.screens.yoga

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.repository.YogaRepository
import com.hitit.app.ui.navigation.Dest
import com.hitit.domain.pose.PoseLandmark
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Shared brain for the Pose Studio (flavor-agnostic — no camera/MediaPipe here): loads the
 * reference pose for practice mode, saves captured references, logs finished sessions. The full
 * flavor's camera UI drives it; the lite stub never needs it.
 */
@HiltViewModel
class PoseStudioViewModel @Inject constructor(
    private val yogaRepository: YogaRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    val poseId: Long = savedStateHandle[Dest.ARG_POSE_ID] ?: Dest.NEW_POSE_ID

    /** Capture mode (no pose yet) vs practice mode (grading against [reference]). */
    val isCapture: Boolean = poseId <= 0

    private val _reference = MutableStateFlow<List<PoseLandmark>?>(null)
    val reference: StateFlow<List<PoseLandmark>?> = _reference.asStateFlow()

    private val _poseName = MutableStateFlow("")
    val poseName: StateFlow<String> = _poseName.asStateFlow()

    private val _sessionLogged = MutableStateFlow(false)
    val sessionLogged: StateFlow<Boolean> = _sessionLogged.asStateFlow()

    init {
        if (!isCapture) {
            viewModelScope.launch {
                _reference.value = yogaRepository.reference(poseId)
                _poseName.value = yogaRepository.poseName(poseId) ?: ""
            }
        }
    }

    fun saveReference(name: String, landmarks: List<PoseLandmark>, onSaved: () -> Unit) {
        if (name.isBlank() || landmarks.isEmpty()) return
        viewModelScope.launch {
            yogaRepository.savePose(name, landmarks)
            onSaved()
        }
    }

    /** Log the finished practice once (idempotent per studio visit). */
    fun logSession(bestScore: Int, holdSeconds: Int) {
        if (_sessionLogged.value || isCapture) return
        _sessionLogged.value = true
        viewModelScope.launch { yogaRepository.logSession(poseId, bestScore, holdSeconds) }
    }
}
