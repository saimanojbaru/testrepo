package com.hitit.app.ui.screens.locker

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.local.entity.LockerNoteEntity
import com.hitit.app.data.repository.LockerRepository
import com.hitit.app.ui.navigation.Dest
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class LockerForm(
    val title: String = "",
    val body: String = "",
    val loaded: Boolean = false,
)

@HiltViewModel
class LockerViewModel @Inject constructor(
    private val lockerRepository: LockerRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val noteId: Long = savedStateHandle[Dest.ARG_NOTE_ID] ?: Dest.ROOT_NOTE_ID

    /** The id of the note currently open; null = the root level. */
    val currentId: Long? = if (noteId < 0) null else noteId
    val isRoot: Boolean = currentId == null

    private val _form = MutableStateFlow(LockerForm())
    val form: StateFlow<LockerForm> = _form.asStateFlow()

    private val _deleted = MutableStateFlow(false)
    val deleted: StateFlow<Boolean> = _deleted.asStateFlow()

    val children: StateFlow<List<LockerNoteEntity>> =
        lockerRepository.observeChildren(currentId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        val id = currentId
        if (id == null) {
            _form.value = LockerForm(loaded = true)
        } else {
            viewModelScope.launch {
                val note = lockerRepository.getNote(id)
                _form.value = if (note == null) {
                    LockerForm(loaded = true)
                } else {
                    LockerForm(title = note.title, body = note.body, loaded = true)
                }
            }
        }
    }

    fun onTitle(value: String) = _form.update { it.copy(title = value) }
    fun onBody(value: String) = _form.update { it.copy(body = value) }

    fun save() {
        val id = currentId ?: return
        val f = _form.value
        viewModelScope.launch {
            val existing = lockerRepository.getNote(id) ?: return@launch
            lockerRepository.saveNote(
                existing.copy(title = f.title.ifBlank { "Untitled" }, body = f.body),
            )
        }
    }

    fun addChild() {
        viewModelScope.launch { lockerRepository.createNote(parentId = currentId) }
    }

    fun deleteCurrent() {
        val id = currentId ?: return
        viewModelScope.launch {
            val existing = lockerRepository.getNote(id) ?: return@launch
            lockerRepository.deleteNote(existing)
            _deleted.value = true
        }
    }
}
