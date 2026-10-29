package com.sortora.app.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import androidx.room.Room
import com.sortora.app.data.ai.OnDeviceMediaAnalyzer
import com.sortora.app.data.file.SafFileOrganizer
import com.sortora.app.data.file.SafTextFileWriter
import com.sortora.app.data.local.LogDao
import com.sortora.app.data.local.SessionDao
import com.sortora.app.data.local.SortoraDatabase
import com.sortora.app.data.media.SafMediaSourceRepository
import com.sortora.app.data.repository.DataStoreSettingsRepository
import com.sortora.app.data.repository.RoomHistoryRepository
import com.sortora.app.data.repository.RoomSessionRepository
import com.sortora.app.data.work.WorkManagerBackgroundJobs
import com.sortora.app.domain.repository.BackgroundJobs
import com.sortora.app.domain.repository.FileOrganizer
import com.sortora.app.domain.repository.HistoryRepository
import com.sortora.app.domain.repository.MediaAnalyzer
import com.sortora.app.domain.repository.MediaSourceRepository
import com.sortora.app.domain.repository.SessionRepository
import com.sortora.app.domain.repository.SettingsRepository
import com.sortora.app.domain.repository.TextFileWriter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import javax.inject.Singleton

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): SortoraDatabase =
        Room.databaseBuilder(context, SortoraDatabase::class.java, "sortora.db")
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .build()

    @Provides
    fun provideSessionDao(db: SortoraDatabase): SessionDao = db.sessionDao()

    @Provides
    fun provideLogDao(db: SortoraDatabase): LogDao = db.logDao()

    @Provides
    @Singleton
    fun provideDataStore(@ApplicationContext context: Context): DataStore<Preferences> = context.settingsDataStore

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }
}

/** Wires domain interfaces to their on-device implementations. */
@Module
@InstallIn(SingletonComponent::class)
abstract class BindingsModule {
    @Binds abstract fun settings(impl: DataStoreSettingsRepository): SettingsRepository
    @Binds abstract fun sessions(impl: RoomSessionRepository): SessionRepository
    @Binds abstract fun history(impl: RoomHistoryRepository): HistoryRepository
    @Binds abstract fun mediaSource(impl: SafMediaSourceRepository): MediaSourceRepository
    @Binds abstract fun analyzer(impl: OnDeviceMediaAnalyzer): MediaAnalyzer
    @Binds abstract fun organizer(impl: SafFileOrganizer): FileOrganizer
    @Binds abstract fun jobs(impl: WorkManagerBackgroundJobs): BackgroundJobs
    @Binds abstract fun textWriter(impl: SafTextFileWriter): TextFileWriter
}
