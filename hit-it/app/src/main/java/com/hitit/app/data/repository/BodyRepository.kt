package com.hitit.app.data.repository

import android.content.Context
import android.net.Uri
import com.hitit.app.data.local.dao.FoodEntryDao
import com.hitit.app.data.local.entity.FoodEntryEntity
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/** BodyFlow's data: food/fuel logging with optional photos (app-private copies, fully offline). */
@Singleton
class BodyRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val foodDao: FoodEntryDao,
) {
    fun observeToday(today: LocalDate = LocalDate.now()): Flow<List<FoodEntryEntity>> =
        foodDao.observeForDate(today)

    fun observeFoodDaysLast7(today: LocalDate = LocalDate.now()): Flow<Int> =
        foodDao.observeDistinctDaysSince(today.minusDays(6))

    fun observeHasAnyLogs(): Flow<Int> = foodDao.observeCount()

    suspend fun log(description: String, kcal: Int, date: LocalDate = LocalDate.now(), photoPath: String? = null) {
        foodDao.insert(
            FoodEntryEntity(date = date, description = description.trim(), kcal = kcal, photoPath = photoPath),
        )
    }

    /**
     * Copy a picked photo into app-private storage and return its path (null on any failure).
     * Owning a private copy means no storage permissions, no lost grants after reboot, and the
     * photo never depends on the gallery app — the diary stays self-contained and offline.
     */
    suspend fun importPhoto(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val dir = File(context.filesDir, PHOTO_DIR).apply { mkdirs() }
            val file = File(dir, "food_${System.currentTimeMillis()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@runCatching null
            file.absolutePath
        }.getOrNull()
    }

    suspend fun delete(id: Long) {
        // Remove the photo file alongside the row so the private dir never leaks orphans.
        runCatching { foodDao.getById(id)?.photoPath?.let { File(it).delete() } }
        foodDao.delete(id)
    }

    suspend fun clearAll() {
        foodDao.deleteAll()
        runCatching { File(context.filesDir, PHOTO_DIR).deleteRecursively() }
    }

    private companion object {
        const val PHOTO_DIR = "food_photos"
    }
}
