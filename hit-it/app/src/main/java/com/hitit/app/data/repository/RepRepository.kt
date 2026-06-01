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
import com.hitit.app.reminder.ReminderScheduler
import com.hitit.domain.model.StreakResult
import com.hitit.domain.momentum.MomentumCalculator
import com.hitit.domain.streak.StreakCalculator
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Compact view of today's progress for the home-screen widget. */
data class TodaySnapshot(val total: Int, val done: Int)

/** Outcome of logging a hit, so the UI can celebrate (haptics / confetti). */
data class LogResult(
    val logged: Boolean = false,
    val met: Boolean = false,
    val perfectDay: Boolean = false,
    val momentumAwarded: Int = 0,
)

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
    private val reminderScheduler: ReminderScheduler,
) {
    fun observeActiveReps(): Flow<List<RepEntity>> = repDao.observeActive()
    fun observeAllReps(): Flow<List<RepEntity>> = repDao.observeAll()
    fun observeRep(id: Long): Flow<RepEntity?> = repDao.observeById(id)
    fun observeHits(repId: Long): Flow<List<RepHitEntity>> = hitDao.observeForRep(repId)
    fun observeHitsBetween(start: LocalDate, end: LocalDate): Flow<List<RepHitEntity>> =
        hitDao.observeBetween(start, end)

    suspend fun getRep(id: Long): RepEntity? = repDao.getById(id)

    suspend fun saveRep(rep: RepEntity): Long {
        val id = if (rep.id == 0L) repDao.insert(rep) else { repDao.update(rep); rep.id }
        reminderScheduler.applyForRep(id, rep.reminderEnabled, rep.reminderHour, rep.reminderMinute)
        return id
    }

    suspend fun setArchived(id: Long, archived: Boolean) {
        repDao.setArchived(id, archived)
        // Archived reps shouldn't nag; re-arm when unarchived if the reminder is on.
        if (archived) {
            reminderScheduler.cancel(id)
        } else {
            repDao.getById(id)?.let { reminderScheduler.applyForRep(it.id, it.reminderEnabled, it.reminderHour, it.reminderMinute) }
        }
    }

    suspend fun setRestMode(id: Long, start: LocalDate?, end: LocalDate?) =
        repDao.setRestMode(id, start, end)

    suspend fun deleteRep(rep: RepEntity) {
        reminderScheduler.cancel(rep.id)
        repDao.delete(rep)
    }

    /** Compute a streak from already-loaded data (pure-domain engine). */
    fun streakFor(rep: RepEntity, hits: List<RepHitEntity>, today: LocalDate): StreakResult =
        streakCalculator.calculate(rep.toCore(), hits.map { it.toHitDay() }, today)

    /** A compact snapshot of today's active Reps for the home-screen widget. */
    suspend fun todaySnapshot(today: LocalDate): TodaySnapshot {
        val reps = repDao.observeActive().first()
        val hitsToday = hitDao.getForDate(today).associateBy { it.repId }
        val active = reps.filter {
            com.hitit.domain.model.ScheduleEvaluator.isActiveOn(it.toCore(), today)
        }
        val done = active.count { (hitsToday[it.id]?.hitCount ?: 0) >= it.targetCount }
        return TodaySnapshot(total = active.size, done = done)
    }

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

    /**
     * Log one hit for [repId] on [today], up to the Rep's target. Awards Momentum on becoming met,
     * applying the Perfect Day 1.5x multiplier when this completion makes every scheduled rep met.
     * Returns a [LogResult] so the UI can celebrate (confetti + haptics).
     */
    suspend fun logHit(repId: Long, today: LocalDate): LogResult {
        return db.withTransaction {
            val rep = repDao.getById(repId) ?: return@withTransaction LogResult()
            val existing = hitDao.getForRepOnDate(repId, today)
            val previousCount = existing?.hitCount ?: 0
            if (previousCount >= rep.targetCount) return@withTransaction LogResult()

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

            if (newCount < rep.targetCount) return@withTransaction LogResult(logged = true)

            // Active Recovery: logging during this rep's rest-mode window earns reduced "recovery"
            // Momentum and skips streak/Perfect-Day logic (the streak stays frozen during rest mode).
            if (isResting(rep.restModeStart, rep.restModeEnd, today)) {
                val recovery = MomentumCalculator.awardForRecovery()
                momentumDao.insert(MomentumTxnEntity(amount = recovery, reason = REASON_RECOVERY, repId = repId))
                profileRepository.recompute(today)
                return@withTransaction LogResult(logged = true, met = true, momentumAwarded = recovery)
            }

            // This rep is now met. Determine whether it completes a Perfect Day.
            val active = repDao.observeActive().first()
                .filter { com.hitit.domain.model.ScheduleEvaluator.isActiveOn(it.toCore(), today) }
            val hitsToday = hitDao.getForDate(today).associateBy { it.repId }
            val metBefore = active.count { r ->
                r.id != repId && (hitsToday[r.id]?.hitCount ?: 0) >= r.targetCount
            }
            val perfect = MomentumCalculator.completesPerfectDay(active.size, metBefore)

            val hits = hitDao.getForRep(repId)
            val streak = streakCalculator.calculate(rep.toCore(), hits.map { it.toHitDay() }, today)
            val award = MomentumCalculator.applyPerfectDay(
                MomentumCalculator.awardForHit(streak.currentStreak),
                perfect,
            )
            momentumDao.insert(
                MomentumTxnEntity(
                    amount = award,
                    reason = if (perfect) REASON_PERFECT else REASON_HIT,
                    repId = repId,
                ),
            )
            profileRepository.recompute(today)
            LogResult(logged = true, met = true, perfectDay = perfect, momentumAwarded = award)
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

    private fun isResting(start: LocalDate?, end: LocalDate?, today: LocalDate): Boolean {
        if (start == null) return false
        val effectiveEnd = end ?: today
        return !today.isBefore(start) && !today.isAfter(effectiveEnd)
    }

    private companion object {
        const val REASON_HIT = "rep_hit"
        const val REASON_PERFECT = "rep_hit_perfect"
        const val REASON_RECOVERY = "active_recovery"
        const val REASON_UNDO = "rep_hit_undo"
        const val STREAK_WINDOW_DAYS = 400L
    }
}
