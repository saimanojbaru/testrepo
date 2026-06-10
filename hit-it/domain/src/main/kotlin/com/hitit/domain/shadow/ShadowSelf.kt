package com.hitit.domain.shadow

/**
 * Shadow Self — the unhinged dark mirror. Given the user's worst recent patterns, it roasts them in
 * Gen-Z slang: brutal but secretly caring (every roast ends pointing at the fix). Pure + deterministic
 * so the tone is testable. This is the "freaky" sibling of the measured Private Coach.
 */
data class ShadowSignals(
    val outstandingDebt: Int = 0,
    val missedRepsLast7: Int = 0,
    val skippedCheckInDays: Int = 0,
    val lowMomentumDays: Int = 0,   // days last week under the "coasting" line
    val impulseSpends: Int = 0,     // late-night burner buys
    val brokeStreak: Boolean = false,
)

data class ShadowRoast(val headline: String, val line: String, val severity: Int)

object ShadowSelf {

    /** The roasts that apply, worst first. Empty signals → a single "no notes" line. */
    fun roast(s: ShadowSignals): List<ShadowRoast> {
        val roasts = mutableListOf<ShadowRoast>()

        if (s.outstandingDebt >= 8) {
            roasts += ShadowRoast(
                "you're cooked 💀",
                "Momentum debt at ${s.outstandingDebt} and rising. This isn't a dip, it's a lifestyle. Clear ONE today before it becomes your whole personality.",
                severity = 5,
            )
        } else if (s.outstandingDebt in 1..7) {
            roasts += ShadowRoast(
                "small debt, big delusion",
                "${s.outstandingDebt} debt sitting there while you 'start tomorrow'. We both know tomorrow-you is also lazy. Do one rep now.",
                severity = 3,
            )
        }

        if (s.brokeStreak) {
            roasts += ShadowRoast(
                "streak said goodbye 👋",
                "All those days, gone, because you 'didn't feel like it'. The feeling was never coming, bestie. Start the new one today, not Monday.",
                severity = 4,
            )
        }

        if (s.missedRepsLast7 >= 6) {
            roasts += ShadowRoast(
                "ghosting your own goals",
                "${s.missedRepsLast7} reps skipped this week. You'd never leave the gc on read like this but your future self? crickets.",
                severity = 4,
            )
        } else if (s.missedRepsLast7 in 3..5) {
            roasts += ShadowRoast(
                "mid week, mid effort",
                "${s.missedRepsLast7} misses. Not a disaster, just deeply average. You didn't download this app to be average.",
                severity = 2,
            )
        }

        if (s.skippedCheckInDays >= 3) {
            roasts += ShadowRoast(
                "emotionally unavailable (to yourself)",
                "${s.skippedCheckInDays} days no check-in. 30 seconds of self-awareness and you chose doomscrolling. say less.",
                severity = 2,
            )
        }

        if (s.impulseSpends >= 2) {
            roasts += ShadowRoast(
                "1am gremlin spending",
                "${s.impulseSpends} impulse buys after dark. Your bank account is filing a missing-person report on your self-control.",
                severity = 3,
            )
        }

        if (s.lowMomentumDays >= 4) {
            roasts += ShadowRoast(
                "coasting in your villain era",
                "${s.lowMomentumDays} low days last week. You're not resting, you're rotting. Aesthetic? no. Fixable? yes — one win flips it.",
                severity = 3,
            )
        }

        if (roasts.isEmpty()) {
            roasts += ShadowRoast(
                "annoyingly locked in",
                "I came here to roast you and you left me nothing. No debt, streak intact, showing up. Disgusting. Keep going, freak.",
                severity = 0,
            )
        }

        return roasts.sortedByDescending { it.severity }
    }

    /** A one-line verdict for the header, scaled to how cooked they are. */
    fun verdict(s: ShadowSignals): String {
        val heat = s.outstandingDebt + s.missedRepsLast7 + s.impulseSpends * 2 + if (s.brokeStreak) 5 else 0
        return when {
            heat >= 16 -> "bro you're COOKED 💀🔥"
            heat >= 9 -> "it's giving... decline 📉"
            heat >= 4 -> "mid, but salvageable 🫠"
            else -> "ok you're kinda him rn ✨"
        }
    }
}
