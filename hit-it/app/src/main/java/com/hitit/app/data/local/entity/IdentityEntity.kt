package com.hitit.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant

/** A persistently unlocked Identity Card. The [id] matches an IdentityDef id in the domain catalog. */
@Entity(tableName = "identities")
data class IdentityEntity(
    @PrimaryKey val id: String,
    val unlockedAt: Instant = Instant.now(),
)
