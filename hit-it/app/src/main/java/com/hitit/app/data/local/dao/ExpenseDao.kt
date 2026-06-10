package com.hitit.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hitit.app.data.local.entity.ExpenseEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface ExpenseDao {
    @Insert
    suspend fun insert(expense: ExpenseEntity): Long

    @Query("SELECT * FROM expenses ORDER BY createdAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 50): Flow<List<ExpenseEntity>>

    @Query("SELECT SUM(amountPaise) FROM expenses WHERE date BETWEEN :from AND :to")
    fun observeSpendBetween(from: LocalDate, to: LocalDate): Flow<Long?>

    @Query(
        "SELECT SUM(amountPaise) FROM expenses WHERE date BETWEEN :from AND :to AND category IN (:categories)",
    )
    fun observeSpendBetweenFor(from: LocalDate, to: LocalDate, categories: List<String>): Flow<Long?>

    @Query("SELECT COUNT(DISTINCT date) FROM expenses WHERE date >= :since")
    fun observeDistinctDaysSince(since: LocalDate): Flow<Int>

    @Query("SELECT COUNT(*) FROM expenses")
    fun observeCount(): Flow<Int>

    @Query("DELETE FROM expenses WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM expenses")
    suspend fun deleteAll()
}
