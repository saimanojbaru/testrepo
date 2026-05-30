package com.hitit.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/** A persistently unlocked Trophy. The [id] matches a TrophyDef id in the domain catalog. */
@Entity(tableName = "trophies")
data class TrophyEntity(
    @PrimaryKey val id: String,
    val unlockedAt: Instant = Instant.now(),
)
