package com.sortora.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Local database in the app's private storage. Excluded from cloud backup and device transfer
 * (see res/xml/data_extraction_rules.xml).
 *
 * When bumping [version], add a Migration: the operation log is the user's record of original
 * file names and must survive upgrades.
 */
@Database(
    entities = [SessionEntity::class, SessionItemEntity::class, OperationLogEntity::class],
    version = 1,
    exportSchema = true,
)
abstract class SortoraDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun logDao(): LogDao
}
