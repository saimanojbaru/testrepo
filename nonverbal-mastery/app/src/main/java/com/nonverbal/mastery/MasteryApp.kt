package com.nonverbal.mastery

import android.app.Application
import android.content.Context
import com.nonverbal.mastery.data.db.AppDatabase
import com.nonverbal.mastery.data.repo.ContentRepository
import com.nonverbal.mastery.data.repo.ProgressRepository
import com.nonverbal.mastery.data.repo.SettingsRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Hand-wired dependencies. Small enough that a DI framework would be ceremony. */
class AppContainer(context: Context) {
    val database: AppDatabase by lazy { AppDatabase.create(context) }
    val content = ContentRepository(context)
    val progress: ProgressRepository by lazy { ProgressRepository(database) }
    val settings = SettingsRepository(context)

    /** Outlives any screen: used to finish saves when a screen closes mid-write. */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

class MasteryApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
