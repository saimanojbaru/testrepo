package com.hitit.app.data.local.entity

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.Instant
import java.time.LocalDate

/** A Big Play (long-term goal). */
@Entity(tableName = "big_plays")
data class BigPlayEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    /** One of the categories in [com.hitit.app.ui.screens.bigplays.GoalCatalog]. */
    val category: String = "Personal",
    /** QUARTER / YEAR / TWO_YEAR / THREE_YEAR. */
    val horizon: String = "YEAR",
    val targetValue: Double? = null,
    val currentValue: Double = 0.0,
    val unit: String = "",
    val deadline: LocalDate? = null,
    val isCompleted: Boolean = false,
    val completedAt: Instant? = null,
    val sortOrder: Int = 0,
    val createdAt: Instant = Instant.now(),
)

/** A milestone within a Big Play. */
@Entity(
    tableName = "checkpoints",
    foreignKeys = [
        ForeignKey(
            entity = BigPlayEntity::class,
            parentColumns = ["id"],
            childColumns = ["bigPlayId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["bigPlayId"])],
)
data class CheckpointEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val bigPlayId: Long,
    val title: String,
    val isDone: Boolean = false,
    val doneAt: Instant? = null,
    val sortOrder: Int = 0,
)

/** A Big Play together with its checkpoints (Room @Relation). */
data class BigPlayWithCheckpoints(
    @Embedded val bigPlay: BigPlayEntity,
    @Relation(parentColumn = "id", entityColumn = "bigPlayId")
    val checkpoints: List<CheckpointEntity>,
)
