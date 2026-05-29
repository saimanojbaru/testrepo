package com.hitit.app.data.repository

import com.hitit.app.data.local.dao.MomentumTxnDao
import com.hitit.app.data.local.dao.UserProfileDao
import com.hitit.app.data.local.entity.MomentumTxnEntity
import com.hitit.app.data.local.entity.UserProfileEntity
import kotlinx.coroutines.flow.Flow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Read-side access to the player profile. Level/Tier/progress are derived from the Momentum total
 * (via the domain LevelCurve/TierLadder) in the ViewModels, so the header is always correct even
 * before the persisted profile row exists.
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
}
