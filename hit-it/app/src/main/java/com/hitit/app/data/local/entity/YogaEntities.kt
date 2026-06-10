package com.hitit.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDate

/** A user-captured reference pose: 33 BlazePose landmarks encoded via domain PoseCodec. */
@Entity(tableName = "yoga_poses")
data class YogaPoseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val landmarksCsv: String,
    val createdAt: Long = System.currentTimeMillis(),
)

/** One practice session against a reference pose: best form score + longest clean hold. */
@Entity(tableName = "yoga_sessions")
data class YogaSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val poseId: Long,
    val date: LocalDate,
    val bestScore: Int,
    val holdSeconds: Int,
    val createdAt: Long = System.currentTimeMillis(),
)
