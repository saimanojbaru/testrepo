package com.hitit.app.data.repository

import com.hitit.app.data.local.entity.DailyLedgerEntity
import com.hitit.domain.coach.CoachDay
import com.hitit.domain.coach.CoachEngine
import com.hitit.domain.coach.CoachInsight
import com.hitit.domain.coach.CoachRephraser
import com.hitit.domain.ledger.DayRecord
import java.time.DayOfWeek
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridges the persisted ledger to the pure [CoachEngine] (rule-based "brutally honest coach"), and
 * optionally asks the on-device LLM [CoachRephraser] to rewrite the review generatively — falling
 * back to the structured rule-based insights whenever the LLM is unavailable or fails.
 */
@Singleton
class CoachRepository @Inject constructor(
    private val ledgerRepository: LedgerRepository,
    private val rephraser: CoachRephraser,
) {
    /** The weekly review over the last [days] finalized days, plus the single daily nudge. */
    suspend fun review(days: Int = 7, today: LocalDate = LocalDate.now()): CoachReport {
        val coachDays = ledgerRepository.recent(days, today).map { it.toCoachDay() }
        val weekly = CoachEngine.weeklyReview(coachDays)
        // Optional generative rewrite; null => show the structured rule-based cards.
        val llmText = runCatching { rephraser.rephrase(weekly) }.getOrNull()
        val llmActive = runCatching { rephraser.isReady() }.getOrDefault(false)
        return CoachReport(
            weekly = weekly,
            nudge = CoachEngine.dailyNudge(coachDays),
            llmSummary = llmText,
            llmActive = llmActive,
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

data class CoachReport(
    val weekly: List<CoachInsight>,
    val nudge: CoachInsight,
    val llmSummary: String? = null,
    val llmActive: Boolean = false,
)
