package com.sortora.app.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sortora.app.domain.model.ThemeMode
import com.sortora.app.domain.repository.SettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

data class MainUiState(
    val loading: Boolean = true,
    val onboardingComplete: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
)

@HiltViewModel
class MainViewModel @Inject constructor(settings: SettingsRepository) : ViewModel() {
    val state: StateFlow<MainUiState> = settings.settings
        .map { MainUiState(loading = false, onboardingComplete = it.onboardingComplete, themeMode = it.themeMode, dynamicColor = it.dynamicColor) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MainUiState())
}
