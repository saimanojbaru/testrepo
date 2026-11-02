package com.nonverbal.mastery.features.level

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nonverbal.mastery.AppContainer
import com.nonverbal.mastery.data.db.LevelProgress
import com.nonverbal.mastery.data.models.Level
import com.nonverbal.mastery.data.repo.Codec
import com.nonverbal.mastery.data.repo.FieldNote
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class LevelViewModel(private val c: AppContainer, private val level: Level) : ViewModel() {
    private val repo = c.progress

    val progress: StateFlow<LevelProgress?> = repo.progress.map { it[level.id] }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _reflection = MutableStateFlow<String?>(null)
    val reflection: StateFlow<String?> = _reflection.asStateFlow()

    private val _fieldNote = MutableStateFlow<FieldNote?>(null)
    val fieldNote: StateFlow<FieldNote?> = _fieldNote.asStateFlow()

    private val _justMastered = MutableStateFlow(false)
    val justMastered: StateFlow<Boolean> = _justMastered.asStateFlow()

    private var reflectionJob: Job? = null

    init {
        viewModelScope.launch {
            val initial = repo.progress.map { it[level.id] }.first()
            _reflection.value = initial?.reflection.orEmpty()
            _fieldNote.value = Codec.fieldNote(initial)
        }
    }

    /** Called when the reading view is actually shown; an unlocked level becomes "in progress". */
    fun markOpened() {
        viewModelScope.launch { repo.markOpened(level) }
    }

    fun toggleDrill(index: Int, done: Boolean) = viewModelScope.launch { repo.setDrill(level, index, done) }
    fun answer(question: Int, choice: Int) = viewModelScope.launch { repo.answer(level, question, choice) }
    fun retake() = viewModelScope.launch { repo.retakeQuiz(level) }

    fun editReflection(text: String) {
        _reflection.value = text
        reflectionJob?.cancel()
        reflectionJob = viewModelScope.launch {
            delay(500)
            repo.saveReflection(level, text)
        }
    }

    fun editFieldNote(note: FieldNote) { _fieldNote.value = note }

    fun logFieldNote() {
        val note = _fieldNote.value ?: return
        viewModelScope.launch { repo.saveFieldNote(level, note) }
    }

    fun master() = viewModelScope.launch {
        flushReflection()
        if (repo.master(level)) _justMastered.value = true
    }

    private suspend fun flushReflection() {
        reflectionJob?.cancel()
        _reflection.value?.let { repo.saveReflection(level, it) }
    }

    override fun onCleared() {
        // Finish an in-flight reflection save even though this screen is gone.
        val pending = reflectionJob?.isActive == true
        val text = _reflection.value
        if (pending && text != null) c.appScope.launch { repo.saveReflection(level, text) }
        super.onCleared()
    }
}
