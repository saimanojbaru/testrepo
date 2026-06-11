package com.hitit.app.ui.screens.moneyvibe

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.hitit.app.data.repository.MoneyRepository
import com.hitit.domain.money.DemonRoasts
import com.hitit.domain.money.FutureSelf
import com.hitit.domain.money.SpendCategory
import com.hitit.domain.money.SpendFact
import com.hitit.domain.money.VibeTax
import com.hitit.domain.money.WealthAnalyzer
import com.hitit.domain.vibe.VibeScore
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
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

data class InsightUi(val emoji: String, val headline: String, val detail: String, val severity: Int)

data class DemonUi(val description: String, val amountRupees: Long, val roast: String)

data class MoneyVibeUiState(
    val moneyPulse: Int = 0,
    val pulseLabel: String = "",
    val weekSpendRupees: Long = 0,
    val trendLine: String = "",
    val trendIsGood: Boolean = true,
    val burnerSpendRupees: Long = 0,
    val burnerBudgetRupees: Long = 0,
    val burnerUtilization: Float? = null,
    val spendDaysLast7: Int = 0,
    val insights: List<InsightUi> = emptyList(),
    val vibeJarRupees: Long = 0,
    val demons: List<DemonUi> = emptyList(),
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
    private val zone: ZoneId = ZoneId.systemDefault()
    private val budgetPaise = MutableStateFlow(moneyRepository.burnerBudgetPaise)

    /** Weekly aggregates folded into one inner combine (the outer combine stays at 3 flows). */
    private data class WeekAgg(val week: Long, val prevWeek: Long, val burner: Long, val days7: Int)

    private val weekAgg = combine(
        moneyRepository.observeWeekSpend(today),
        moneyRepository.observePrevWeekSpend(today),
        moneyRepository.observeWeekBurnerSpend(today),
        moneyRepository.observeSpendDaysLast7(today),
    ) { week, prev, burner, days7 ->
        WeekAgg(week ?: 0L, prev ?: 0L, burner ?: 0L, days7)
    }

    val state: StateFlow<MoneyVibeUiState> = combine(
        moneyRepository.observeRecent(),
        weekAgg,
        budgetPaise,
    ) { recent, agg, budget ->
        val utilization = if (budget > 0) agg.burner.toFloat() / budget else null
        val pulse = VibeScore.moneyPulse(agg.days7, utilization)
        val vision = FutureSelf.vision(agg.burner)

        // Flatten this week's spends for the analyzer (hour from the log timestamp).
        val weekFacts = recent
            .filter { !it.date.isBefore(today.minusDays(6)) }
            .map { e ->
                SpendFact(
                    amountPaise = e.amountPaise,
                    category = categoryOf(e.category),
                    impulse = e.impulse,
                    daysAgo = ChronoUnit.DAYS.between(e.date, today).toInt().coerceIn(0, 6),
                    hourOfDay = Instant.ofEpochMilli(e.createdAt).atZone(zone).hour,
                )
            }
        val insights = WealthAnalyzer.analyze(weekFacts, budget)

        val diff = agg.week - agg.prevWeek
        val trend = when {
            agg.week == 0L && agg.prevWeek == 0L -> ""
            agg.prevWeek == 0L -> "first tracked week — baseline set"
            diff > 0 -> "+₹${diff / 100} vs last week"
            diff < 0 -> "−₹${-diff / 100} vs last week"
            else -> "dead even with last week"
        }

        MoneyVibeUiState(
            moneyPulse = pulse,
            pulseLabel = VibeScore.label(pulse),
            weekSpendRupees = agg.week / 100,
            trendLine = trend,
            trendIsGood = diff <= 0,
            burnerSpendRupees = agg.burner / 100,
            burnerBudgetRupees = budget / 100,
            burnerUtilization = utilization,
            spendDaysLast7 = agg.days7,
            insights = insights.map { InsightUi(it.emoji, it.headline, it.detail, it.severity) },
            vibeJarRupees = VibeTax.jarTotal(weekFacts) / 100,
            demons = recent
                .filter { it.impulse && !it.date.isBefore(today.minusDays(6)) }
                .take(4)
                .map { e ->
                    DemonUi(
                        description = e.description,
                        amountRupees = e.amountPaise / 100,
                        roast = DemonRoasts.roast(categoryOf(e.category), seed = e.id.toInt()),
                    )
                },
            futureBrokeLine = vision.brokeLine,
            futureGlowLine = vision.glowLine,
            expenses = recent.map { e ->
                val cat = categoryOf(e.category)
                ExpenseUi(
                    id = e.id,
                    amountRupees = e.amountPaise / 100,
                    description = e.description,
                    categoryLabel = cat.label,
                    categoryEmoji = cat.emoji,
                    impulse = e.impulse,
                    dateLabel = if (e.date == today) "today" else "${e.date.dayOfMonth}/${e.date.monthValue}",
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

    private fun categoryOf(name: String): SpendCategory =
        runCatching { SpendCategory.valueOf(name) }.getOrDefault(SpendCategory.OTHER)
}
