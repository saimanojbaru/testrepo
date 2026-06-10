package com.hitit.domain.glitch

/**
 * Reality Glitch — when momentum runs unreasonably hot, reality itself starts artifacting with
 * praise. Pure rules: the trigger threshold and a deterministic praise pick (seeded, so the visual
 * layer can rotate lines without RNG in the UI).
 */
object RealityGlitch {
    const val THRESHOLD = 90

    private val PRAISE = listOf(
        "REALITY CAN'T RENDER THIS",
        "STATS TOO HIGH. SIMULATION STRUGGLING",
        "WHO CODED THIS PLAYER",
        "MOMENTUM EXCEEDS SAFE LIMITS",
        "DEV TEAM HAS BEEN NOTIFIED OF YOUR EXISTENCE",
        "ok this is getting suspicious 👁️",
    )

    fun isEligible(momentumScore: Int): Boolean = momentumScore >= THRESHOLD

    /** Deterministic praise line for a seed (e.g. burst index) — cycles the pool. */
    fun praise(seed: Int): String = PRAISE[((seed % PRAISE.size) + PRAISE.size) % PRAISE.size]

    val praiseCount: Int get() = PRAISE.size
}
