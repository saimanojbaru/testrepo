package com.hitit.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.hitit.app.data.local.dao.MomentumTxnDao
import com.hitit.app.data.local.dao.RepDao
import com.hitit.app.data.local.dao.RepHitDao
import com.hitit.app.data.local.dao.UserProfileDao
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
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class HitItDatabase : RoomDatabase() {
    abstract fun repDao(): RepDao
    abstract fun repHitDao(): RepHitDao
    abstract fun userProfileDao(): UserProfileDao
    abstract fun momentumTxnDao(): MomentumTxnDao
}
