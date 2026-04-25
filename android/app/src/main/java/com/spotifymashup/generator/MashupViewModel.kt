package com.spotifymashup.generator

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotifymashup.generator.api.MashupRequest
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// ── UI state sealed hierarchy ─────────────────────────────────────────────────

sealed class MashupUiState {
    object Idle : MashupUiState()

    data class Loading(
        val message: String,
        val progress: Int,    // 0–100
    ) : MashupUiState()

    data class Ready(
        val jobId: String,
        val fileName: String,
    ) : MashupUiState()

    data class Downloading(val fileName: String) : MashupUiState()

    data class Downloaded(val savedPath: String) : MashupUiState()

    data class Error(val message: String) : MashupUiState()
}

// ── ViewModel ─────────────────────────────────────────────────────────────────

class MashupViewModel : ViewModel() {

    private val repository = MashupRepository()

    private val _state = MutableStateFlow<MashupUiState>(MashupUiState.Idle)
    val state: StateFlow<MashupUiState> = _state.asStateFlow()

    // ── Generate mashup ───────────────────────────────────────────────────────

    fun generateMashup(
        trackA: String,
        trackB: String,
        youtubeOnly: Boolean,
        bpmA: Float?,
        bpmB: Float?,
        keyA: Int?,
        keyB: Int?,
        applyPitchShift: Boolean,
        stemBackend: String,
    ) {
        val request = MashupRequest(
            trackA = trackA.trim(),
            trackB = trackB.trim(),
            youtubeOnly = youtubeOnly,
            bpmA = bpmA,
            bpmB = bpmB,
            keyA = keyA,
            keyB = keyB,
            applyPitchShift = applyPitchShift,
            stemBackend = stemBackend,
        )

        viewModelScope.launch {
            try {
                _state.value = MashupUiState.Loading("Submitting job to server…", 2)

                val jobId = repository.submitJob(request)

                // Build a human-readable file name from the inputs
                val fileName = buildFileName(trackA, trackB)

                val terminal = repository.pollUntilDone(jobId) { job ->
                    _state.value = MashupUiState.Loading(job.message, job.progress)
                }

                when (terminal.status) {
                    "done" -> _state.value = MashupUiState.Ready(jobId, fileName)
                    else -> _state.value = MashupUiState.Error(terminal.message)
                }
            } catch (e: Exception) {
                _state.value = MashupUiState.Error(e.message ?: "Unexpected error")
            }
        }
    }

    // ── Download ──────────────────────────────────────────────────────────────

    fun downloadMashup(context: Context, jobId: String, fileName: String) {
        viewModelScope.launch {
            try {
                _state.value = MashupUiState.Downloading(fileName)
                val savedPath = repository.downloadToMusic(context, jobId, fileName)
                _state.value = MashupUiState.Downloaded(savedPath)
            } catch (e: Exception) {
                _state.value = MashupUiState.Error("Download failed: ${e.message}")
            }
        }
    }

    // ── Reset ─────────────────────────────────────────────────────────────────

    fun reset() {
        _state.value = MashupUiState.Idle
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private fun buildFileName(trackA: String, trackB: String): String {
        fun safe(s: String) = s.replace(Regex("[^A-Za-z0-9_\\- ]"), "").trim().take(30)
        return "Mashup_${safe(trackA)}_vs_${safe(trackB)}.mp3"
    }
}
