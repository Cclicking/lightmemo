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

data class TodayUiState(
    val date: LocalDate = LocalDate.now(),
    val entries: List<FoodLog> = emptyList(),
    val target: Float = 1800f,
    val total: Nutrition = Nutrition(),
) {
    val progress: Float
        get() = if (target <= 0f) 0f else (total.caloriesKcal / target).toFloat().coerceIn(0f, 1.2f)
}

class TodayViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FoodApp).foodLogRepository
    private val settingsRepo = (app as FoodApp).settingsRepository

    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date

    val uiState: StateFlow<TodayUiState> = combine(
        combine(repo.logs, _date) { logs, date -> logs.filter { it.dateEpochDay == date.toEpochDay() } },
        settingsRepo.settings,
        _date,
    ) { entries, settings, date ->
        TodayUiState(
            date = date,
            entries = entries,
            target = settings.dailyCalorieTarget,
            total = entries.fold(Nutrition()) { acc, item -> acc + item.nutrition },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    fun previousDay() = _date.update { it.minusDays(1) }

    fun nextDay() = _date.update { it.plusDays(1) }

    fun selectDate(date: LocalDate) {
        _date.value = date
    }

    fun delete(id: Long) {
        viewModelScope.launch { repo.deleteById(id) }
    }
}

class StatsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FoodApp).foodLogRepository
    private val settingsRepo = (app as FoodApp).settingsRepository

    private val _rangeDays = MutableStateFlow(7)
    val rangeDays: StateFlow<Int> = _rangeDays

    data class StatsUiState(
        val rangeDays: Int = 7,
        val daily: List<DayNutritionSummary> = emptyList(),
        val averageKcal: Double = 0.0,
        val maxKcal: Double = 0.0,
        val daysHitTarget: Int = 0,
        val target: Float = 1800f,
    ) {
        val hasData: Boolean get() = daily.isNotEmpty()
    }

    val uiState: StateFlow<StatsUiState> = combine(
        repo.logs,
        _rangeDays,
        settingsRepo.settings,
    ) { logs, days, settings ->
        val today = LocalDate.now()
        val from = today.minusDays(days.toLong() - 1).toEpochDay()
        val to = today.toEpochDay()
        val filtered = logs.filter { it.dateEpochDay in from..to }
        val grouped = filtered.groupBy { it.dateEpochDay }
        val daily = grouped.map { (day, items) ->
            DayNutritionSummary(
                dateEpochDay = day,
                total = items.fold(Nutrition()) { acc, i -> acc + i.nutrition },
            )
        }.sortedBy { it.dateEpochDay }
        val target = settings.dailyCalorieTarget.toDouble()
        StatsUiState(
            rangeDays = days,
            daily = daily,
            averageKcal = if (daily.isEmpty()) 0.0 else daily.map { it.total.caloriesKcal }.average(),
            maxKcal = daily.maxOfOrNull { it.total.caloriesKcal } ?: 0.0,
            daysHitTarget = daily.count { it.total.caloriesKcal in 0.0..target },
            target = settings.dailyCalorieTarget,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setRange(days: Int) {
        _rangeDays.value = days
    }
}
