package com.sortora.app.data.repository

import com.sortora.app.data.local.EmbeddingCodec
import com.sortora.app.data.local.FaceDao
import com.sortora.app.data.local.FaceEntity
import com.sortora.app.data.local.SessionDao
import com.sortora.app.domain.model.FaceRecord
import com.sortora.app.domain.model.FaceSample
import com.sortora.app.domain.repository.FaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoomFaceRepository @Inject constructor(
    private val dao: FaceDao,
    private val sessionDao: SessionDao,
    private val thumbnails: FaceThumbnailStore,
) : FaceRepository {

    override suspend fun saveFaces(sessionId: Long, itemId: Long, faces: List<FaceSample>) {
        val entities = withContext(Dispatchers.IO) {
            faces.mapIndexed { index, face ->
                FaceEntity(
                    sessionId = sessionId,
                    itemId = itemId,
                    embedding = EmbeddingCodec.encode(face.embedding),
                    thumbnailPath = thumbnails.save(sessionId, itemId, index, face.thumbnailJpeg),
                )
            }
        }
        dao.insertAll(entities)
    }

    override fun observeFaces(sessionId: Long): Flow<List<FaceRecord>> =
        dao.observeFaces(sessionId)
            .map { rows -> rows.map { FaceRecord(it.id, it.itemId, EmbeddingCodec.decode(it.embedding), it.thumbnailPath, it.personName) } }
            .flowOn(Dispatchers.Default)

    override suspend fun setPersonName(faceIds: Collection<Long>, name: String?) =
        dao.setPersonName(faceIds.toList(), name?.trim()?.takeIf { it.isNotEmpty() })

    override fun observePeople(sessionId: Long): Flow<Map<Long, List<String>>> =
        dao.observePeople(sessionId).map { rows ->
            rows.groupBy({ it.itemId }, { it.personName }).mapValues { (_, names) -> names.distinct() }
        }

    override suspend fun deleteOrphanedThumbnails() {
        val live = sessionDao.openSessions().map { it.session.id }.toSet() + dao.sessionIdsWithFaces()
        withContext(Dispatchers.IO) { thumbnails.retainOnly(live) }
    }
}
