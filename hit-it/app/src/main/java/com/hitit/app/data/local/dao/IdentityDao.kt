package com.hitit.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.hitit.app.data.local.entity.IdentityEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface IdentityDao {

    @Query("SELECT * FROM identities")
    fun observeAll(): Flow<List<IdentityEntity>>

    @Query("SELECT id FROM identities")
    suspend fun unlockedIds(): List<String>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(identity: IdentityEntity)
}
