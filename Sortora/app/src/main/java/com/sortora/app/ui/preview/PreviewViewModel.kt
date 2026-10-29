package com.sortora.app.ui.preview

import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.sortora.app.R
import com.sortora.app.domain.filters.SmartFilter
import com.sortora.app.domain.model.AnalysisStatus
import com.sortora.app.domain.model.AppSettings
import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.OperationMode
import com.sortora.app.domain.model.SessionItem
import com.sortora.app.domain.planning.PlanOptions
import com.sortora.app.domain.planning.PlannedFile
import com.sortora.app.domain.planning.SortPlanner
import com.sortora.app.domain.repository.BackgroundJobs
import com.sortora.app.domain.repository.FaceRepository
import com.sortora.app.domain.repository.JobState
import com.sortora.app.domain.repository.MediaSourceRepository
import com.sortora.app.domain.repository.SessionRepository
import com.sortora.app.domain.repository.SettingsRepository
import com.sortora.app.domain.usecase.ApplySortPlanUseCase
import com.sortora.app.domain.usecase.FindDuplicatesUseCase
import com.sortora.app.domain.usecase.KeepDuplicateUseCase
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class PreviewRow(val item: SessionItem, val plan: PlannedFile)

data class FolderGroup(val folder: String, val count: Int)

data class PreviewUiState(
    val loading: Boolean = true,
    /** Rows matching the current folder chip and smart filters. */
    val rows: List<PreviewRow> = emptyList(),
    val totalAnalyzed: Int = 0,
    val includedCount: Int = 0,
    val folders: List<FolderGroup> = emptyList(),
    val folderFilter: String? = null,
    val smartFilters: Set<SmartFilter> = emptySet(),
    /** How many analysed files each smart filter would show (filters with 0 are hidden). */
    val filterCounts: Map<SmartFilter, Int> = emptyMap(),
    val duplicateGroups: Int = 0,
    val failed: List<SessionItem> = emptyList(),
    val stillAnalyzing: Int = 0,
    val mode: OperationMode = OperationMode.COPY,
    val destinationLabel: String? = null,
    val destinationReady: Boolean = false,
    val applying: Boolean = false,
    /** Folder names offered as quick picks in the edit sheet and "set folder". */
    val folderSuggestions: List<String> = emptyList(),
    val editing: PreviewRow? = null,
    /** Other members of the edited file's duplicate group. */
    val editingDuplicates: List<PreviewRow> = emptyList(),
    val faceCount: Int = 0,
) {
    val isFiltered: Boolean get() = folderFilter != null || smartFilters.isNotEmpty()
    val needsDestination: Boolean get() = mode != OperationMode.RENAME && !destinationReady
}

sealed interface PreviewEvent {
    data class Applied(val sessionId: Long) : PreviewEvent
    data class Message(@StringRes val text: Int) : PreviewEvent
}

