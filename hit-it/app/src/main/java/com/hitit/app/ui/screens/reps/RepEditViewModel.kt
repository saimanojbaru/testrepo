package com.hitit.app.ui.screens.reps

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.local.entity.RepEntity
import com.hitit.app.data.mapper.formatCustomDays
import com.hitit.app.data.mapper.parseCustomDays
import com.hitit.app.data.repository.RepRepository
import com.hitit.app.ui.navigation.Dest
import com.hitit.app.ui.theme.RepPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject

data class RepEditState(
    val id: Long = 0,
    val isNew: Boolean = true,
    val name: String = "",
    val emoji: String = "✅",
    val colorHex: String = RepPalette.first(),
    val scheduleType: String = "DAILY",
    /** ISO day numbers 1..7 selected for CUSTOM. */
    val customDays: Set<Int> = emptySet(),
    val weeklyTarget: Int = 3,
    val targetCount: Int = 1,
    val restDaysAllowed: Int = 2,
    val reminderEnabled: Boolean = false,
    val reminderHour: Int = 9,
    val reminderMinute: Int = 0,
    val saved: Boolean = false,
) {
    val canSave: Boolean get() = name.isNotBlank() &&
        (scheduleType != "CUSTOM" || customDays.isNotEmpty())
}

@HiltViewModel
class RepEditViewModel @Inject constructor(
    private val repRepository: RepRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val repId: Long = savedStateHandle[Dest.ARG_REP_ID] ?: Dest.NEW_REP_ID

    private val _state = MutableStateFlow(RepEditState())
    val state: StateFlow<RepEditState> = _state.asStateFlow()

    init {
        if (repId != Dest.NEW_REP_ID) {
            viewModelScope.launch {
                repRepository.getRep(repId)?.let { rep ->
                    _state.value = RepEditState(
                        id = rep.id,
                        isNew = false,
                        name = rep.name,
                        emoji = rep.emoji,
                        colorHex = rep.colorHex,
                        scheduleType = rep.scheduleType,
                        customDays = parseCustomDays(rep.customDaysCsv).map { it.value }.toSet(),
                        weeklyTarget = rep.weeklyTarget,
                        targetCount = rep.targetCount,
                        restDaysAllowed = rep.restDaysAllowed,
                        reminderEnabled = rep.reminderEnabled,
                        reminderHour = rep.reminderHour,
                        reminderMinute = rep.reminderMinute,
                    )
                }
            }
        }
    }

    fun onName(value: String) = update { it.copy(name = value) }
    fun onEmoji(value: String) = update { it.copy(emoji = value.take(2).ifBlank { "✅" }) }
    fun onColor(hex: String) = update { it.copy(colorHex = hex) }
    fun onSchedule(type: String) = update { it.copy(scheduleType = type) }

    fun toggleCustomDay(isoDay: Int) = update {
        val days = it.customDays.toMutableSet()
        if (!days.add(isoDay)) days.remove(isoDay)
        it.copy(customDays = days)
    }

    fun onWeeklyTarget(value: Int) = update { it.copy(weeklyTarget = value.coerceIn(1, 21)) }
    fun onTargetCount(value: Int) = update { it.copy(targetCount = value.coerceIn(1, 20)) }
    fun onRestDays(value: Int) = update { it.copy(restDaysAllowed = value.coerceIn(0, 7)) }
    fun onReminderEnabled(value: Boolean) = update { it.copy(reminderEnabled = value) }
    fun onReminderTime(hour: Int, minute: Int) =
        update { it.copy(reminderHour = hour.coerceIn(0, 23), reminderMinute = minute.coerceIn(0, 59)) }

    fun save() {
        val s = _state.value
        if (!s.canSave) return
        viewModelScope.launch {
            val existing = if (s.isNew) null else repRepository.getRep(s.id)
            val entity = RepEntity(
                id = s.id,
                name = s.name.trim(),
                emoji = s.emoji,
                colorHex = s.colorHex,
                scheduleType = s.scheduleType,
                customDaysCsv = formatCustomDays(
                    s.customDays.mapNotNull { runCatching { DayOfWeek.of(it) }.getOrNull() }.toSet(),
                ),
                weeklyTarget = s.weeklyTarget,
                targetCount = s.targetCount,
                restDaysAllowed = s.restDaysAllowed,
                restModeStart = existing?.restModeStart,
                restModeEnd = existing?.restModeEnd,
                reminderEnabled = s.reminderEnabled,
                reminderHour = s.reminderHour,
                reminderMinute = s.reminderMinute,
                isArchived = existing?.isArchived ?: false,
                sortOrder = existing?.sortOrder ?: 0,
                createdDate = existing?.createdDate ?: LocalDate.now(),
            )
            repRepository.saveRep(entity)
            _state.value = _state.value.copy(saved = true)
        }
    }

    private inline fun update(block: (RepEditState) -> RepEditState) {
        _state.value = block(_state.value)
    }
}
