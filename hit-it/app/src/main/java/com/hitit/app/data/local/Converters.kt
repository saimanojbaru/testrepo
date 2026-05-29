package com.hitit.app.data.local

import androidx.room.TypeConverter
import java.time.Instant
import java.time.LocalDate

/** Room type converters: LocalDate <-> ISO String, Instant <-> epoch millis. */
class Converters {

    @TypeConverter
    fun localDateToString(value: LocalDate?): String? = value?.toString()

    @TypeConverter
    fun stringToLocalDate(value: String?): LocalDate? = value?.let(LocalDate::parse)

    @TypeConverter
    fun instantToLong(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun longToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)
}