private data class LocalState(
    val folderFilter: String? = null,
    val smartFilters: Set<SmartFilter> = emptySet(),
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
    private val keepDuplicate: KeepDuplicateUseCase,
    findDuplicates: FindDuplicatesUseCase,
    faces: FaceRepository,
    jobs: BackgroundJobs,
) : ViewModel() {

    val sessionId: Long = savedStateHandle.toRoute<PreviewRoute>().sessionId
    private val local = MutableStateFlow(LocalState())
    private val events = Channel<PreviewEvent>(Channel.BUFFERED)
    val eventFlow = events.receiveAsFlow()

    /** Latest plan and items, used by Apply, bulk actions and the pattern preview. */
    @Volatile private var latestPlan: Map<Long, PlannedFile> = emptyMap()
    @Volatile private var latestItems: List<SessionItem> = emptyList()
    @Volatile private var latestSettings: AppSettings = AppSettings()
    @Volatile private var latestPeople: Map<Long, List<String>> = emptyMap()
    @Volatile private var visibleIds: List<Long> = emptyList()

    private val inputs = combine(
        sessions.observeItems(sessionId),
        settings.settings,
        faces.observePeople(sessionId),
        faces.observeFaces(sessionId).map { it.size },
        jobs.observeAnalysis(sessionId),
    ) { items, config, people, faceCount, job ->
        Inputs(items, config, people, faceCount, job.state == JobState.RUNNING || job.state == JobState.QUEUED)
    }

    val state: StateFlow<PreviewUiState> = combine(inputs, local) { input, local -> buildState(input, local) }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PreviewUiState())

    init {
        // Covers sessions whose analysis was stopped early: group whatever was analysed.
        viewModelScope.launch { runCatching { findDuplicates(sessionId) } }
    }

    private data class Inputs(
        val items: List<SessionItem>,
        val config: AppSettings,
        val people: Map<Long, List<String>>,
        val faceCount: Int,
        val analysisRunning: Boolean,
    )

    private fun buildState(input: Inputs, local: LocalState): PreviewUiState {
        val (items, config, people) = Triple(input.items, input.config, input.people)
        val mode = local.mode ?: config.defaultMode
        val plan = planner.plan(items, config, PlanOptions(mode, people))
        latestPlan = plan
        latestItems = items
        latestSettings = config
        latestPeople = people

        val analyzed = items.filter { it.analysisStatus == AnalysisStatus.DONE && it.id in plan }
        val allRows = analyzed.map { PreviewRow(it, plan.getValue(it.id)) }
        val included = allRows.filter { it.item.included }

        val folders = included
            .groupBy { topFolder(it.plan.folder) }
            .map { (folder, rows) -> FolderGroup(folder, rows.size) }
            .sortedByDescending { it.count }
        val folderFilter = local.folderFilter?.takeIf { f -> allRows.any { topFolder(it.plan.folder) == f } }
        val byFolder = if (folderFilter == null) allRows else allRows.filter { topFolder(it.plan.folder) == folderFilter }
        val rows = byFolder.filter { row -> local.smartFilters.all { it.matches(row.item) } }
        visibleIds = rows.map { it.item.id }

        val suggestions = (
            folders.map { it.folder } +
                config.customCategories.map { it.folderName } +
                ContentCategory.entries.map(config::folderFor) +
                SortPlanner.DUPLICATES_FOLDER
            ).filter { it.isNotBlank() }.distinct()

        val editing = local.editingId?.let { id -> allRows.firstOrNull { it.item.id == id } }
        val editingDuplicates = editing?.item?.duplicate?.let { dup ->
            allRows.filter { it.item.duplicate?.groupId == dup.groupId && it.item.id != editing.item.id }
        }.orEmpty()

        return PreviewUiState(
            loading = false,
            rows = rows,
            totalAnalyzed = allRows.size,
            includedCount = included.size,
            folders = folders,
            folderFilter = folderFilter,
            smartFilters = local.smartFilters,
            filterCounts = SmartFilter.entries.associateWith { f -> analyzed.count { f.matches(it) } },
            duplicateGroups = analyzed.mapNotNull { it.duplicate?.groupId }.distinct().size,
            failed = items.filter { it.analysisStatus == AnalysisStatus.FAILED },
            stillAnalyzing = if (input.analysisRunning) items.count { it.analysisStatus == AnalysisStatus.PENDING } else 0,
            mode = mode,
            destinationLabel = config.destinationLabel,
            destinationReady = config.destinationUri?.let { sources.hasTreeAccess(it, needWrite = true) } == true,
            applying = local.applying,
            folderSuggestions = suggestions,
            editing = editing,
            editingDuplicates = editingDuplicates,
            faceCount = input.faceCount,
        )
    }

    // Filters

    fun setFolderFilter(folder: String?) = local.update { it.copy(folderFilter = folder) }

    fun toggleSmartFilter(filter: SmartFilter) = local.update {
        it.copy(smartFilters = if (filter in it.smartFilters) it.smartFilters - filter else it.smartFilters + filter)
    }

    fun clearFilters() = local.update { it.copy(folderFilter = null, smartFilters = emptySet()) }

    fun setMode(mode: OperationMode) = local.update { it.copy(mode = mode) }

    // Single-item edits

    fun toggleIncluded(row: PreviewRow) {
        viewModelScope.launch { sessions.setIncluded(row.item.id, !row.item.included) }
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
            if (newName != null) sessions.setCustomPattern(listOf(row.item.id), null)
            stopEditing()
        }
    }

    fun resetEdit(row: PreviewRow) {
        viewModelScope.launch {
            sessions.resetEdits(listOf(row.item.id))
            stopEditing()
        }
    }

    fun keepThisDuplicate(row: PreviewRow) {
        viewModelScope.launch { keepDuplicate(sessionId, row.item.id) }
    }

    // Bulk actions on what's currently visible (all files, or the filtered ones)

    fun includeVisible(included: Boolean) = bulk { sessions.setIncluded(it, included) }

    fun setFolderForVisible(folder: String) = bulk { sessions.setCustomFolder(it, folder.trim()) }

    fun renameVisible(pattern: String) = bulk { sessions.setCustomPattern(it, pattern.trim().ifEmpty { null }) }

    fun resetVisible() = bulk { sessions.resetEdits(it) }

    private fun bulk(action: suspend (List<Long>) -> Unit) {
        val ids = visibleIds
        if (ids.isEmpty()) return
        viewModelScope.launch { action(ids) }
    }

    /** What the first few visible files would be called with [pattern] (original → new). */
    fun previewPattern(pattern: String, limit: Int = 3): List<Pair<String, String>> {
        val ids = visibleIds.toSet()
        if (ids.isEmpty()) return emptyList()
        val trial = latestItems.map { if (it.id in ids) it.copy(customPattern = pattern.ifBlank { null }, customName = null) else it }
        val plan = planner.plan(trial, latestSettings, PlanOptions(state.value.mode, latestPeople))
        return visibleIds.take(limit).mapNotNull { id ->
            val item = latestItems.firstOrNull { it.id == id } ?: return@mapNotNull null
            plan[id]?.let { item.file.displayName to it.name }
        }
    }

    // Destination and apply

    fun onDestinationPicked(uri: Uri?) {
        uri ?: return
        viewModelScope.launch {
            if (!setDestination(uri.toString())) events.send(PreviewEvent.Message(R.string.error_destination_not_writable))
        }
    }

    fun apply() {
        val current = state.value
        if (current.applying || current.includedCount == 0) return
        if (current.needsDestination) {
            viewModelScope.launch { events.send(PreviewEvent.Message(R.string.preview_need_destination)) }
            return
        }
        val includedIds = latestItems.filter { it.included }.mapTo(HashSet()) { it.id }
        local.update { it.copy(applying = true) }
        viewModelScope.launch {
            try {
                applyPlan(sessionId, current.mode, latestPlan.values, includedIds)
                events.send(PreviewEvent.Applied(sessionId))
            } catch (e: Exception) {
                local.update { it.copy(applying = false) }
                events.send(PreviewEvent.Message(R.string.error_generic))
            }
        }
    }

    private fun topFolder(path: String) = path.substringBefore('/')
}
