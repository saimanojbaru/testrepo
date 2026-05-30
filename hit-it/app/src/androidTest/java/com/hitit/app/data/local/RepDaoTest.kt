package com.hitit.app.data.local

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hitit.app.data.local.dao.RepDao
import com.hitit.app.data.local.dao.RepHitDao
import com.hitit.app.data.local.entity.RepEntity
import com.hitit.app.data.local.entity.RepHitEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

@RunWith(AndroidJUnit4::class)
class RepDaoTest {

    private lateinit var db: HitItDatabase
    private lateinit var repDao: RepDao
    private lateinit var hitDao: RepHitDao

    private val today = LocalDate.of(2026, 5, 29)

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        db = Room.inMemoryDatabaseBuilder(context, HitItDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repDao = db.repDao()
        hitDao = db.repHitDao()
    }

    @After
    fun teardown() {
        db.close()
    }

    @Test
    fun insertAndObserveActiveExcludesArchived() = runTest {
        repDao.insert(RepEntity(name = "Run", createdDate = today))
        val archivedId = repDao.insert(RepEntity(name = "Old", createdDate = today))
        repDao.setArchived(archivedId, true)

        val active = repDao.observeActive().first()
        assertEquals(1, active.size)
        assertEquals("Run", active.first().name)

        val all = repDao.observeAll().first()
        assertEquals(2, all.size)
    }

    @Test
    fun hitUpsertReplacesOnUniqueRepIdDate() = runTest {
        val repId = repDao.insert(RepEntity(name = "Water", targetCount = 3, createdDate = today))

        hitDao.upsert(RepHitEntity(repId = repId, date = today, hitCount = 1))
        hitDao.upsert(RepHitEntity(repId = repId, date = today, hitCount = 2))

        val onDate = hitDao.getForRepOnDate(repId, today)
        assertEquals(2, onDate?.hitCount)
        // Only one row for that (repId, date) thanks to the unique index + REPLACE.
        assertEquals(1, hitDao.getForRep(repId).size)
    }

    @Test
    fun deletingRepCascadesHits() = runTest {
        val repId = repDao.insert(RepEntity(name = "Read", createdDate = today))
        hitDao.upsert(RepHitEntity(repId = repId, date = today, hitCount = 1))
        assertEquals(1, hitDao.getForDate(today).size)

        repDao.delete(repDao.getById(repId)!!)
        assertEquals(0, hitDao.getForDate(today).size)
    }

    @Test
    fun getReminderEnabledFiltersArchivedAndDisabled() = runTest {
        repDao.insert(RepEntity(name = "A", reminderEnabled = true, createdDate = today))
        repDao.insert(RepEntity(name = "B", reminderEnabled = false, createdDate = today))
        val archived = repDao.insert(RepEntity(name = "C", reminderEnabled = true, createdDate = today))
        repDao.setArchived(archived, true)

        val enabled = repDao.getReminderEnabled()
        assertEquals(1, enabled.size)
        assertEquals("A", enabled.first().name)
    }

    @Test
    fun setRestModePersists() = runTest {
        val repId = repDao.insert(RepEntity(name = "Gym", createdDate = today))
        repDao.setRestMode(repId, today, today.plusDays(3))
        val rep = repDao.getById(repId)
        assertEquals(today, rep?.restModeStart)
        assertEquals(today.plusDays(3), rep?.restModeEnd)

        repDao.setRestMode(repId, null, null)
        assertNull(repDao.getById(repId)?.restModeStart)
    }
}
