package com.hitit.app.data.repository

import com.hitit.app.data.local.dao.FoodEntryDao
import com.hitit.app.data.local.entity.FoodEntryEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** BodyFlow's data: food/fuel logging now; Health Connect activity lands in the next drop. */
@Singleton
class BodyRepository @Inject constructor(
    private val foodDao: FoodEntryDao,
) {
    fun observeToday(today: LocalDate = LocalDate.now()): Flow<List<FoodEntryEntity>> =
        foodDao.observeForDate(today)

    fun observeFoodDaysLast7(today: LocalDate = LocalDate.now()): Flow<Int> =
        foodDao.observeDistinctDaysSince(today.minusDays(6))

    fun observeHasAnyLogs(): Flow<Int> = foodDao.observeCount()

    suspend fun log(description: String, kcal: Int, date: LocalDate = LocalDate.now()) {
        foodDao.insert(FoodEntryEntity(date = date, description = description.trim(), kcal = kcal))
    }

    suspend fun delete(id: Long) = foodDao.delete(id)

    suspend fun clearAll() = foodDao.deleteAll()
}
