package com.hitit.domain.surge

/** Why a surge fired — shapes the copy and the stakes. */
enum class SurgeKind { DEBT_RECOVERY, MONOLITH_BLITZ }

/**
 * A time-gated high-stakes window. Win (complete the target before [expiresAtMillis]) and the day
 * becomes an Overdrive entry; let it expire and Momentum Debt is doubled. Pure value type — `now`
 * is always passed in so timing is deterministic and testable.
 */
data class Surge(
    val kind: SurgeKind,
    val startedAtMillis: Long,
    val expiresAtMillis: Long,
) {
    fun isActive(nowMillis: Long): Boolean = nowMillis in startedAtMillis until expiresAtMillis
    fun isExpired(nowMillis: Long): Boolean = nowMillis >= expiresAtMillis
    fun remainingMillis(nowMillis: Long): Long = (expiresAtMillis - nowMillis).coerceAtLeast(0)
}

/** The live metrics the rule engine evaluates each time the dashboard refreshes. */
data class SurgeMetrics(
    val currentStreak: Int,
    val outstandingDebt: Int,
    val recentCompletionRate: Float, // 0f..1f over the recent window
)

/**
 * Deterministic rule engine that decides whether to deploy a [Surge], and applies the win/expire
 * consequences. Pure (no clock, no Android) — `now` is injected so everything is unit-testable.
 */
object SurgeEngine {
    const val DEBT_RECOVERY_MINUTES = 45L
    const val MONOLITH_BLITZ_MINUTES = 30L
    private const val MIN_MS = 60_000L

    const val DEBT_TRIGGER_THRESHOLD = 6
    const val DEBT_TRIGGER_RATE_BELOW = 0.65f
    const val MONOLITH_STREAK = 7
    const val MONOLITH_RATE_AT_LEAST = 0.90f

    /** Decide whether to start a new surge given [metrics]; null = stay normal. */
    fun evaluate(metrics: SurgeMetrics, nowMillis: Long): Surge? = when {
        metrics.outstandingDebt >= DEBT_TRIGGER_THRESHOLD &&
            metrics.recentCompletionRate < DEBT_TRIGGER_RATE_BELOW ->
            Surge(SurgeKind.DEBT_RECOVERY, nowMillis, nowMillis + DEBT_RECOVERY_MINUTES * MIN_MS)

        metrics.currentStreak >= MONOLITH_STREAK &&
            metrics.recentCompletionRate >= MONOLITH_RATE_AT_LEAST ->
            Surge(SurgeKind.MONOLITH_BLITZ, nowMillis, nowMillis + MONOLITH_BLITZ_MINUTES * MIN_MS)

        else -> null
    }

    /**
     * The debt consequence of a surge that EXPIRED unmet: the day's base debt is doubled.
     * Returns the new (replacement) debt value, clamped to the engine cap.
     */
    fun debtOnExpiry(baseDebt: Int, maxDebt: Int = 100): Int = (baseDebt * 2).coerceIn(0, maxDebt)

    /**
     * The EXTRA debt banked when a DEBT_RECOVERY surge expires unmet — i.e. the doubling delta
     * ([debtOnExpiry] minus the base). The live session adds this on top of the ledger's own debt
     * (the ledger itself stays immutable). Never negative; respects the same cap as [debtOnExpiry].
     */
    fun expiryDebtDelta(baseDebt: Int, maxDebt: Int = 100): Int =
        (debtOnExpiry(baseDebt, maxDebt) - baseDebt).coerceAtLeast(0)

    fun headline(kind: SurgeKind): String = when (kind) {
        SurgeKind.DEBT_RECOVERY -> "Surge: clear your debt"
        SurgeKind.MONOLITH_BLITZ -> "Surge: Monolith blitz"
    }

    fun detail(kind: SurgeKind, remainingMinutes: Long): String = when (kind) {
        SurgeKind.DEBT_RECOVERY ->
            "Finish a rep in $remainingMinutes min to wipe today's debt. Miss it and the debt doubles."
        SurgeKind.MONOLITH_BLITZ ->
            "You're hot. Land a rep in $remainingMinutes min for an Overdrive day on your grid."
    }
}
