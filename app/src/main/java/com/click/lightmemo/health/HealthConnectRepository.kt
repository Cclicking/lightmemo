package com.click.lightmemo.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.HealthConnectFeatures
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.TotalCaloriesBurnedRecord
import androidx.health.connect.client.request.AggregateGroupByPeriodRequest
import androidx.health.connect.client.time.TimeRangeFilter
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.Period

enum class HealthConnectStatus { LOADING, AVAILABLE, PERMISSION_REQUIRED, UPDATE_REQUIRED, UNAVAILABLE, ERROR }

data class CalorieExpenditureState(
    val status: HealthConnectStatus = HealthConnectStatus.LOADING,
    val dailyKcal: Map<Long, Double> = emptyMap(),
    val permissions: Set<String> = emptySet(),
    val historyLimited: Boolean = false,
    val periodStart: LocalDate? = null,
    val periodEnd: LocalDate? = null,
)

class HealthConnectRepository(private val context: Context) {
    companion object {
        const val PROVIDER_PACKAGE = "com.google.android.apps.healthdata"
        val READ_CALORIES = HealthPermission.getReadPermission(TotalCaloriesBurnedRecord::class)
    }

    suspend fun readDailyCalories(start: LocalDate, end: LocalDate): CalorieExpenditureState {
        val sdkStatus = HealthConnectClient.getSdkStatus(context)
        if (sdkStatus != HealthConnectClient.SDK_AVAILABLE) {
            return CalorieExpenditureState(status = if (sdkStatus == HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED)
                HealthConnectStatus.UPDATE_REQUIRED else HealthConnectStatus.UNAVAILABLE)
        }
        val client = HealthConnectClient.getOrCreate(context)
        val historySupported = client.features.getFeatureStatus(HealthConnectFeatures.FEATURE_READ_HEALTH_DATA_HISTORY) ==
            HealthConnectFeatures.FEATURE_STATUS_AVAILABLE
        val permissions = buildSet {
            add(READ_CALORIES)
            if (historySupported) add(HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY)
        }
        val granted = client.permissionController.getGrantedPermissions()
        if (READ_CALORIES !in granted) {
            return CalorieExpenditureState(HealthConnectStatus.PERMISSION_REQUIRED, permissions = permissions)
        }
        val hasHistory = HealthPermission.PERMISSION_READ_HEALTH_DATA_HISTORY in granted
        val today = LocalDate.now()
        val readableEnd = minOf(end, today)
        suspend fun aggregate(from: LocalDate): Map<Long, Double> {
            if (readableEnd < from) return emptyMap()
            return client.aggregateGroupByPeriod(
                AggregateGroupByPeriodRequest(
                    metrics = setOf(TotalCaloriesBurnedRecord.ENERGY_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(from.atStartOfDay(), readableEnd.plusDays(1).atStartOfDay()),
                    timeRangeSlicer = Period.ofDays(1),
                )
            ).mapNotNull { bucket ->
                bucket.result[TotalCaloriesBurnedRecord.ENERGY_TOTAL]?.inKilocalories
                    ?.takeIf { it.isFinite() && it >= 0.0 }
                    ?.let { bucket.startTime.toLocalDate().toEpochDay() to it }
            }.toMap()
        }
        val daily = try {
            aggregate(start)
        } catch (e: SecurityException) {
            // The default window starts 30 days before FIRST grant, not 30 days before today.
            // Try the selected period first so previously authorized history remains visible.
            // On a restricted range, retain recent whole days instead of failing the entire month.
            if (hasHistory || READ_CALORIES !in client.permissionController.getGrantedPermissions()) throw e
            val safeStart = LocalDateTime.now().minusDays(30).toLocalDate().plusDays(1)
            if (start >= safeStart) throw e
            aggregate(safeStart)
        }
        return CalorieExpenditureState(HealthConnectStatus.AVAILABLE, daily, permissions,
            historyLimited = !hasHistory, periodStart = start, periodEnd = end)
    }
}
