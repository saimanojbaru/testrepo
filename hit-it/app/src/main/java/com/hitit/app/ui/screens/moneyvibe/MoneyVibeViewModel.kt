package com.hitit.app.ui.screens.moneyvibe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.repository.MoneyRepository
import com.hitit.domain.money.SpendCategory
import com.hitit.domain.vibe.VibeScore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import javax.inject.Inject

data class ExpenseUi(
    val id: Long,
    val amountRupees: Long,
    val description: String,
    val categoryLabel: String,
    val categoryEmoji: String,
    val impulse: Boolean,
    val dateLabel: String,
)

data class MoneyVibeUiState(
    val moneyPulse: Int = 0,
    val pulseLabel: String = "",
    val weekSpendRupees: Long = 0,
    val burnerSpendRupees: Long = 0,
    val burnerBudgetRupees: Long = 0,
    val burnerUtilization: Float? = null,
    val spendDaysLast7: Int = 0,
    val futureBrokeLine: String = "",
    val futureGlowLine: String = "",
    val expenses: List<ExpenseUi> = emptyList(),
    val started: Boolean = false,
    val loading: Boolean = true,
)

@HiltViewModel
class MoneyVibeViewModel @Inject constructor(
    private val moneyRepository: MoneyRepository,
) : ViewModel() {

    private val today: LocalDate = LocalDate.now()
    private val budgetPaise = MutableStateFlow(moneyRepository.burnerBudgetPaise)

    val state: StateFlow<MoneyVibeUiState> = combine(
        moneyRepository.observeRecent(),
        moneyRepository.observeWeekSpend(today),
        moneyRepository.observeWeekBurnerSpend(today),
        moneyRepository.observeSpendDaysLast7(today),
        budgetPaise,
    ) { recent, weekSpend, burnerSpend, days7, budget ->
        val burner = burnerSpend ?: 0L
        val utilization = if (budget > 0) burner.toFloat() / budget else null
        val pulse = VibeScore.moneyPulse(days7, utilization)
        val vision = com.hitit.domain.money.FutureSelf.vision(burner)
        MoneyVibeUiState(
            moneyPulse = pulse,
            pulseLabel = VibeScore.label(pulse),
            weekSpendRupees = (weekSpend ?: 0L) / 100,
            burnerSpendRupees = burner / 100,
            burnerBudgetRupees = budget / 100,
            burnerUtilization = utilization,
            spendDaysLast7 = days7,
            futureBrokeLine = vision.brokeLine,
            futureGlowLine = vision.glowLine,
            expenses = recent.map { e ->
                val cat = runCatching { SpendCategory.valueOf(e.category) }.getOrDefault(SpendCategory.OTHER)
                ExpenseUi(
                    id = e.id,
                    amountRupees = e.amountPaise / 100,
                    description = e.description,
                    categoryLabel = cat.label,
                    categoryEmoji = cat.emoji,
                    impulse = e.impulse,
                    dateLabel = if (e.date == today) "today" else e.date.dayOfMonth.toString() + "/" + e.date.monthValue,
                )
            },
            started = recent.isNotEmpty(),
            loading = false,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MoneyVibeUiState())

    fun logExpense(amountRupees: Long, description: String) {
        if (amountRupees <= 0 || description.isBlank()) return
        viewModelScope.launch { moneyRepository.log(amountRupees * 100, description) }
    }

    fun deleteExpense(id: Long) {
        viewModelScope.launch { moneyRepository.delete(id) }
    }

    fun setBurnerBudget(rupees: Long) {
        moneyRepository.burnerBudgetPaise = rupees * 100
        budgetPaise.value = rupees * 100
    }
}
