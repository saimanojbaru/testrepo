package com.hitit.app.data.repository

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.SleepSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import javax.inject.Inject
import javax.inject.Singleton

/** Live body signals read from Health Connect; null fields mean "no data recorded". */
data class BodySignals(
    val stepsToday: Long? = null,
    val sleepMinutesLastNight: Long? = null,
    val latestBpm: Long? = null,
)

/**
 * Read-only Health Connect bridge: steps today, last night's sleep session, latest heart-rate
 * sample. Everything is defensive — on Android 8 (no HC), provider missing, permission denied, or
 * any read failure the caller gets nulls and the UI degrades to its connect/unavailable states.
 * Signals are read live (never stored), keeping the offline-first promise intact.
 */
@Singleton
class HealthRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(StepsRecord::class),
        HealthPermission.getReadPermission(SleepSessionRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
    )

    /** One of [HealthConnectClient.SDK_AVAILABLE] / SDK_UNAVAILABLE / SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED. */
    fun sdkStatus(): Int = HealthConnectClient.getSdkStatus(context)

    suspend fun hasAllPermissions(): Boolean {
        if (sdkStatus() != HealthConnectClient.SDK_AVAILABLE) return false
        return runCatching {
            HealthConnectClient.getOrCreate(context)
                .permissionController.getGrantedPermissions()
                .containsAll(permissions)
        }.getOrDefault(false)
    }

    suspend fun readSignals(today: LocalDate = LocalDate.now()): BodySignals {
        if (sdkStatus() != HealthConnectClient.SDK_AVAILABLE) return BodySignals()
        val client = runCatching { HealthConnectClient.getOrCreate(context) }.getOrNull() ?: return BodySignals()
        val zone = ZoneId.systemDefault()
        val now = Instant.now()

        // Steps today — aggregate dedupes multiple sources (phone + watch).
        val steps = runCatching {
            client.aggregate(
                AggregateRequest(
                    metrics = setOf(StepsRecord.COUNT_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(today.atStartOfDay(zone).toInstant(), now),
                ),
            )[StepsRecord.COUNT_TOTAL]
        }.getOrNull()

        // Last night's sleep: latest session ending in the window from yesterday evening to now.
        val sleepMinutes = runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = SleepSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(
                        today.minusDays(1).atTime(LocalTime.of(18, 0)).atZone(zone).toInstant(),
                        now,
                    ),
                ),
            ).records.maxByOrNull { it.endTime }
                ?.let { Duration.between(it.startTime, it.endTime).toMinutes() }
        }.getOrNull()

        // Latest heart-rate sample in the last 24h.
        val bpm = runCatching {
            client.readRecords(
                ReadRecordsRequest(
                    recordType = HeartRateRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(now.minus(Duration.ofHours(24)), now),
                    ascendingOrder = false,
                    pageSize = 1,
                ),
            ).records.firstOrNull()?.samples?.maxByOrNull { it.time }?.beatsPerMinute
        }.getOrNull()

        return BodySignals(stepsToday = steps, sleepMinutesLastNight = sleepMinutes, latestBpm = bpm)
    }
}
