package com.nonverbal.mastery.data.repo

import android.content.Context
import android.provider.Settings
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.nonverbal.mastery.design.tokens.DefaultReadingSize
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode { SYSTEM, LIGHT, DARK }
enum class MotionMode { SYSTEM, REDUCED }

data class DesignPrefs(
    val theme: ThemeMode = ThemeMode.SYSTEM,
    val motion: MotionMode = MotionMode.SYSTEM,
    val readingSize: Int = DefaultReadingSize,
)

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "design")

class SettingsRepository(private val context: Context) {
    private val themeKey = stringPreferencesKey("theme")
    private val motionKey = stringPreferencesKey("motion")
    private val sizeKey = intPreferencesKey("reading_size")

    val prefs: Flow<DesignPrefs> = context.dataStore.data.map { p ->
        DesignPrefs(
            theme = p[themeKey]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.SYSTEM,
            motion = p[motionKey]?.let { runCatching { MotionMode.valueOf(it) }.getOrNull() } ?: MotionMode.SYSTEM,
            readingSize = (p[sizeKey] ?: DefaultReadingSize).coerceIn(15, 22),
        )
    }

    suspend fun setTheme(mode: ThemeMode) = context.dataStore.edit { it[themeKey] = mode.name }
    suspend fun setMotion(mode: MotionMode) = context.dataStore.edit { it[motionKey] = mode.name }
    suspend fun setReadingSize(size: Int) = context.dataStore.edit { it[sizeKey] = size.coerceIn(15, 22) }

    /** The system's animator duration scale; 0 means the person has turned animations off. */
    fun systemAnimationsOff(): Boolean = runCatching {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }.getOrDefault(false)
}
