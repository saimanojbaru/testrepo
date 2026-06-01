package com.hitit.app.data.repository

import androidx.room.withTransaction
import com.hitit.app.data.local.HitItDatabase
import com.hitit.app.data.local.dao.MomentumTxnDao
import com.hitit.app.data.local.dao.RepDao
import com.hitit.app.data.local.dao.RepHitDao
import com.hitit.app.data.local.dao.SuddenDeathDao
import com.hitit.app.data.local.entity.MomentumTxnEntity
import com.hitit.app.data.local.entity.SuddenDeathPenaltyEntity
import com.hitit.app.data.mapper.toCore
import com.hitit.app.data.mapper.toHitDay
import com.hitit.domain.momentum.MomentumCalculator
import com.hitit.domain.suddendeath.SuddenDeathEvaluator
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Applies Sudden Death penalties for missed scheduled days. Idempotent: each (rep, missed-date) is
 * recorded so re-running (daily worker, app open, catch-up after the device was off) never
 * double-charges. Returns the total number of newly-penalized days so the UI/notification can react.
 */
@Singleton
class SuddenDeathRepository @Inject constructor(
    private val db: HitItDatabase,
    private val repDao: RepDao,
    private val hitDao: RepHitDao,
    private val momentumDao: MomentumTxnDao,
    private val suddenDeathDao: SuddenDeathDao,
    private val profileRepository: ProfileRepository,
) {
    suspend fun evaluate(today: LocalDate = LocalDate.now()): Int {
        var penalized = 0
        val active = repDao.activeSnapshot()
        for (rep in active) {
            if (!rep.isSuddenDeath) continue
            val hits = hitDao.getForRep(rep.id).map { it.toHitDay() }
            val already = suddenDeathDao.penalizedDates(rep.id).toSet()
            val misses = SuddenDeathEvaluator.newMisses(rep.toCore(), hits, already, today)
            if (misses.isEmpty()) continue
            db.withTransaction {
                misses.forEach { date ->
                    suddenDeathDao.record(SuddenDeathPenaltyEntity(repId = rep.id, date = date))
                    momentumDao.insert(
                        MomentumTxnEntity(
                            amount = -MomentumCalculator.suddenDeathPenalty(),
                            reason = REASON,
                            repId = rep.id,
                        ),
                    )
                    penalized++
                }
                profileRepository.recompute(today)
            }
        }
        return penalized
    }

    private companion object {
        const val REASON = "sudden_death_penalty"
    }
}
