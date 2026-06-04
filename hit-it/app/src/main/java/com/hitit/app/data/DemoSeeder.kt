package com.hitit.app.data

import com.hitit.app.data.local.AppPreferences
import com.hitit.app.data.local.dao.CheckInDao
import com.hitit.app.data.local.dao.HitTaskDao
import com.hitit.app.data.local.dao.RepDao
import com.hitit.app.data.local.dao.RepHitDao
import com.hitit.app.data.local.entity.CheckInEntity
import com.hitit.app.data.local.entity.HitTaskEntity
import com.hitit.app.data.local.entity.RepEntity
import com.hitit.app.data.local.entity.RepHitEntity
import com.hitit.app.data.repository.ProfileRepository
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.random.Random

/**
 * Seeds relatable demo data on first launch so the app feels alive instead of a blank canvas:
 * 3 Reps with ~30 days of history (so The Grid glows and Profile shows a real level), a Main Target
 * Hit, and a Check-In. Clearable from Profile via [clear]. Idempotent via [AppPreferences.demoSeeded].
 */
@Singleton
class DemoSeeder @Inject constructor(
    private val repDao: RepDao,
    private val hitDao: RepHitDao,
    private val taskDao: HitTaskDao,
    private val checkInDao: CheckInDao,
    private val profileRepository: ProfileRepository,
    private val ledgerRepository: com.hitit.app.data.repository.LedgerRepository,
    private val prefs: AppPreferences,
) {
    suspend fun seedIfNeeded() {
        if (prefs.demoSeeded) return
        // Only seed a genuinely empty install (don't clobber a user who already has reps).
        if (repDao.count() == 0) seed()
        prefs.demoSeeded = true
    }

    private suspend fun seed() {
        val today = LocalDate.now()
        val created = today.minusDays(SEED_DAYS.toLong())

        data class Spec(val name: String, val emoji: String, val color: String, val density: Double)
        val specs = listOf(
            Spec("Morning Run", "🏃", "#00E5C0", 0.85),
            Spec("Read 20 pages", "📚", "#A78BFA", 0.7),
            Spec("Deep Work", "💻", "#FFC857", 0.55),
        )

        val rng = Random(42) // deterministic, pleasant-looking grid
        specs.forEachIndexed { index, spec ->
            val repId = repDao.insert(
                RepEntity(
                    name = spec.name,
                    emoji = spec.emoji,
                    colorHex = spec.color,
                    scheduleType = "DAILY",
                    sortOrder = index,
                    createdDate = created,
                ),
            )
            // History: most recent days dense (to give a live current streak), older days sparser.
            for (offset in 0..SEED_DAYS) {
                val date = today.minusDays(offset.toLong())
                val recencyBoost = if (offset < 6) 0.25 else 0.0
                if (rng.nextDouble() < (spec.density + recencyBoost)) {
                    hitDao.upsert(
                        RepHitEntity(repId = repId, date = date, hitCount = 1, timestamp = Instant.now()),
                    )
                }
            }
            // Guarantee a visible recent streak on the first rep.
            if (index == 0) {
                for (offset in 0..4) {
                    hitDao.upsert(RepHitEntity(repId = repId, date = today.minusDays(offset.toLong()), hitCount = 1))
                }
            }
        }

        // A Main Target for today + a couple of open Hits.
        taskDao.insert(
            HitTaskEntity(title = "Plan tomorrow's training", priority = "HIGH", mainTargetDate = today),
        )
        taskDao.insert(HitTaskEntity(title = "Stretch 10 min", priority = "MEDIUM"))

        // Today's check-in so the dashboard shows a mood.
        checkInDao.upsert(
            CheckInEntity(
                date = today,
                morning = "Feeling strong. Let's build momentum.",
                evening = "",
                mood = 8,
                energy = 7,
                morningAwarded = true,
            ),
        )

        // Seed enough Momentum that the player starts at a believable level/tier.
        profileRepository.addMomentum(SEED_MOMENTUM, reason = "demo_seed", today = today)
    }

    /** Wipe demo data and reset Momentum so the user gets a clean slate. */
    suspend fun clear() {
        // Deleting reps cascades to their hits; then clear tasks, check-ins, and the ledger/profile.
        repDao.deleteAll()
        taskDao.deleteAll()
        checkInDao.deleteAll()
        ledgerRepository.clearAll()
        profileRepository.resetAll(LocalDate.now())
    }

    private companion object {
        const val SEED_DAYS = 34
        const val SEED_MOMENTUM = 900L // ~level 4-5, a credible "returning athlete" start
    }
}
