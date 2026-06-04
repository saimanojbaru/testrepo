package com.hitit.app.data.repository

import com.hitit.app.data.local.dao.CheckInDao
import com.hitit.app.data.local.dao.DailyLedgerDao
import com.hitit.app.data.local.dao.HitTaskDao
import com.hitit.app.data.local.dao.LockInSessionDao
import com.hitit.app.data.local.dao.RepDao
import com.hitit.app.data.local.dao.RepHitDao
import com.hitit.app.data.local.entity.DailyLedgerEntity
import com.hitit.app.data.mapper.toCore
import com.hitit.domain.ledger.DayRecord
import com.hitit.domain.ledger.MomentumDebtEngine
import com.hitit.domain.model.ScheduleEvaluator
import com.hitit.domain.momentum.MomentumScore
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the strict daily ledger. On app open it **finalizes** every past day that hasn't been
 * recorded yet — reconstructing that day's facts from the hit/check-in/focus history, scoring it,
 * and computing Momentum Debt via the pure [MomentumDebtEngine]. Finalized rows are immutable
 * (insert IGNORE), so history can't be rewritten.
 */
@Singleton
class LedgerRepository @Inject constructor(
    private val ledgerDao: DailyLedgerDao,
    private val repDao: RepDao,
    private val hitDao: RepHitDao,
    private val checkInDao: CheckInDao,
    private val taskDao: HitTaskDao,
    private val lockInDao: LockInSessionDao,
) {
    fun observeOutstandingDebt(): Flow<Int> = ledgerDao.observeOutstandingDebt()

    fun observeRecent(days: Int, today: LocalDate = LocalDate.now()): Flow<List<DailyLedgerEntity>> =
        ledgerDao.observeBetween(today.minusDays(days.toLong()), today.minusDays(1))

    suspend fun recent(days: Int, today: LocalDate = LocalDate.now()): List<DailyLedgerEntity> =
        ledgerDao.getBetween(today.minusDays(days.toLong()), today.minusDays(1))

    /** Finalize every past day from the earliest activity up to yesterday that isn't yet recorded. */
    suspend fun finalizePastDays(today: LocalDate = LocalDate.now()) {
        val lastDone = ledgerDao.lastFinalizedDate()
        // Start the day after the last finalized one, or a bounded look-back for a fresh install.
        val start = (lastDone?.plusDays(1)) ?: today.minusDays(MAX_BACKFILL_DAYS)
        var day = start
        while (day.isBefore(today)) {
            if (ledgerDao.getByDate(day) == null) {
                ledgerDao.insert(buildLedger(day))
            }
            day = day.plusDays(1)
        }
    }

    private suspend fun buildLedger(day: LocalDate): DailyLedgerEntity {
        val zone = ZoneId.systemDefault()
        val startMs = day.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = day.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()

        val reps = repDao.activeSnapshot().filter { ScheduleEvaluator.isActiveOn(it.toCore(), day) }
        val hitsByRep = hitDao.getForDate(day).associateBy { it.repId }
        val repsMet = reps.count { (hitsByRep[it.id]?.hitCount ?: 0) >= it.targetCount }

        val checkIn = checkInDao.getForDate(day)
        val checkedIn = checkIn != null &&
            (checkIn.morning.isNotBlank() || checkIn.evening.isNotBlank() || checkIn.mood != null)
        val focusMinutes = lockInDao.focusMinutesOn(day)
        val hitsCompleted = taskDao.completedCountBetween(startMs, endMs)

        val score = MomentumScore.score(
            repsScheduled = reps.size,
            repsMet = repsMet,
            checkedIn = checkedIn,
            hitsCompletedToday = hitsCompleted,
            focusMinutesToday = focusMinutes,
        )
        val debt = MomentumDebtEngine.calculate(
            DayRecord(
                momentumScore = score,
                repsScheduled = reps.size,
                repsMet = repsMet,
                hitsCompleted = hitsCompleted,
                focusMinutes = focusMinutes,
                checkedIn = checkedIn,
            ),
        )
        return DailyLedgerEntity(
            date = day,
            momentumScore = score,
            repsScheduled = reps.size,
            repsMet = repsMet,
            hitsCompleted = hitsCompleted,
            focusMinutes = focusMinutes,
            checkedIn = checkedIn,
            debt = debt.debt,
            reconciliationDue = debt.reconciliationDue,
        )
    }

    suspend fun clearAll() = ledgerDao.deleteAll()

    private companion object {
        const val MAX_BACKFILL_DAYS = 30L
    }
}
