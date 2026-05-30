package com.hitit.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hitit.app.data.local.dao.BigPlayDao
import com.hitit.app.data.local.entity.BigPlayEntity
import com.hitit.app.data.local.entity.CheckpointEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BigPlayDaoTest {

    private lateinit var db: HitItDatabase
    private lateinit var dao: BigPlayDao

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, HitItDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        dao = db.bigPlayDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun relationLoadsCheckpointsForPlay() = runTest {
        val planId = dao.insert(BigPlayEntity(title = "Read 12 books"))
        dao.insertCheckpoint(CheckpointEntity(bigPlayId = planId, title = "Book 1"))
        dao.insertCheckpoint(CheckpointEntity(bigPlayId = planId, title = "Book 2"))

        val withCheckpoints = dao.observeAllWithCheckpoints().first()
        assertEquals(1, withCheckpoints.size)
        assertEquals(2, withCheckpoints.first().checkpoints.size)
    }

    @Test
    fun toggleCheckpointPersists() = runTest {
        val planId = dao.insert(BigPlayEntity(title = "Ship app"))
        val cpId = dao.insertCheckpoint(CheckpointEntity(bigPlayId = planId, title = "Beta"))

        val cp = dao.getCheckpoint(cpId)!!
        dao.updateCheckpoint(cp.copy(isDone = true))

        assertTrue(dao.getCheckpoint(cpId)!!.isDone)
    }

    @Test
    fun deletingPlayCascadesCheckpoints() = runTest {
        val planId = dao.insert(BigPlayEntity(title = "Temp"))
        dao.insertCheckpoint(CheckpointEntity(bigPlayId = planId, title = "M1"))
        dao.insertCheckpoint(CheckpointEntity(bigPlayId = planId, title = "M2"))

        dao.delete(dao.getById(planId)!!)

        val remaining = dao.observeAllWithCheckpoints().first()
        assertTrue(remaining.isEmpty())
    }
}
