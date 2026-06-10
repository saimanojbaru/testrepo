package com.hitit.app.data.repository

import com.hitit.app.data.local.AppPreferences
import com.hitit.app.data.local.dao.RepDao
import com.hitit.domain.sacrifice.SacrificeEngine
import com.hitit.domain.sacrifice.SacrificeOffer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The altar. Executes a Streak Sacrifice atomically-ish: stamps the rep's streakResetAt (the live
 * streak dies), pays the Momentum bounty, banks the relic, and burns this month's ritual slot.
 * Ritual state (relics + month gate) is exposed as flows so Profile/RepDetail react immediately.
 */
@Singleton
class SacrificeRepository @Inject constructor(
    private val repDao: RepDao,
    private val profileRepository: ProfileRepository,
    private val prefs: AppPreferences,
) {
    private val _relics = MutableStateFlow(loadRelics())
    val relics: StateFlow<List<String>> = _relics.asStateFlow()

    private val _lastMonth = MutableStateFlow(prefs.lastSacrificeMonth)
    val lastSacrificeMonth: StateFlow<String?> = _lastMonth.asStateFlow()

    fun monthKey(today: LocalDate = LocalDate.now()): String =
        SacrificeEngine.monthKey(today.year, today.monthValue)

    fun isEligible(currentStreak: Int, today: LocalDate = LocalDate.now()): Boolean =
        SacrificeEngine.isEligible(currentStreak, monthKey(today), _lastMonth.value)

    suspend fun sacrifice(repId: Long, currentStreak: Int, today: LocalDate = LocalDate.now()): SacrificeOffer? {
        if (!isEligible(currentStreak, today)) return null
        val offer = SacrificeEngine.offerFor(currentStreak)
        repDao.setStreakReset(repId, today)
        profileRepository.addMomentum(offer.xp, reason = "streak_sacrifice", today = today)
        prefs.lastSacrificeMonth = monthKey(today)
        _lastMonth.value = prefs.lastSacrificeMonth
        if (offer.relicId !in _relics.value) {
            prefs.relicsCsv = (loadRelics() + offer.relicId).joinToString(",")
            _relics.value = loadRelics()
        }
        return offer
    }

    private fun loadRelics(): List<String> =
        prefs.relicsCsv.split(",").map { it.trim() }.filter { it.isNotEmpty() }
}
