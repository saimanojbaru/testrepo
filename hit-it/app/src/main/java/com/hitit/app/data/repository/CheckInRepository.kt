package com.hitit.app.data.repository

import androidx.room.withTransaction
import com.hitit.app.data.local.HitItDatabase
import com.hitit.app.data.local.dao.CheckInDao
import com.hitit.app.data.local.dao.MomentumTxnDao
import com.hitit.app.data.local.entity.CheckInEntity
import com.hitit.app.data.local.entity.MomentumTxnEntity
import com.hitit.domain.momentum.MomentumCalculator
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Source of truth for daily Check-Ins. Saving awards a flat Momentum the first time the morning and
 * the evening entries get content (sticky, so editing later doesn't re-award), then recomputes the
 * profile — all in one transaction.
 */
@Singleton
class CheckInRepository @Inject constructor(
    private val db: HitItDatabase,
    private val checkInDao: CheckInDao,
    private val momentumDao: MomentumTxnDao,
    private val profileRepository: ProfileRepository,
) {
    fun observeForDate(date: LocalDate): Flow<CheckInEntity?> = checkInDao.observeForDate(date)
    fun observeActiveDates(): Flow<List<LocalDate>> = checkInDao.observeActiveDates()
    fun observeCount(): Flow<Int> = checkInDao.observeCount()

    suspend fun getForDate(date: LocalDate): CheckInEntity? = checkInDao.getForDate(date)

    suspend fun save(
        date: LocalDate,
        morning: String,
        evening: String,
        mood: Int?,
        energy: Int?,
    ) {
        db.withTransaction {
            val existing = checkInDao.getForDate(date)
            val morningAlready = existing?.morningAwarded ?: false
            val eveningAlready = existing?.eveningAwarded ?: false

            val morningNow = morning.isNotBlank()
            val eveningNow = evening.isNotBlank()
            val newMorningAwarded = morningAlready || morningNow
            val newEveningAwarded = eveningAlready || eveningNow

            var newAwards = 0
            if (!morningAlready && newMorningAwarded) newAwards++
            if (!eveningAlready && newEveningAwarded) newAwards++

            checkInDao.upsert(
                CheckInEntity(
                    date = date,
                    morning = morning.trim(),
                    evening = evening.trim(),
                    mood = mood,
                    energy = energy,
                    morningAwarded = newMorningAwarded,
                    eveningAwarded = newEveningAwarded,
                    updatedAt = Instant.now(),
                ),
            )

            if (newAwards > 0) {
                momentumDao.insert(
                    MomentumTxnEntity(
                        amount = MomentumCalculator.awardForCheckIn() * newAwards,
                        reason = REASON_CHECK_IN,
                    ),
                )
                profileRepository.recompute(date)
            }
        }
    }

    private companion object {
        const val REASON_CHECK_IN = "check_in"
    }
}
