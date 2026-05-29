package com.hitit.app.data.repository

import androidx.room.withTransaction
import com.hitit.app.data.local.HitItDatabase
import com.hitit.app.data.local.dao.HitTaskDao
import com.hitit.app.data.local.dao.MomentumTxnDao
import com.hitit.app.data.local.entity.HitTaskEntity
import com.hitit.app.data.local.entity.MomentumTxnEntity
import com.hitit.domain.model.Priority
import com.hitit.domain.momentum.MomentumCalculator
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Source of truth for Hits (tasks). Completing/undoing a Hit awards/reverses Momentum and recomputes
 * the profile inside one Room transaction. Only one task may be the Main Target on a given day.
 */
@Singleton
class TaskRepository @Inject constructor(
    private val db: HitItDatabase,
    private val taskDao: HitTaskDao,
    private val momentumDao: MomentumTxnDao,
    private val profileRepository: ProfileRepository,
) {
    fun observeAll(): Flow<List<HitTaskEntity>> = taskDao.observeAll()
    fun observeMainTarget(date: LocalDate): Flow<HitTaskEntity?> = taskDao.observeMainTarget(date)
    fun observeTask(id: Long): Flow<HitTaskEntity?> = taskDao.observeById(id)

    suspend fun getTask(id: Long): HitTaskEntity? = taskDao.getById(id)

    suspend fun saveTask(task: HitTaskEntity): Long =
        if (task.id == 0L) taskDao.insert(task) else { taskDao.update(task); task.id }

    suspend fun deleteTask(task: HitTaskEntity) = taskDao.delete(task)

    /** Toggle completion: awards (or reverses) Momentum scaled by priority and recomputes the profile. */
    suspend fun setDone(id: Long, done: Boolean, today: LocalDate) {
        db.withTransaction {
            val task = taskDao.getById(id) ?: return@withTransaction
            if (task.isDone == done) return@withTransaction
            taskDao.setDone(id, done, if (done) Instant.now() else null)
            val award = MomentumCalculator.awardForTask(priorityOf(task.priority))
            momentumDao.insert(
                MomentumTxnEntity(
                    amount = if (done) award else -award,
                    reason = if (done) REASON_DONE else REASON_UNDO,
                ),
            )
            profileRepository.recompute(today)
        }
    }

    /** Make [id] the single Main Target for [date], clearing any other task that held it. */
    suspend fun makeMainTarget(id: Long, date: LocalDate) {
        db.withTransaction {
            taskDao.clearMainTargetFor(date)
            taskDao.setMainTargetDate(id, date)
        }
    }

    /** Remove the Main Target flag from [id]. */
    suspend fun clearMainTarget(id: Long) = taskDao.setMainTargetDate(id, null)

    private fun priorityOf(name: String): Priority =
        runCatching { Priority.valueOf(name) }.getOrDefault(Priority.MEDIUM)

    private companion object {
        const val REASON_DONE = "task_done"
        const val REASON_UNDO = "task_undo"
    }
}
