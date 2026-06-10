package com.hitit.domain.sacrifice

/**
 * Streak Sacrifice — the ritual. Once per calendar month you may burn a long living streak at the
 * altar for a massive Momentum payout and a rare relic. The streak's *record* stays in the trophy
 * case; the living streak resets to zero. Pure rules, fully tested: eligibility, payout, relic tier.
 */
data class SacrificeOffer(val xp: Long, val relicId: String, val relicName: String, val relicEmoji: String)

object SacrificeEngine {
    const val MIN_STREAK = 10
    const val XP_PER_DAY = 25L
    const val XP_CAP = 1_500L

    /** Eligible when the streak is long enough and this month's ritual is unused. */
    fun isEligible(currentStreak: Int, monthKey: String, lastSacrificeMonth: String?): Boolean =
        currentStreak >= MIN_STREAK && monthKey != lastSacrificeMonth

    /** The deal on the table for a given streak (what the altar pays). */
    fun offerFor(currentStreak: Int): SacrificeOffer {
        val xp = (currentStreak * XP_PER_DAY).coerceAtMost(XP_CAP)
        return when {
            currentStreak >= 30 -> SacrificeOffer(xp, "relic_eclipse", "Eclipse Relic", "🌑")
            currentStreak >= 20 -> SacrificeOffer(xp, "relic_blade", "Blade Relic", "🗡️")
            else -> SacrificeOffer(xp, "relic_ember", "Ember Relic", "🜂")
        }
    }

    /** Month key for the once-per-month gate, e.g. "2026-06". */
    fun monthKey(year: Int, month: Int): String = "%04d-%02d".format(year, month)

    fun ritualCopy(streak: Int, offer: SacrificeOffer): String =
        "Feed your $streak-day streak to the void. It dies; you gain +${offer.xp} ⚡ and the ${offer.relicEmoji} ${offer.relicName}. " +
            "Your record stays in the trophy case. The void does not refund."
}
