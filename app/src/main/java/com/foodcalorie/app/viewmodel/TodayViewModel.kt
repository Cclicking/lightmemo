package com.foodcalorie.app.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.foodcalorie.app.FoodApp
import com.foodcalorie.app.domain.DayNutritionSummary
import com.foodcalorie.app.domain.FoodLog
import com.foodcalorie.app.domain.Nutrition
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val entries: List<FoodLog> = emptyList(),
    val entriesByDate: Map<Long, List<FoodLog>> = emptyMap(),
    val target: Float = 1800f,
    val total: Nutrition = Nutrition(),
    val proteinTarget: Float = 120f,
    val carbsTarget: Float = 250f,
    val fatTarget: Float = 60f,
) {
    val progress: Float
        get() = if (target <= 0f) 0f else (total.caloriesKcal / target).toFloat().coerceIn(0f, 1.2f)
}

class TodayViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FoodApp).foodLogRepository
    val readError: StateFlow<String?> = repo.readError
    private val settingsRepo = (app as FoodApp).settingsRepository

    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date
    val deletedEntries = MutableStateFlow<List<FoodLog>>(emptyList())
    val operationError = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)

    init {
        viewModelScope.launch {
            var previous = LocalDate.now()
            currentDateFlow().collect { current ->
                if (_date.value == previous) _date.value = current
                previous = current
            }
        }
    }

    val uiState: StateFlow<TodayUiState> = combine(
        repo.logs,
        settingsRepo.settings,
        _date,
    ) { logs, settings, date ->
        val entries = logs.filter { it.dateEpochDay == date.toEpochDay() }
        TodayUiState(
            date = date,
            entries = entries,
            entriesByDate = logs.groupBy { it.dateEpochDay },
            target = settings.dailyCalorieTarget,
            proteinTarget = settings.effectiveProteinG.takeIf { it > 0 } ?: 120f,
            carbsTarget = settings.effectiveCarbsG.takeIf { it > 0 } ?: 250f,
            fatTarget = settings.effectiveFatG.takeIf { it > 0 } ?: 60f,
            total = entries.fold(Nutrition()) { acc, item -> acc + item.nutrition },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun previousDay() = _date.update { it.minusDays(1) }

    fun nextDay() = _date.update { it.plusDays(1) }

    fun selectDate(date: LocalDate) {
        _date.value = date
    }

    fun delete(id: Long) {
        perform {
            repo.deleteById(id)?.let { deleted -> deletedEntries.update { it + deleted } }
        }
    }

    fun undoDelete() {
        perform {
            val entry = deletedEntries.value.lastOrNull() ?: return@perform
            repo.restore(entry)
            deletedEntries.update { it.filterNot { item -> item.id == entry.id } }
        }
    }

    fun update(entry: FoodLog, onSaved: () -> Unit) {
        perform {
            repo.update(entry)
            onSaved()
        }
    }

    private fun perform(block: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        operationError.value = null
        viewModelScope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                operationError.value = e.message ?: "操作失败，请重试"
            } finally {
                busy.value = false
            }
        }
    }
}

class StatsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FoodApp).foodLogRepository
    val readError: StateFlow<String?> = repo.readError
    private val settingsRepo = (app as FoodApp).settingsRepository

    private val _rangeDays = MutableStateFlow(7)
    val rangeDays: StateFlow<Int> = _rangeDays

    data class StatsUiState(
        val rangeDays: Int = 7,
        val daily: List<DayNutritionSummary> = emptyList(),
        val averageKcal: Double = 0.0,
        val averageNutrition: Nutrition = Nutrition(),
        val maxKcal: Double = 0.0,
        val daysHitTarget: Int = 0,
        val daysOverTarget: Int = 0,
        val loggedDays: Int = 0,
        val target: Float = 1800f,
    ) {
        val hasData: Boolean get() = loggedDays > 0
    }

    val uiState: StateFlow<StatsUiState> = combine(
        repo.logs,
        _rangeDays,
        settingsRepo.settings,
        currentDateFlow(),
    ) { logs, days, settings, today ->
        val from = today.minusDays(days.toLong() - 1).toEpochDay()
        val to = today.toEpochDay()
        val filtered = logs.filter { it.dateEpochDay in from..to }
        val grouped = filtered.groupBy { it.dateEpochDay }
        val daily = (0 until days).map { offset ->
            val day = from + offset
            DayNutritionSummary(
                dateEpochDay = day,
                total = grouped[day].orEmpty().fold(Nutrition()) { acc, i -> acc + i.nutrition },
            )
        }
        val logged = daily.filter { grouped.containsKey(it.dateEpochDay) }
        val target = settings.dailyCalorieTarget.toDouble()
        StatsUiState(
            rangeDays = days,
            daily = daily,
            averageKcal = if (logged.isEmpty()) 0.0 else logged.map { it.total.caloriesKcal }.average(),
            averageNutrition = logged.fold(Nutrition()) { acc, day -> acc + day.total }
                .times(1.0 / logged.size.coerceAtLeast(1)),
            maxKcal = daily.maxOfOrNull { it.total.caloriesKcal } ?: 0.0,
            daysHitTarget = logged.count { it.total.caloriesKcal in 0.0..target },
            daysOverTarget = logged.count { it.total.caloriesKcal > target },
            loggedDays = logged.size,
            target = settings.dailyCalorieTarget,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setRange(days: Int) {
        _rangeDays.value = if (days == 30) 30 else 7
    }
}

private fun currentDateFlow() = flow {
    while (true) {
        emit(LocalDate.now())
        delay(30_000)
    }
}.distinctUntilChanged()
