package com.hitit.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hitit.app.data.local.dao.BigPlayDao
import com.hitit.app.data.local.dao.CheckInDao
import com.hitit.app.data.local.dao.DailyLedgerDao
import com.hitit.app.data.local.dao.HitTaskDao
import com.hitit.app.data.local.dao.IdentityDao
import com.hitit.app.data.local.dao.LockInSessionDao
import com.hitit.app.data.local.dao.LockerNoteDao
import com.hitit.app.data.local.dao.MomentumTxnDao
import com.hitit.app.data.local.dao.RepDao
import com.hitit.app.data.local.dao.RepHitDao
import com.hitit.app.data.local.dao.SuddenDeathDao
import com.hitit.app.data.local.dao.TrophyDao
import com.hitit.app.data.local.dao.UserProfileDao
import com.hitit.app.data.local.entity.BigPlayEntity
import com.hitit.app.data.local.entity.CheckInEntity
import com.hitit.app.data.local.entity.CheckpointEntity
import com.hitit.app.data.local.entity.DailyLedgerEntity
import com.hitit.app.data.local.entity.HitTaskEntity
import com.hitit.app.data.local.entity.IdentityEntity
import com.hitit.app.data.local.entity.LockInSessionEntity
import com.hitit.app.data.local.entity.LockerNoteEntity
import com.hitit.app.data.local.entity.MomentumTxnEntity
import com.hitit.app.data.local.entity.RepEntity
import com.hitit.app.data.local.entity.RepHitEntity
import com.hitit.app.data.local.entity.SuddenDeathPenaltyEntity
import com.hitit.app.data.local.entity.TrophyEntity
import com.hitit.app.data.local.entity.UserProfileEntity

@Database(
    entities = [
        UserProfileEntity::class,
        RepEntity::class,
        RepHitEntity::class,
        MomentumTxnEntity::class,
        HitTaskEntity::class,
        LockInSessionEntity::class,
        CheckInEntity::class,
        BigPlayEntity::class,
        CheckpointEntity::class,
        LockerNoteEntity::class,
        TrophyEntity::class,
        SuddenDeathPenaltyEntity::class,
        IdentityEntity::class,
        DailyLedgerEntity::class,
    ],
    version = 10,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class HitItDatabase : RoomDatabase() {
    abstract fun repDao(): RepDao
    abstract fun repHitDao(): RepHitDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun momentumTxnDao(): MomentumTxnDao
    abstract fun hitTaskDao(): HitTaskDao
    abstract fun lockInSessionDao(): LockInSessionDao
    abstract fun checkInDao(): CheckInDao
    abstract fun bigPlayDao(): BigPlayDao
    abstract fun lockerNoteDao(): LockerNoteDao
    abstract fun trophyDao(): TrophyDao
    abstract fun suddenDeathDao(): SuddenDeathDao
    abstract fun identityDao(): IdentityDao
    abstract fun dailyLedgerDao(): DailyLedgerDao
}
