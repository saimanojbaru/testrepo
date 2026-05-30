package com.hitit.app.data.repository

import androidx.room.withTransaction
import com.hitit.app.data.local.HitItDatabase
import com.hitit.app.data.local.dao.MomentumTxnDao
import com.hitit.app.data.local.dao.TrophyDao
import com.hitit.app.data.local.entity.MomentumTxnEntity
import com.hitit.app.data.local.entity.TrophyEntity
import com.hitit.domain.trophy.TrophyCatalog
import com.hitit.domain.trophy.TrophyStats
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Persists Trophy unlocks. [sync] is idempotent: it inserts only newly-earned Trophies (IGNORE +
 * id diff), awards their one-time Momentum bonus, and recomputes the profile — so repeated calls
 * converge and never double-award.
 */
@Singleton
class TrophyRepository @Inject constructor(
    private val db: HitItDatabase,
    private val trophyDao: TrophyDao,
    private val momentumDao: MomentumTxnDao,
    private val profileRepository: ProfileRepository,
) {
    fun observeUnlocked(): Flow<List<TrophyEntity>> = trophyDao.observeAll()

    suspend fun sync(stats: TrophyStats, today: LocalDate) {
        val earned = TrophyCatalog.evaluate(stats)
        if (earned.isEmpty()) return
        db.withTransaction {
            val already = trophyDao.unlockedIds().toSet()
            val newlyEarned = earned - already
            if (newlyEarned.isEmpty()) return@withTransaction
            var bonus = 0
            newlyEarned.forEach { id ->
                trophyDao.insert(TrophyEntity(id = id))
                bonus += TrophyCatalog.byId(id)?.bonus ?: 0
            }
            if (bonus > 0) {
                momentumDao.insert(MomentumTxnEntity(amount = bonus, reason = REASON_TROPHY))
                profileRepository.recompute(today)
            }
        }
    }

    private companion object {
        const val REASON_TROPHY = "trophy"
    }
}
