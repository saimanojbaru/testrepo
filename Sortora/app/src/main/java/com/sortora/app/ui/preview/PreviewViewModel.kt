package com.sortora.app.ui.preview

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.sortora.app.R
import com.sortora.app.domain.model.AnalysisStatus
import com.sortora.app.domain.model.AppSettings
import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.OperationMode
import com.sortora.app.domain.model.SessionItem
import com.sortora.app.domain.planning.PlannedFile
import com.sortora.app.domain.planning.SortPlanner
import com.sortora.app.domain.repository.BackgroundJobs
import com.sortora.app.domain.repository.JobState
import com.sortora.app.domain.repository.MediaSourceRepository
import com.sortora.app.domain.repository.SessionRepository
import com.sortora.app.domain.repository.SettingsRepository
import com.sortora.app.domain.usecase.ApplySortPlanUseCase
import com.sortora.app.domain.usecase.SetDestinationUseCase
import com.sortora.app.ui.navigation.PreviewRoute
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PreviewRow(val item: SessionItem, val plan: PlannedFile)

data class FolderGroup(val folder: String, val count: Int)

data class PreviewUiState(
    val loading: Boolean = true,
    /** Rows matching the current folder filter. */
    val rows: List<PreviewRow> = emptyList(),
    val totalAnalyzed: Int = 0,
    val includedCount: Int = 0,
    val folders: List<FolderGroup> = emptyList(),
    val filter: String? = null,
    val failed: List<SessionItem> = emptyList(),
    val stillAnalyzing: Int = 0,
    val mode: OperationMode = OperationMode.COPY,
    val destinationLabel: String? = null,
    val destinationReady: Boolean = false,
    val applying: Boolean = false,
    /** Folder names offered as quick picks in the edit sheet. */
    val folderSuggestions: List<String> = emptyList(),
    val editing: PreviewRow? = null,
)

sealed interface PreviewEvent {
    data class Applied(val sessionId: Long) : PreviewEvent
    data class Message(@StringRes val text: Int) : PreviewEvent
}

private data class LocalState(
    val filter: String? = null,
    val mode: OperationMode? = null,
    val applying: Boolean = false,
    val editingId: Long? = null,
)

@HiltViewModel
class PreviewViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val sessions: SessionRepository,
    private val settings: SettingsRepository,
    private val sources: MediaSourceRepository,
    private val planner: SortPlanner,
    private val applyPlan: ApplySortPlanUseCase,
    private val setDestination: SetDestinationUseCase,
    jobs: BackgroundJobs,
) : ViewModel() {

    val sessionId: Long = savedStateHandle.toRoute<PreviewRoute>().sessionId
    private val local = MutableStateFlow(LocalState())
    private val events = Channel<PreviewEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    /** Latest full plan (all analysed items), used when the user taps Apply. */
    @Volatile private var latestPlan: Map<Long, PlannedFile> = emptyMap()
    @Volatile private var latestIncluded: Set<Long> = emptySet()

    val state: StateFlow<PreviewUiState> = combine(
        sessions.observeItems(sessionId),
        settings.settings,
        local,
        jobs.observeAnalysis(sessionId),
    ) { items, config, local, job ->
        buildState(items, config, local, analysisRunning = job.state == JobState.RUNNING || job.state == JobState.QUEUED)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreviewUiState())

    private fun buildState(items: List<SessionItem>, config: AppSettings, local: LocalState, analysisRunning: Boolean): PreviewUiState {
        val plan = planner.plan(items, config)
        val analyzed = items.filter { it.analysisStatus == AnalysisStatus.DONE && it.id in plan }
        val allRows = analyzed.map { PreviewRow(it, plan.getValue(it.id)) }
        val included = allRows.filter { it.item.included }
        latestPlan = plan
        latestIncluded = included.mapTo(HashSet()) { it.item.id }

        val folders = included
            .groupBy { topFolder(it.plan.folder) }
            .map { (folder, rows) -> FolderGroup(folder, rows.size) }
            .sortedByDescending { it.count }
        val filter = local.filter?.takeIf { f -> allRows.any { topFolder(it.plan.folder) == f } }
        val suggestions = (
            folders.map { it.folder } +
                config.customCategories.map { it.folderName } +
                ContentCategory.entries.map(config::folderFor)
            ).filter { it.isNotBlank() }.distinct()

        return PreviewUiState(
            loading = false,
            rows = if (filter == null) allRows else allRows.filter { topFolder(it.plan.folder) == filter },
            totalAnalyzed = allRows.size,
            includedCount = included.size,
            folders = folders,
            filter = filter,
            failed = items.filter { it.analysisStatus == AnalysisStatus.FAILED },
            stillAnalyzing = if (analysisRunning) items.count { it.analysisStatus == AnalysisStatus.PENDING } else 0,
            mode = local.mode ?: config.defaultMode,
            destinationLabel = config.destinationLabel,
            destinationReady = config.destinationUri?.let { sources.hasTreeAccess(it, needWrite = true) } == true,
            applying = local.applying,
            folderSuggestions = suggestions,
            editing = local.editingId?.let { id -> allRows.firstOrNull { it.item.id == id } },
        )
    }

    fun setFilter(folder: String?) = local.update { it.copy(filter = folder) }

    fun setMode(mode: OperationMode) = local.update { it.copy(mode = mode) }

    fun toggleIncluded(row: PreviewRow) {
        viewModelScope.launch { sessions.setIncluded(row.item.id, !row.item.included) }
    }

    fun setAllIncluded(included: Boolean) {
        viewModelScope.launch { sessions.setIncludedForAll(sessionId, included) }
    }

    fun startEditing(row: PreviewRow) = local.update { it.copy(editingId = row.item.id) }

    fun stopEditing() = local.update { it.copy(editingId = null) }

    /** Saves edits. Unchanged fields stay "suggested" so they keep following settings. */
    fun saveEdit(row: PreviewRow, baseName: String, folder: String) {
        val currentBase = row.plan.name.substringBeforeLast('.')
        val newName = baseName.trim().takeIf { it.isNotEmpty() && (it != currentBase || row.plan.nameEdited) }
        val newFolder = folder.trim().takeIf { it.isNotEmpty() && (it != row.plan.folder || row.plan.folderEdited) }
        viewModelScope.launch {
            sessions.updateEdit(row.item.id, newName, newFolder)
            stopEditing()
        }
    }

    fun resetEdit(row: PreviewRow) {
        viewModelScope.launch {
            sessions.updateEdit(row.item.id, null, null)
            stopEditing()
        }
    }

    fun onDestinationPicked(uri: Uri?) {
        uri ?: return
        viewModelScope.launch {
            if (!setDestination(uri.toString())) events.send(PreviewEvent.Message(R.string.error_destination_not_writable))
        }
    }

    fun apply() {
        val current = state.value
        if (current.applying || current.includedCount == 0) return
        if (!current.destinationReady) {
            viewModelScope.launch { events.send(PreviewEvent.Message(R.string.preview_need_destination)) }
            return
        }
        local.update { it.copy(applying = true) }
        viewModelScope.launch {
            try {
                applyPlan(sessionId, current.mode, latestPlan.values, latestIncluded)
                events.send(PreviewEvent.Applied(sessionId))
            } catch (e: Exception) {
                local.update { it.copy(applying = false) }
                events.send(PreviewEvent.Message(R.string.error_generic))
            }
        }
    }

    private fun topFolder(path: String) = path.substringBefore('/')
}
