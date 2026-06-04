package com.hitit.app.di

import android.content.Context
import androidx.room.Room
import com.hitit.app.data.local.HitItDatabase
import com.hitit.app.data.local.dao.BigPlayDao
import com.hitit.app.data.local.dao.CheckInDao
import com.hitit.app.data.local.dao.HitTaskDao
import com.hitit.app.data.local.dao.LockInSessionDao
import com.hitit.app.data.local.dao.LockerNoteDao
import com.hitit.app.data.local.dao.MomentumTxnDao
import com.hitit.app.data.local.dao.RepDao
import com.hitit.app.data.local.dao.RepHitDao
import com.hitit.app.data.local.dao.SuddenDeathDao
import com.hitit.app.data.local.dao.TrophyDao
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

    @Provides
    fun provideHitTaskDao(db: HitItDatabase): HitTaskDao = db.hitTaskDao()

    @Provides
    fun provideLockInSessionDao(db: HitItDatabase): LockInSessionDao = db.lockInSessionDao()

    @Provides
    fun provideCheckInDao(db: HitItDatabase): CheckInDao = db.checkInDao()

    @Provides
    fun provideBigPlayDao(db: HitItDatabase): BigPlayDao = db.bigPlayDao()

    @Provides
    fun provideLockerNoteDao(db: HitItDatabase): LockerNoteDao = db.lockerNoteDao()

    @Provides
    fun provideTrophyDao(db: HitItDatabase): TrophyDao = db.trophyDao()

    @Provides
    fun provideSuddenDeathDao(db: HitItDatabase): SuddenDeathDao = db.suddenDeathDao()

    @Provides
    fun provideIdentityDao(db: HitItDatabase): com.hitit.app.data.local.dao.IdentityDao = db.identityDao()

    @Provides
    fun provideDailyLedgerDao(db: HitItDatabase): com.hitit.app.data.local.dao.DailyLedgerDao =
        db.dailyLedgerDao()
}
