package com.sortora.app.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

/**
 * Local database in the app's private storage. Excluded from cloud backup and device transfer
 * (see res/xml/data_extraction_rules.xml).
 *
 * v2 added faces, duplicate groups, batch-rename patterns and undo state. v1 was only a test
 * build, so it is recreated (see AppModule). From now on, bumping [version] needs a real
 * Migration: the operation log is the user's record of original file names.
 */
@Database(
    entities = [SessionEntity::class, SessionItemEntity::class, OperationLogEntity::class, FaceEntity::class],
    version = 2,
    exportSchema = true,
)
abstract class SortoraDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun logDao(): LogDao
    abstract fun faceDao(): FaceDao
}
