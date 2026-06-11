package com.hitit.app.data.repository

import com.hitit.app.data.local.AppPreferences
import com.hitit.app.data.local.dao.ExpenseDao
import com.hitit.app.data.local.entity.ExpenseEntity
import com.hitit.domain.money.ExpenseCategorizer
import com.hitit.domain.money.SpendCategory
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject
import javax.inject.Singleton

/** MoneyVibe's data: manual spends now (auto-capture via notifications is a later opt-in drop). */
@Singleton
class MoneyRepository @Inject constructor(
    private val expenseDao: ExpenseDao,
    private val prefs: AppPreferences,
) {
    fun observeRecent(): Flow<List<ExpenseEntity>> = expenseDao.observeRecent()

    fun observeWeekSpend(today: LocalDate = LocalDate.now()): Flow<Long?> =
        expenseDao.observeSpendBetween(today.minusDays(6), today)

    /** The 7 days before this week's window — for the week-over-week trend. */
    fun observePrevWeekSpend(today: LocalDate = LocalDate.now()): Flow<Long?> =
        expenseDao.observeSpendBetween(today.minusDays(13), today.minusDays(7))

    /** This week's spend in burner ("fun money") categories — what the Burner Budget tracks. */
    fun observeWeekBurnerSpend(today: LocalDate = LocalDate.now()): Flow<Long?> =
        expenseDao.observeSpendBetweenFor(
            from = today.minusDays(6),
            to = today,
            categories = SpendCategory.entries.filter { it.burner }.map { it.name },
        )

    fun observeSpendDaysLast7(today: LocalDate = LocalDate.now()): Flow<Int> =
        expenseDao.observeDistinctDaysSince(today.minusDays(6))

    fun observeHasAnyLogs(): Flow<Int> = expenseDao.observeCount()

    var burnerBudgetPaise: Long
        get() = prefs.burnerBudgetPaise
        set(value) { prefs.burnerBudgetPaise = value }

    /** Log a spend; category + impulse are decided by the offline rule engine (user can override). */
    suspend fun log(
        amountPaise: Long,
        description: String,
        category: SpendCategory? = null,
        date: LocalDate = LocalDate.now(),
        hourOfDay: Int = LocalTime.now().hour,
    ) {
        val resolved = category ?: ExpenseCategorizer.categorize(description)
        expenseDao.insert(
            ExpenseEntity(
                date = date,
                amountPaise = amountPaise,
                description = description.trim(),
                category = resolved.name,
                impulse = ExpenseCategorizer.isImpulse(resolved, hourOfDay),
            ),
        )
    }

    suspend fun delete(id: Long) = expenseDao.delete(id)

    /**
     * Log a notification-captured spend, deduplicated: the same amount + merchant within
     * [DEDUPE_WINDOW_MS] is dropped (apps often post several notifications per order — confirm,
     * prepare, deliver). Returns true when a new row was written.
     */
    suspend fun logCaptured(
        captured: com.hitit.domain.money.CapturedSpend,
        date: LocalDate = LocalDate.now(),
        hourOfDay: Int = LocalTime.now().hour,
        nowMillis: Long = System.currentTimeMillis(),
    ): Boolean {
        val merchant = captured.merchant.trim()
        if (expenseDao.countSimilarSince(captured.amountPaise, merchant, nowMillis - DEDUPE_WINDOW_MS) > 0) {
            return false
        }
        expenseDao.insert(
            ExpenseEntity(
                date = date,
                amountPaise = captured.amountPaise,
                description = merchant,
                category = captured.category.name,
                impulse = ExpenseCategorizer.isImpulse(captured.category, hourOfDay),
                auto = true,
            ),
        )
        return true
    }

    private companion object {
        const val DEDUPE_WINDOW_MS = 30 * 60_000L // 30 min — covers an order's notification volley
    }

    suspend fun clearAll() = expenseDao.deleteAll()
}
