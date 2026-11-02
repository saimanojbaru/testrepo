package com.nonverbal.mastery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nonverbal.mastery.data.repo.ContentState
import com.nonverbal.mastery.data.repo.DesignPrefs
import com.nonverbal.mastery.data.repo.MotionMode
import com.nonverbal.mastery.data.seed.SeedData
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Root state: the content pack, design preferences, and whether motion should be reduced. */
class MasteryViewModel(private val container: AppContainer) : ViewModel() {
    val content: StateFlow<ContentState> = container.content.state

    /** Null until DataStore has answered, so the first frame never flashes the wrong theme. */
    val prefs: StateFlow<DesignPrefs?> = container.settings.prefs
        .map<DesignPrefs, DesignPrefs?> { it }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    fun reducedMotion(p: DesignPrefs?): Boolean =
        p?.motion == MotionMode.REDUCED || container.settings.systemAnimationsOff()

    init {
        viewModelScope.launch {
            container.content.load()
            (container.content.state.value as? ContentState.Ready)?.let {
                runCatching { SeedData.firstRun(container.database, it.curriculum) }
            }
        }
    }
}
