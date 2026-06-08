package com.hitit.app.coach

import com.hitit.domain.coach.CoachInsight
import com.hitit.domain.coach.CoachRephraser
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Lite-flavor stand-in for the on-device LLM rephraser.
 *
 * The lite distribution deliberately omits the MediaPipe dependency (that's what keeps this build
 * small, native-lib-free, and installable on any ABI). This stub keeps the same class name as the
 * full flavor's implementation so the shared Hilt binding in [com.hitit.app.di.CoachModule] compiles
 * unchanged — it simply reports "no model" and never rephrases, so the Coach always uses its
 * always-on rule-based engine. Swap to the `full` flavor to get the real on-device LLM.
 */
@Singleton
class MediaPipeCoachRephraser @Inject constructor() : CoachRephraser {
    override suspend fun isReady(): Boolean = false
    override suspend fun rephrase(insights: List<CoachInsight>): String? = null
}
