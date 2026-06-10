package com.hitit.app.ui.screens.shadow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.repository.LedgerRepository
import com.hitit.app.data.repository.MoneyRepository
import com.hitit.domain.shadow.ShadowSelf
import com.hitit.domain.shadow.ShadowSignals
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate
import javax.inject.Inject

data class RoastUi(val headline: String, val line: String, val severity: Int)

data class ShadowUiState(
    val verdict: String = "",
    val roasts: List<RoastUi> = emptyList(),
    val loading: Boolean = true,
)

@HiltViewModel
class ShadowSelfViewModel @Inject constructor(
    ledgerRepository: LedgerRepository,
    moneyRepository: MoneyRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()

    val state: StateFlow<ShadowUiState> = combine(
        ledgerRepository.observeOutstandingDebt(),
        ledgerRepository.observeRecent(7, today),
        moneyRepository.observeRecent(),
    ) { debt, ledger, expenses ->
        val missed = ledger.sumOf { (it.repsScheduled - it.repsMet).coerceAtLeast(0) }
        val lowDays = ledger.count { it.momentumScore < 50 }
        val skippedCheckIns = ledger.count { !it.checkedIn }
        val impulse = expenses.count { it.impulse && !it.date.isBefore(today.minusDays(6)) }

        val signals = ShadowSignals(
            outstandingDebt = debt,
            missedRepsLast7 = missed,
            skippedCheckInDays = skippedCheckIns,
            lowMomentumDays = lowDays,
            impulseSpends = impulse,
            brokeStreak = false,
        )
        ShadowUiState(
            verdict = ShadowSelf.verdict(signals),
            roasts = ShadowSelf.roast(signals).map { RoastUi(it.headline, it.line, it.severity) },
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ShadowUiState())
}
