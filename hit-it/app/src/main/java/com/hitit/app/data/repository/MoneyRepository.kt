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

    suspend fun clearAll() = expenseDao.deleteAll()
}
