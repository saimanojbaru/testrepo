package com.hitit.app.data.repository

import com.hitit.app.data.local.dao.BigPlayDao
import com.hitit.app.data.local.dao.CheckInDao
import com.hitit.app.data.local.dao.HitTaskDao
import com.hitit.app.data.local.dao.LockInSessionDao
import com.hitit.app.data.local.dao.MomentumTxnDao
import com.hitit.domain.momentum.LevelCurve
import com.hitit.domain.trophy.TrophyStats
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** Aggregates the cross-feature stats that drive Trophies and the Profile Stats section. */
@Singleton
class StatsRepository @Inject constructor(
    private val momentumDao: MomentumTxnDao,
    private val lockInDao: LockInSessionDao,
    private val checkInDao: CheckInDao,
    private val hitTaskDao: HitTaskDao,
    private val bigPlayDao: BigPlayDao,
    private val repRepository: RepRepository,
) {
    private data class Core(
        val momentum: Long,
        val focus: Int,
        val checkIns: Int,
        val tasks: Int,
        val bigPlays: Int,
    )

    fun observeStats(today: LocalDate): Flow<TrophyStats> {
        val core: Flow<Core> = combine(
            momentumDao.observeTotal(),
            lockInDao.observeTotalFocusMinutes(),
            checkInDao.observeCount(),
            hitTaskDao.observeCompletedCount(),
            bigPlayDao.observeCompletedCount(),
        ) { momentum, focus, checkIns, tasks, bigPlays ->
            Core(momentum, focus, checkIns, tasks, bigPlays)
        }
        return combine(core, repRepository.observeBestStreak(today)) { c, bestStreak ->
            TrophyStats(
                momentum = c.momentum,
                level = LevelCurve.levelFor(c.momentum),
                totalFocusMinutes = c.focus,
                checkInCount = c.checkIns,
                tasksCompleted = c.tasks,
                bigPlaysCompleted = c.bigPlays,
                bestStreak = bestStreak,
            )
        }
    }
}
