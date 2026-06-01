package com.hitit.app.data.repository

import androidx.room.withTransaction
import com.hitit.app.data.local.HitItDatabase
import com.hitit.app.data.local.dao.IdentityDao
import com.hitit.app.data.local.dao.MomentumTxnDao
import com.hitit.app.data.local.entity.IdentityEntity
import com.hitit.app.data.local.entity.MomentumTxnEntity
import com.hitit.domain.identity.IdentityCatalog
import com.hitit.domain.trophy.TrophyStats
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists Identity Card unlocks. [sync] is idempotent (insert IGNORE + id diff) and gives a small
 * one-time unlock Momentum bonus. The ongoing per-action bonus is applied in [RepRepository.logHit].
 */
@Singleton
class IdentityRepository @Inject constructor(
    private val db: HitItDatabase,
    private val identityDao: IdentityDao,
    private val momentumDao: MomentumTxnDao,
    private val profileRepository: ProfileRepository,
) {
    fun observeUnlocked(): Flow<List<IdentityEntity>> = identityDao.observeAll()

    suspend fun sync(stats: TrophyStats, today: LocalDate) {
        val earned = IdentityCatalog.evaluate(stats)
        if (earned.isEmpty()) return
        db.withTransaction {
            val already = identityDao.unlockedIds().toSet()
            val newlyEarned = earned - already
            if (newlyEarned.isEmpty()) return@withTransaction
            newlyEarned.forEach { id -> identityDao.insert(IdentityEntity(id = id)) }
            momentumDao.insert(MomentumTxnEntity(amount = UNLOCK_BONUS * newlyEarned.size, reason = REASON))
            profileRepository.recompute(today)
        }
    }

    private companion object {
        const val REASON = "identity_unlock"
        const val UNLOCK_BONUS = 25
    }
}
