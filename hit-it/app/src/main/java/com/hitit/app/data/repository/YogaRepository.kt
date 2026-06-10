package com.hitit.app.data.repository

import com.hitit.app.data.local.dao.YogaPoseDao
import com.hitit.app.data.local.dao.YogaSessionDao
import com.hitit.app.data.local.entity.YogaPoseEntity
import com.hitit.app.data.local.entity.YogaSessionEntity
import com.hitit.domain.pose.PoseCodec
import com.hitit.domain.pose.PoseLandmark
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pose Freak's data: user-captured reference poses (landmarks via the domain PoseCodec) and the
 * practice sessions graded against them. Detection itself lives in the full flavor; this storage
 * layer is shared so both flavors render the library identically.
 */
@Singleton
class YogaRepository @Inject constructor(
    private val poseDao: YogaPoseDao,
    private val sessionDao: YogaSessionDao,
) {
    fun observePoses(): Flow<List<YogaPoseEntity>> = poseDao.observeAll()

    fun observeRecentSessions(): Flow<List<YogaSessionEntity>> = sessionDao.observeRecent()

    suspend fun savePose(name: String, landmarks: List<PoseLandmark>): Long =
        poseDao.insert(YogaPoseEntity(name = name.trim(), landmarksCsv = PoseCodec.encode(landmarks)))

    /** Decoded reference landmarks, or null when the pose is gone/corrupt. */
    suspend fun reference(poseId: Long): List<PoseLandmark>? =
        poseDao.getById(poseId)?.let { PoseCodec.decode(it.landmarksCsv) }

    suspend fun poseName(poseId: Long): String? = poseDao.getById(poseId)?.name

    suspend fun logSession(poseId: Long, bestScore: Int, holdSeconds: Int, date: LocalDate = LocalDate.now()) {
        sessionDao.insert(
            YogaSessionEntity(poseId = poseId, date = date, bestScore = bestScore, holdSeconds = holdSeconds),
        )
    }

    suspend fun deletePose(poseId: Long) {
        sessionDao.deleteForPose(poseId)
        poseDao.delete(poseId)
    }

    suspend fun clearAll() {
        sessionDao.deleteAll()
        poseDao.deleteAll()
    }
}
