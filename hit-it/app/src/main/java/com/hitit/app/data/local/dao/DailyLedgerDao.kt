package com.hitit.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hitit.app.data.local.entity.DailyLedgerEntity
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

@Dao
interface DailyLedgerDao {

    @Query("SELECT * FROM daily_ledger WHERE date = :date")
    fun observeByDate(date: LocalDate): Flow<DailyLedgerEntity?>

    @Query("SELECT * FROM daily_ledger WHERE date BETWEEN :start AND :end ORDER BY date DESC")
    fun observeBetween(start: LocalDate, end: LocalDate): Flow<List<DailyLedgerEntity>>

    @Query("SELECT * FROM daily_ledger WHERE date BETWEEN :start AND :end ORDER BY date")
    suspend fun getBetween(start: LocalDate, end: LocalDate): List<DailyLedgerEntity>

    @Query("SELECT * FROM daily_ledger WHERE date = :date")
    suspend fun getByDate(date: LocalDate): DailyLedgerEntity?

    @Query("SELECT MAX(date) FROM daily_ledger")
    suspend fun lastFinalizedDate(): LocalDate?

    /** Finalized rows are never overwritten; IGNORE keeps the first (immutable) write. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(ledger: DailyLedgerEntity)

    @Query("SELECT COALESCE(SUM(debt), 0) FROM daily_ledger WHERE reconciliationDue = 1")
    fun observeOutstandingDebt(): Flow<Int>

    @Query("DELETE FROM daily_ledger")
    suspend fun deleteAll()
}
