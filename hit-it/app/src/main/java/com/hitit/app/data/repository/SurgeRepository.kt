package com.hitit.app.data.repository

import com.hitit.domain.surge.Surge
import com.hitit.domain.surge.SurgeEngine
import com.hitit.domain.surge.SurgeKind
import com.hitit.domain.surge.SurgeMetrics
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The live (in-session) high-stakes Surge coordinator — the behavioral loop's state machine.
 *
 * Evaluated whenever the dashboard refreshes: with no surge running, the rule engine may deploy one.
 * Three terminal transitions:
 *  - **Win**  — the target was completed while the window was open ([resolve]); no consequence.
 *  - **Expire (debt recovery)** — the window closed unmet ⇒ bank the doubling delta into [penalty].
 *  - **Expire (blitz)** — the window closed unmet ⇒ no penalty (a blitz is upside-only).
 *
 * The penalty lives in memory (not the immutable ledger) so a mid-day window can never rewrite
 * finalized history — the ledger's own debt accounting remains the source of truth for the past.
 * Surge evaluation deliberately reads the LEDGER base debt (not the penalty-inflated total) so a
 * banked penalty can't feed back into deploying ever-larger surges.
 */
@Singleton
class SurgeRepository @Inject constructor() {

    private val _active = MutableStateFlow<Surge?>(null)
    val active: StateFlow<Surge?> = _active.asStateFlow()

    /** Extra debt accrued this session from debt-recovery surges that expired unmet (doubling deltas). */
    private val _penalty = MutableStateFlow(0)
    val penalty: StateFlow<Int> = _penalty.asStateFlow()

    private var baseDebtAtStart = 0
    private var resolvedCurrent = false

    /**
     * Re-evaluate against current [metrics] (debt = the ledger base, NOT including [penalty]).
     * Preserves a still-running surge; banks the penalty for an expired-unmet debt window; then
     * deploys a fresh surge if the rules now call for one.
     */
    fun refresh(metrics: SurgeMetrics, nowMillis: Long = System.currentTimeMillis()) {
        val current = _active.value
        if (current != null) {
            if (current.isActive(nowMillis)) return // window still open — leave it be
            // Window closed. A debt-recovery surge left unresolved doubles the day's debt.
            if (!resolvedCurrent && current.kind == SurgeKind.DEBT_RECOVERY) {
                _penalty.value = (_penalty.value + SurgeEngine.expiryDebtDelta(baseDebtAtStart)).coerceAtMost(MAX_PENALTY)
            }
            _active.value = null
        }
        val next = SurgeEngine.evaluate(metrics, nowMillis)
        if (next != null) {
            baseDebtAtStart = metrics.outstandingDebt
            resolvedCurrent = false
            _active.value = next
        }
    }

    /** The target was completed inside the window — the surge is won (no penalty). */
    fun resolve() {
        resolvedCurrent = true
        _active.value = null
    }

    /** Wipe the banked session penalty (e.g. the user liquidated their debt). */
    fun clearPenalty() { _penalty.value = 0 }

    private companion object {
        const val MAX_PENALTY = 100
    }
}
