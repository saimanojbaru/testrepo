package com.hitit.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.hitit.app.data.local.entity.MomentumTxnEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface MomentumTxnDao {

    @Insert
    suspend fun insert(txn: MomentumTxnEntity)

    @Query("SELECT COALESCE(SUM(amount), 0) FROM momentum_txns")
    suspend fun total(): Long

    @Query("SELECT COALESCE(SUM(amount), 0) FROM momentum_txns")
    fun observeTotal(): Flow<Long>

    @Query("SELECT * FROM momentum_txns ORDER BY timestamp DESC, id DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<MomentumTxnEntity>>

    @Query("DELETE FROM momentum_txns")
    suspend fun deleteAll()

    /** Net Momentum earned within [startEpochMs, endEpochMs) — used per-day by the flame check. */
    @Query("SELECT COALESCE(SUM(amount), 0) FROM momentum_txns WHERE timestamp >= :startEpochMs AND timestamp < :endEpochMs")
    suspend fun sumBetween(startEpochMs: Long, endEpochMs: Long): Int
}
