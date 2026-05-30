package com.hitit.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hitit.app.data.local.dao.HitTaskDao
import com.hitit.app.data.local.dao.LockInSessionDao
import com.hitit.app.data.local.dao.MomentumTxnDao
import com.hitit.app.data.local.dao.RepDao
import com.hitit.app.data.local.dao.RepHitDao
import com.hitit.app.data.local.dao.UserProfileDao
import com.hitit.app.data.local.entity.HitTaskEntity
import com.hitit.app.data.local.entity.LockInSessionEntity
import com.hitit.app.data.local.entity.MomentumTxnEntity
import com.hitit.app.data.local.entity.RepEntity
import com.hitit.app.data.local.entity.RepHitEntity
import com.hitit.app.data.local.entity.UserProfileEntity

@Database(
    entities = [
        UserProfileEntity::class,
        RepEntity::class,
        RepHitEntity::class,
        MomentumTxnEntity::class,
        HitTaskEntity::class,
        LockInSessionEntity::class,
    ],
    version = 3,
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
}
