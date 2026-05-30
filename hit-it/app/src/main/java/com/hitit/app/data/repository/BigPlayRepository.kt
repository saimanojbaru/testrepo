package com.hitit.app.data.repository

import androidx.room.withTransaction
import com.hitit.app.data.local.HitItDatabase
import com.hitit.app.data.local.dao.BigPlayDao
import com.hitit.app.data.local.dao.MomentumTxnDao
import com.hitit.app.data.local.entity.BigPlayEntity
import com.hitit.app.data.local.entity.BigPlayWithCheckpoints
import com.hitit.app.data.local.entity.CheckpointEntity
import com.hitit.app.data.local.entity.MomentumTxnEntity
import com.hitit.domain.momentum.MomentumCalculator
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Source of truth for Big Plays and their checkpoints. Completing a checkpoint or a whole Big Play
 * awards Momentum (and reverses it on undo) and recomputes the profile in one transaction.
 */
@Singleton
class BigPlayRepository @Inject constructor(
    private val db: HitItDatabase,
    private val bigPlayDao: BigPlayDao,
    private val momentumDao: MomentumTxnDao,
    private val profileRepository: ProfileRepository,
) {
    fun observeAll(): Flow<List<BigPlayWithCheckpoints>> = bigPlayDao.observeAllWithCheckpoints()
    fun observeOne(id: Long): Flow<BigPlayWithCheckpoints?> = bigPlayDao.observeWithCheckpoints(id)

    suspend fun getPlay(id: Long): BigPlayEntity? = bigPlayDao.getById(id)

    suspend fun savePlay(play: BigPlayEntity): Long =
        if (play.id == 0L) bigPlayDao.insert(play) else { bigPlayDao.update(play); play.id }

    suspend fun deletePlay(play: BigPlayEntity) = bigPlayDao.delete(play)

    suspend fun addCheckpoint(planId: Long, title: String) {
        bigPlayDao.insertCheckpoint(
            CheckpointEntity(bigPlayId = planId, title = title.trim().ifBlank { "Checkpoint" }),
        )
    }

    suspend fun removeCheckpoint(id: Long) {
        val checkpoint = bigPlayDao.getCheckpoint(id) ?: return
        bigPlayDao.deleteCheckpoint(checkpoint)
    }

    suspend fun setCurrentValue(play: BigPlayEntity, value: Double) =
        bigPlayDao.update(play.copy(currentValue = value))

    suspend fun toggleCheckpoint(id: Long, done: Boolean) {
        db.withTransaction {
            val checkpoint = bigPlayDao.getCheckpoint(id) ?: return@withTransaction
            if (checkpoint.isDone == done) return@withTransaction
            bigPlayDao.updateCheckpoint(
                checkpoint.copy(isDone = done, doneAt = if (done) Instant.now() else null),
            )
            val award = MomentumCalculator.awardForCheckpoint()
            momentumDao.insert(
                MomentumTxnEntity(amount = if (done) award else -award, reason = REASON_CHECKPOINT),
            )
            profileRepository.recompute(LocalDate.now())
        }
    }

    suspend fun toggleCompleted(play: BigPlayEntity, completed: Boolean) {
        db.withTransaction {
            if (play.isCompleted == completed) return@withTransaction
            bigPlayDao.update(
                play.copy(isCompleted = completed, completedAt = if (completed) Instant.now() else null),
            )
            val award = MomentumCalculator.awardForBigPlay()
            momentumDao.insert(
                MomentumTxnEntity(amount = if (completed) award else -award, reason = REASON_BIG_PLAY),
            )
            profileRepository.recompute(LocalDate.now())
        }
    }

    private companion object {
        const val REASON_CHECKPOINT = "checkpoint"
        const val REASON_BIG_PLAY = "big_play"
    }
}
