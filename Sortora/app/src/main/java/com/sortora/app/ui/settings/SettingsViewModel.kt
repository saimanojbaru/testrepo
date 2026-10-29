package com.sortora.app.ui.settings

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortora.app.domain.model.AnalysisOptions
import com.sortora.app.domain.model.AppSettings
import com.sortora.app.domain.model.ContentCategory
import com.sortora.app.domain.model.NamingSettings
import com.sortora.app.domain.model.OperationMode
import com.sortora.app.domain.model.ThemeMode
import com.sortora.app.domain.model.UserCategory
import com.sortora.app.domain.repository.MediaSourceRepository
import com.sortora.app.domain.repository.SettingsRepository
import com.sortora.app.domain.usecase.SetDestinationUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

data class SettingsUiState(
    val loading: Boolean = true,
    val settings: AppSettings = AppSettings(),
    val destinationReady: Boolean = false,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val repo: SettingsRepository,
    private val sources: MediaSourceRepository,
    private val setDestination: SetDestinationUseCase,
) : ViewModel() {

    private val errors = Channel<Unit>(Channel.BUFFERED)
    val destinationErrors = errors.receiveAsFlow()

    val state: StateFlow<SettingsUiState> = repo.settings
        .map { s ->
            SettingsUiState(
                loading = false,
                settings = s,
                destinationReady = s.destinationUri?.let { sources.hasTreeAccess(it, needWrite = true) } == true,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setDefaultMode(mode: OperationMode) = launch { repo.setDefaultMode(mode) }
    fun setGroupByYear(enabled: Boolean) = launch { repo.setGroupByYear(enabled) }
    fun setWriteLogFile(enabled: Boolean) = launch { repo.setWriteLogFile(enabled) }
    fun updateNaming(transform: (NamingSettings) -> NamingSettings) = launch { repo.updateNaming(transform) }
    fun updateAnalysis(transform: (AnalysisOptions) -> AnalysisOptions) = launch { repo.updateAnalysis(transform) }
    fun setThemeMode(mode: ThemeMode) = launch { repo.setThemeMode(mode) }
    fun setDynamicColor(enabled: Boolean) = launch { repo.setDynamicColor(enabled) }
    fun setFolderName(category: ContentCategory, name: String) = launch { repo.setFolderName(category, name) }
    fun resetFolderNames() = launch { repo.resetFolderNames() }
    fun deleteCategory(id: String) = launch { repo.deleteCustomCategory(id) }
    fun moveCategory(id: String, up: Boolean) = launch { repo.moveCustomCategory(id, up) }

    fun saveCategory(existingId: String?, folderName: String, keywords: String) = launch {
        repo.upsertCustomCategory(
            UserCategory(
                id = existingId ?: UUID.randomUUID().toString(),
                folderName = folderName,
                keywords = keywords.split(',', ';', '\n'),
            ),
        )
    }

    fun onDestinationPicked(uri: Uri?) {
        uri ?: return
        launch { if (!setDestination(uri.toString())) errors.send(Unit) }
    }

    private fun launch(block: suspend () -> Unit) {
        viewModelScope.launch { block() }
    }
}
