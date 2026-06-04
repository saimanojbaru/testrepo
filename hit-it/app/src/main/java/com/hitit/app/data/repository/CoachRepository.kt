package com.hitit.app.data.repository

import com.hitit.app.data.local.entity.DailyLedgerEntity
import com.hitit.domain.coach.CoachDay
import com.hitit.domain.coach.CoachEngine
import com.hitit.domain.coach.CoachInsight
import com.hitit.domain.ledger.DayRecord
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Bridges the persisted ledger to the pure [CoachEngine] (rule-based "brutally honest coach"). */
@Singleton
class CoachRepository @Inject constructor(
    private val ledgerRepository: LedgerRepository,
) {
    /** The weekly review over the last [days] finalized days, plus the single daily nudge. */
    suspend fun review(days: Int = 7, today: LocalDate = LocalDate.now()): CoachReport {
        val coachDays = ledgerRepository.recent(days, today).map { it.toCoachDay() }
        return CoachReport(
            weekly = CoachEngine.weeklyReview(coachDays),
            nudge = CoachEngine.dailyNudge(coachDays),
        )
    }

    private fun DailyLedgerEntity.toCoachDay(): CoachDay {
        val iso = date.dayOfWeek.value // 1=Mon..7=Sun
        return CoachDay(
            isoDayOfWeek = iso,
            isWeekend = date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY,
            record = DayRecord(
                momentumScore = momentumScore,
                repsScheduled = repsScheduled,
                repsMet = repsMet,
                hitsCompleted = hitsCompleted,
                focusMinutes = focusMinutes,
                checkedIn = checkedIn,
            ),
        )
    }
}

data class CoachReport(val weekly: List<CoachInsight>, val nudge: CoachInsight)
