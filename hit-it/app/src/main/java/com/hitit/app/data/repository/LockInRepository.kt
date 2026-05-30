package com.hitit.app.data.repository

import androidx.room.withTransaction
import com.hitit.app.data.local.HitItDatabase
import com.hitit.app.data.local.dao.LockInSessionDao
import com.hitit.app.data.local.dao.MomentumTxnDao
import com.hitit.app.data.local.entity.LockInSessionEntity
import com.hitit.app.data.local.entity.MomentumTxnEntity
import com.hitit.domain.momentum.MomentumCalculator
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists Lock In sessions and awards focus Momentum (1 per focused minute) in one transaction.
 */
@Singleton
class LockInRepository @Inject constructor(
    private val db: HitItDatabase,
    private val sessionDao: LockInSessionDao,
    private val momentumDao: MomentumTxnDao,
    private val profileRepository: ProfileRepository,
) {
    fun observeTotalFocusMinutes(): Flow<Int> = sessionDao.observeTotalFocusMinutes()
    fun observeSessionCount(): Flow<Int> = sessionDao.observeSessionCount()
    fun observeRecent(limit: Int = 10): Flow<List<LockInSessionEntity>> = sessionDao.observeRecent(limit)

    /** Record a finished/stopped session (if at least a minute was focused) and award Momentum. */
    suspend fun completeSession(
        startTime: Instant,
        endTime: Instant,
        plannedMinutes: Int,
        focusedMinutes: Int,
        repId: Long?,
        taskId: Long?,
        zone: String,
        completed: Boolean,
    ) {
        if (focusedMinutes <= 0) return
        val today = LocalDate.now(ZoneId.systemDefault())
        db.withTransaction {
            sessionDao.insert(
                LockInSessionEntity(
                    startTime = startTime,
                    endTime = endTime,
                    plannedMinutes = plannedMinutes,
                    focusedMinutes = focusedMinutes,
                    repId = repId,
                    taskId = taskId,
                    zone = zone,
                    completed = completed,
                    date = today,
                ),
            )
            val award = MomentumCalculator.awardForFocus(focusedMinutes)
            momentumDao.insert(MomentumTxnEntity(amount = award, reason = REASON_FOCUS, repId = repId))
            profileRepository.recompute(today)
        }
    }

    private companion object {
        const val REASON_FOCUS = "lock_in"
    }
}
