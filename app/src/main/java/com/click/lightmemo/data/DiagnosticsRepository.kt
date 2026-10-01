package com.click.lightmemo.data

import androidx.room.withTransaction
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class DiagnosticsRepository(private val database: FoodDatabase) {
    private val dao = database.localDiagnosticsDao()
    private val json = Json { prettyPrint = true }

    fun recentDays(days: Int = 30, today: LocalDate = LocalDate.now()) =
        dao.observeRange(startDay(days, today), today.toEpochDay())

    suspend fun increment(delta: LocalDiagnosticsEntity) {
        delta.validateDelta()
        database.withTransaction {
            val previous = dao.find(delta.dateEpochDay) ?: LocalDiagnosticsEntity(delta.dateEpochDay)
            dao.upsert(previous + delta)
        }
    }

    /** Export only typed numeric aggregates; no settings or food tables are read. */
    suspend fun exportJson(days: Int = 30, today: LocalDate = LocalDate.now()): String =
        json.encodeToString(recentDays(days, today).first())

    suspend fun clear() = dao.clear()

    /** Optional metrics must never turn a saved food or a successful recognition into a failure. */
    suspend fun recordSafely(delta: LocalDiagnosticsEntity) {
        kotlinx.coroutines.withContext(kotlinx.coroutines.NonCancellable + kotlinx.coroutines.Dispatchers.IO) {
            try { increment(delta) } catch (_: Exception) { /* Best effort numeric telemetry only. */ }
        }
    }

    private fun startDay(days: Int, today: LocalDate): Long {
        require(days in 1..3650)
        return today.minusDays(days.toLong() - 1).toEpochDay()
    }
}
