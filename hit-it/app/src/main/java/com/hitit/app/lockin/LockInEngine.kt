package com.hitit.app.lockin

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** Snapshot of the running Lock In timer, shared between the Service (writer) and the UI (reader). */
data class LockInRunState(
    val active: Boolean = false,
    val paused: Boolean = false,
    val totalMillis: Long = 0L,
    val remainingMillis: Long = 0L,
    val zone: String = FocusZones.DEFAULT,
    val repId: Long? = null,
    val taskId: Long? = null,
    val label: String = "",
    /** One-shot: set to the focused minutes when a session just finished (for a UI confirmation). */
    val justFinishedMinutes: Int? = null,
)

/**
 * Process-wide holder for the active timer. The foreground [LockInService] is the single writer;
 * the Lock In screen observes [state]. This keeps the countdown alive across navigation and while
 * the app is backgrounded (the service runs); it does not survive full process death.
 */
object LockInEngine {
    private val _state = MutableStateFlow(LockInRunState())
    val state: StateFlow<LockInRunState> = _state.asStateFlow()

    fun current(): LockInRunState = _state.value
    fun publish(value: LockInRunState) { _state.value = value }
    fun update(block: (LockInRunState) -> LockInRunState) { _state.update(block) }
    fun reset() { _state.value = LockInRunState() }
}
