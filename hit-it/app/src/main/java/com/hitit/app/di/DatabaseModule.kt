package com.hitit.app.di

import android.content.Context
import androidx.room.Room
import com.hitit.app.data.local.HitItDatabase
import com.hitit.app.data.local.dao.MomentumTxnDao
import com.hitit.app.data.local.dao.RepDao
import com.hitit.app.data.local.dao.RepHitDao
import com.hitit.app.data.local.dao.UserProfileDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): HitItDatabase =
        Room.databaseBuilder(context, HitItDatabase::class.java, "hitit.db")
            .fallbackToDestructiveMigration(dropAllTables = true)
            .build()

    @Provides
    fun provideRepDao(db: HitItDatabase): RepDao = db.repDao()

    @Provides
    fun provideRepHitDao(db: HitItDatabase): RepHitDao = db.repHitDao()

    @Provides
    fun provideUserProfileDao(db: HitItDatabase): UserProfileDao = db.userProfileDao()

    @Provides
    fun provideMomentumTxnDao(db: HitItDatabase): MomentumTxnDao = db.momentumTxnDao()
}
