package com.hitit.app.data.repository

import com.hitit.app.data.local.dao.MomentumTxnDao
import com.hitit.app.data.local.dao.UserProfileDao
import com.hitit.app.data.local.entity.MomentumTxnEntity
import com.hitit.app.data.local.entity.UserProfileEntity
import com.hitit.domain.momentum.LevelCurve
import com.hitit.domain.momentum.TierLadder
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Access to the player profile. Level/Tier/progress are derived from the Momentum total (via the
 * domain LevelCurve/TierLadder), so the header is always correct even before the persisted profile
 * row exists. [recompute] is called by the write paths (Reps and Hits) inside their transactions to
 * keep the stored profile in sync with the Momentum ledger.
 */
@Singleton
class ProfileRepository @Inject constructor(
    private val profileDao: UserProfileDao,
    private val momentumDao: MomentumTxnDao,
) {
    fun observeProfile(): Flow<UserProfileEntity?> = profileDao.observe()
    fun observeMomentumTotal(): Flow<Long> = momentumDao.observeTotal()
    fun observeRecentMomentum(limit: Int = 20): Flow<List<MomentumTxnEntity>> =
        momentumDao.observeRecent(limit)

    /** Recompute level/tier from the Momentum ledger total and persist the profile row. */
    suspend fun recompute(today: LocalDate) {
        val total = momentumDao.total().coerceAtLeast(0L)
        val level = LevelCurve.levelFor(total)
        val tier = TierLadder.tierFor(level).name
        val joinDate = profileDao.get()?.joinDate ?: today
        profileDao.upsert(
            UserProfileEntity(
                id = UserProfileEntity.ID,
                momentum = total,
                level = level,
                tier = tier,
                joinDate = joinDate,
            ),
        )
    }

    /** Post a Momentum ledger entry and recompute the profile (used by the demo seeder). */
    suspend fun addMomentum(amount: Long, reason: String, today: LocalDate) {
        momentumDao.insert(MomentumTxnEntity(amount = amount.toInt(), reason = reason))
        recompute(today)
    }

    /** Clear the entire Momentum ledger and reset the profile to level 1 (used by demo clear). */
    suspend fun resetAll(today: LocalDate) {
        momentumDao.deleteAll()
        recompute(today)
    }
}
