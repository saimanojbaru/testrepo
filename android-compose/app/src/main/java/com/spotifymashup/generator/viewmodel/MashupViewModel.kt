package com.spotifymashup.generator.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.spotifymashup.generator.data.CompatibilityResponse
import com.spotifymashup.generator.data.HookDto
import com.spotifymashup.generator.data.MashupRepository
import com.spotifymashup.generator.data.MashupRequest
import com.spotifymashup.generator.data.TrendingHookResponse
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class Stage { Input, Progress, Result }

data class TrackState(
    val query: String = "",
    val hooks: List<HookDto> = emptyList(),
    val selectedHook: HookDto? = null,
    val analyzing: Boolean = false,
    val trackName: String? = null,
    val artistName: String? = null,
    val durationMs: Int = 0,
    val error: String? = null,
)

data class MashupUiState(
    val baseUrl: String = "http://10.0.2.2:8000",
    val stage: Stage = Stage.Input,
    val trackA: TrackState = TrackState(),
    val trackB: TrackState = TrackState(),
    val compatibility: CompatibilityResponse? = null,
    val advancedOpen: Boolean = false,
    val youtubeOnly: Boolean = false,
    val applyPitchShift: Boolean = true,
    val bpmOverrideA: String = "",
    val bpmOverrideB: String = "",
    val progress: Int = 0,
    val progressMessage: String = "",
    val jobId: String? = null,
    val resultFilename: String? = null,
    val toast: String? = null,
)

class MashupViewModel(
    private val repo: MashupRepository = MashupRepository(),
) : ViewModel() {

    private val _state = MutableStateFlow(MashupUiState())
    val state: StateFlow<MashupUiState> = _state.asStateFlow()

    fun setBaseUrl(url: String) {
        _state.update { it.copy(baseUrl = url) }
        repo.baseUrl = url.ifEmpty { "http://10.0.2.2:8000" }
    }

    fun setQuery(forA: Boolean, q: String) = _state.update {
        if (forA) it.copy(trackA = it.trackA.copy(query = q))
        else it.copy(trackB = it.trackB.copy(query = q))
    }

    fun setYoutubeOnly(v: Boolean) = _state.update { it.copy(youtubeOnly = v) }
    fun setPitchShift(v: Boolean) = _state.update { it.copy(applyPitchShift = v) }
    fun setBpmOverrideA(v: String) = _state.update { it.copy(bpmOverrideA = v) }
    fun setBpmOverrideB(v: String) = _state.update { it.copy(bpmOverrideB = v) }
    fun toggleAdvanced() = _state.update { it.copy(advancedOpen = !it.advancedOpen) }
    fun clearToast() = _state.update { it.copy(toast = null) }

    fun findHooks(forA: Boolean) {
        val track = if (forA) _state.value.trackA else _state.value.trackB
        if (track.query.isBlank()) {
            _state.update { it.copy(toast = "Paste a Spotify URL or song name first") }
            return
        }
        update(forA) { it.copy(analyzing = true, error = null) }
        viewModelScope.launch {
            try {
                val r: TrendingHookResponse = repo.trendingHook(track.query, topK = 4)
                update(forA) {
                    it.copy(
                        analyzing = false,
                        hooks = r.hooks,
                        trackName = r.trackName,
                        artistName = r.artistName,
                        durationMs = r.durationMs,
                    )
                }
            } catch (e: Exception) {
                update(forA) { it.copy(analyzing = false, error = e.message) }
                _state.update { it.copy(toast = "Couldn't analyse: ${e.message?.take(120)}") }
            }
        }
    }

    fun selectHook(forA: Boolean, h: HookDto) {
        update(forA) { it.copy(selectedHook = h) }
        if (_state.value.trackA.selectedHook != null && _state.value.trackB.selectedHook != null) {
            checkCompatibility()
        }
    }

    private fun checkCompatibility() {
        val a = _state.value.trackA.query.trim()
        val b = _state.value.trackB.query.trim()
        if (a.isEmpty() || b.isEmpty()) return
        viewModelScope.launch {
            try {
                val c = repo.compatibility(a, b)
                _state.update { it.copy(compatibility = c) }
            } catch (e: Exception) {
                _state.update { it.copy(toast = "Compatibility check failed: ${e.message?.take(80)}") }
            }
        }
    }

    fun generate() {
        val s = _state.value
        if (s.trackA.query.isBlank() || s.trackB.query.isBlank()) {
            _state.update { it.copy(toast = "Fill in both tracks") }
            return
        }
        val req = MashupRequest(
            trackA = s.trackA.query.trim(),
            trackB = s.trackB.query.trim(),
            youtubeOnly = s.youtubeOnly,
            bpmA = s.bpmOverrideA.toDoubleOrNull(),
            bpmB = s.bpmOverrideB.toDoubleOrNull(),
            applyPitchShift = s.applyPitchShift,
            hookAStartMs = s.trackA.selectedHook?.startMs,
            hookAEndMs = s.trackA.selectedHook?.endMs,
            hookBStartMs = s.trackB.selectedHook?.startMs,
            hookBEndMs = s.trackB.selectedHook?.endMs,
        )
        _state.update {
            it.copy(stage = Stage.Progress, progress = 0, progressMessage = "Connecting…")
        }
        viewModelScope.launch {
            try {
                val job = repo.createMashup(req)
                _state.update { it.copy(jobId = job.jobId) }
                pollJob(job.jobId)
            } catch (e: Exception) {
                _state.update {
                    it.copy(stage = Stage.Input, toast = "Couldn't start: ${e.message?.take(120)}")
                }
            }
        }
    }

    private suspend fun pollJob(jobId: String) {
        while (true) {
            try {
                val j = repo.pollJob(jobId)
                _state.update {
                    it.copy(progress = j.progress, progressMessage = j.message)
                }
                when (j.status) {
                    "done" -> {
                        _state.update {
                            it.copy(
                                stage = Stage.Result,
                                resultFilename = "Mashup_${jobId.take(8)}.mp3",
                            )
                        }
                        return
                    }
                    "failed" -> {
                        _state.update {
                            it.copy(stage = Stage.Input, toast = "Mashup failed: ${j.message}")
                        }
                        return
                    }
                }
                delay(3000L)
            } catch (e: Exception) {
                _state.update {
                    it.copy(stage = Stage.Input, toast = "Network error: ${e.message?.take(120)}")
                }
                return
            }
        }
    }

    fun reset() = _state.update {
        MashupUiState(baseUrl = it.baseUrl).also { repo.baseUrl = it.baseUrl }
    }

    fun downloadUrl(): String = repo.downloadUrl(_state.value.jobId ?: return "")

    private fun update(forA: Boolean, transform: (TrackState) -> TrackState) {
        _state.update {
            if (forA) it.copy(trackA = transform(it.trackA))
            else it.copy(trackB = transform(it.trackB))
        }
    }
}
