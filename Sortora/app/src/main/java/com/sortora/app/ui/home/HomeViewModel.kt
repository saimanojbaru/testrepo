package com.sortora.app.ui.home

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortora.app.R
import com.sortora.app.domain.model.MediaFile
import com.sortora.app.domain.model.MediaKind
import com.sortora.app.domain.model.SortSession
import com.sortora.app.domain.repository.BackgroundJobs
import com.sortora.app.domain.repository.MediaSourceRepository
import com.sortora.app.domain.repository.SessionRepository
import com.sortora.app.domain.repository.SettingsRepository
import com.sortora.app.domain.usecase.SetDestinationUseCase
import com.sortora.app.domain.usecase.StartSortSessionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SourceSelection(
    val label: String,
    val files: List<MediaFile>,
    val treeUri: String? = null,
) {
    val photoCount: Int get() = files.count { it.kind == MediaKind.IMAGE }
    val videoCount: Int get() = files.count { it.kind == MediaKind.VIDEO }
}

data class HomeUiState(
    val selection: SourceSelection? = null,
    val scanning: Boolean = false,
    val starting: Boolean = false,
    val destinationLabel: String? = null,
    val destinationReady: Boolean = false,
    val includeSubfolders: Boolean = true,
    val resumable: SortSession? = null,
) {
    val canAnalyze: Boolean get() = !scanning && !starting && (selection?.files?.isNotEmpty() == true)
}

sealed interface HomeEvent {
    data class SessionStarted(val sessionId: Long) : HomeEvent
    data class Message(@StringRes val text: Int) : HomeEvent
}

private data class LocalState(val selection: SourceSelection? = null, val scanning: Boolean = false, val starting: Boolean = false)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val settings: SettingsRepository,
    private val sources: MediaSourceRepository,
    private val sessions: SessionRepository,
    private val jobs: BackgroundJobs,
    private val startSession: StartSortSessionUseCase,
    private val setDestination: SetDestinationUseCase,
) : ViewModel() {

    private val local = MutableStateFlow(LocalState())
    private val events = Channel<HomeEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    val state: StateFlow<HomeUiState> = combine(local, settings.settings, sessions.observeResumableSession()) { local, config, resumable ->
        val destination = config.destinationUri
        HomeUiState(
            selection = local.selection,
            scanning = local.scanning,
            starting = local.starting,
            destinationLabel = config.destinationLabel,
            destinationReady = destination != null && sources.hasTreeAccess(destination, needWrite = true),
            includeSubfolders = config.analysis.includeSubfolders,
            resumable = resumable,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiState())

    fun onFilesPicked(uris: List<Uri>) {
        if (uris.isEmpty()) return
        scan {
            val files = sources.resolveFiles(uris.map(Uri::toString))
            SourceSelection(label = "", files = files)
        }
    }

    fun onFolderPicked(uri: Uri?) {
        uri ?: return
        val tree = uri.toString()
        sources.persistTreeAccess(tree)
        scanFolder(tree, includeSubfolders = null)
    }

    /** [includeSubfolders] null = use the saved setting. */
    private fun scanFolder(tree: String, includeSubfolders: Boolean?) = scan {
        val config = settings.current()
        val include = includeSubfolders ?: config.analysis.includeSubfolders
        val files = sources.scanFolder(tree, include, excludeTreeUri = config.destinationUri)
        SourceSelection(label = sources.treeDisplayName(tree).orEmpty(), files = files, treeUri = tree)
    }

    fun onDestinationPicked(uri: Uri?) {
        uri ?: return
        val tree = uri.toString()
        viewModelScope.launch {
            if (!setDestination(tree)) events.send(HomeEvent.Message(R.string.error_destination_not_writable))
        }
    }

    fun setIncludeSubfolders(include: Boolean) {
        viewModelScope.launch { settings.updateAnalysis { it.copy(includeSubfolders = include) } }
        // Re-scan the picked folder so the count reflects the new setting.
        local.value.selection?.treeUri?.let { scanFolder(it, include) }
    }

    fun clearSelection() = local.update { it.copy(selection = null) }

    fun startAnalysis(defaultLabel: String) {
        val selection = local.value.selection ?: return
        if (selection.files.isEmpty()) return
        local.update { it.copy(starting = true) }
        viewModelScope.launch {
            try {
                val id = startSession(selection.label.ifBlank { defaultLabel }, selection.files)
                local.update { LocalState() }
                events.send(HomeEvent.SessionStarted(id))
            } catch (e: Exception) {
                local.update { it.copy(starting = false) }
                events.send(HomeEvent.Message(R.string.error_generic))
            }
        }
    }

    fun discardResumable() {
        val session = state.value.resumable ?: return
        viewModelScope.launch {
            jobs.cancelAnalysis(session.id)
            sessions.discard(session.id)
        }
    }

    private fun scan(block: suspend () -> SourceSelection) {
        local.update { it.copy(scanning = true) }
        viewModelScope.launch {
            try {
                val selection = block()
                local.update { it.copy(scanning = false, selection = selection) }
                if (selection.files.isEmpty()) events.send(HomeEvent.Message(R.string.error_no_media_found))
            } catch (e: Exception) {
                local.update { it.copy(scanning = false) }
                events.send(HomeEvent.Message(R.string.error_cannot_read_selection))
            }
        }
    }
}
