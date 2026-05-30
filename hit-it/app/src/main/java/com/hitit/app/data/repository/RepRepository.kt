package com.hitit.app.data.repository

import androidx.room.withTransaction
import com.hitit.app.data.local.HitItDatabase
import com.hitit.app.data.local.dao.MomentumTxnDao
import com.hitit.app.data.local.dao.RepDao
import com.hitit.app.data.local.dao.RepHitDao
import com.hitit.app.data.local.entity.MomentumTxnEntity
import com.hitit.app.data.local.entity.RepEntity
import com.hitit.app.data.local.entity.RepHitEntity
import com.hitit.app.data.mapper.toCore
import com.hitit.app.data.mapper.toHitDay
import com.hitit.domain.model.StreakResult
import com.hitit.domain.momentum.MomentumCalculator
import com.hitit.domain.streak.StreakCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Source of truth for Reps and their hits. Hit logging and Momentum awarding happen in a single
 * Room transaction so the ledger and profile never drift from the hit records.
 */
@Singleton
class RepRepository @Inject constructor(
    private val db: HitItDatabase,
    private val repDao: RepDao,
    private val hitDao: RepHitDao,
    private val momentumDao: MomentumTxnDao,
    private val streakCalculator: StreakCalculator,
    private val profileRepository: ProfileRepository,
) {
    fun observeActiveReps(): Flow<List<RepEntity>> = repDao.observeActive()
    fun observeAllReps(): Flow<List<RepEntity>> = repDao.observeAll()
    fun observeRep(id: Long): Flow<RepEntity?> = repDao.observeById(id)
    fun observeHits(repId: Long): Flow<List<RepHitEntity>> = hitDao.observeForRep(repId)
    fun observeHitsBetween(start: LocalDate, end: LocalDate): Flow<List<RepHitEntity>> =
        hitDao.observeBetween(start, end)

    suspend fun getRep(id: Long): RepEntity? = repDao.getById(id)

    suspend fun saveRep(rep: RepEntity): Long =
        if (rep.id == 0L) repDao.insert(rep) else { repDao.update(rep); rep.id }

    suspend fun setArchived(id: Long, archived: Boolean) = repDao.setArchived(id, archived)

    suspend fun setRestMode(id: Long, start: LocalDate?, end: LocalDate?) =
        repDao.setRestMode(id, start, end)

    suspend fun deleteRep(rep: RepEntity) = repDao.delete(rep)

    /** Compute a streak from already-loaded data (pure-domain engine). */
    fun streakFor(rep: RepEntity, hits: List<RepHitEntity>, today: LocalDate): StreakResult =
        streakCalculator.calculate(rep.toCore(), hits.map { it.toHitDay() }, today)

    /** Best (longest) streak across all active Reps — used for streak Trophies. */
    fun observeBestStreak(today: LocalDate): Flow<Int> = combine(
        repDao.observeActive(),
        hitDao.observeBetween(today.minusDays(STREAK_WINDOW_DAYS), today),
    ) { reps, hits ->
        val hitsByRep = hits.groupBy { it.repId }
        reps.maxOfOrNull { rep ->
            streakCalculator.calculate(rep.toCore(), hitsByRep[rep.id].orEmpty().map { it.toHitDay() }, today)
                .longestStreak
        } ?: 0
    }

    /** Log one hit for [repId] on [today], up to the Rep's target. Awards Momentum on becoming met. */
    suspend fun logHit(repId: Long, today: LocalDate) {
        db.withTransaction {
            val rep = repDao.getById(repId) ?: return@withTransaction
            val existing = hitDao.getForRepOnDate(repId, today)
            val previousCount = existing?.hitCount ?: 0
            if (previousCount >= rep.targetCount) return@withTransaction // already met; nothing to add

            val newCount = (previousCount + 1).coerceAtMost(rep.targetCount)
            hitDao.upsert(
                RepHitEntity(
                    id = existing?.id ?: 0,
                    repId = repId,
                    date = today,
                    hitCount = newCount,
                    loggedZone = ZoneId.systemDefault().id,
                    timestamp = Instant.now(),
                ),
            )

            if (newCount >= rep.targetCount) {
                val hits = hitDao.getForRep(repId)
                val streak = streakCalculator.calculate(rep.toCore(), hits.map { it.toHitDay() }, today)
                val award = MomentumCalculator.awardForHit(streak.currentStreak)
                momentumDao.insert(MomentumTxnEntity(amount = award, reason = REASON_HIT, repId = repId))
                profileRepository.recompute(today)
            }
        }
    }

    /** Clear today's hit for [repId]. If it had been met, posts a compensating Momentum entry. */
    suspend fun clearHit(repId: Long, today: LocalDate) {
        db.withTransaction {
            val rep = repDao.getById(repId) ?: return@withTransaction
            val existing = hitDao.getForRepOnDate(repId, today) ?: return@withTransaction
            val wasMet = existing.hitCount >= rep.targetCount

            if (wasMet) {
                val hits = hitDao.getForRep(repId)
                val streak = streakCalculator.calculate(rep.toCore(), hits.map { it.toHitDay() }, today)
                val refund = MomentumCalculator.awardForHit(streak.currentStreak)
                momentumDao.insert(MomentumTxnEntity(amount = -refund, reason = REASON_UNDO, repId = repId))
            }
            hitDao.deleteForRepOnDate(repId, today)
            profileRepository.recompute(today)
        }
    }

    private companion object {
        const val REASON_HIT = "rep_hit"
        const val REASON_UNDO = "rep_hit_undo"
        const val STREAK_WINDOW_DAYS = 400L
    }
}
