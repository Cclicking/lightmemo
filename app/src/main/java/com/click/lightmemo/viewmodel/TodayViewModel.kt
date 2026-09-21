package com.click.lightmemo.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.click.lightmemo.FoodApp
import com.click.lightmemo.domain.DayNutritionSummary
import com.click.lightmemo.domain.FoodLog
import com.click.lightmemo.domain.MealType
import com.click.lightmemo.domain.Nutrition
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlin.math.abs
import kotlin.math.roundToInt

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

@OptIn(ExperimentalCoroutinesApi::class)
class TodayViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FoodApp).foodLogRepository
    val readError: StateFlow<String?> = repo.readError
    private val settingsRepo = (app as FoodApp).settingsRepository

    private val _date = MutableStateFlow(LocalDate.now())
    val date: StateFlow<LocalDate> = _date
    val deletedEntries = MutableStateFlow<List<FoodLog>>(emptyList())
    val operationError = MutableStateFlow<String?>(null)
    val busy = MutableStateFlow(false)
    private val eventChannel = Channel<String>(Channel.BUFFERED)
    val events = eventChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            var previous = LocalDate.now()
            currentDateFlow().collect { current ->
                if (_date.value == previous) _date.value = current
                previous = current
            }
        }
    }

    /** Window covering day-swipe neighbors and adjacent calendar pages (±1 month). */
    private val logWindow = combine(_date, currentDateFlow()) { date, today ->
        val monthStart = YearMonth.from(date).minusMonths(1).atDay(1)
        val from = monthStart.toEpochDay()
        val to = minOf(
            today.toEpochDay(),
            YearMonth.from(date).plusMonths(1).atEndOfMonth().toEpochDay(),
        )
        from to to
    }.distinctUntilChanged()

    val uiState: StateFlow<TodayUiState> = combine(
        logWindow.flatMapLatest { (from, to) -> repo.logsInRange(from, to) },
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

    fun deleteSelected(ids: Collection<Long>) {
        val selectedIds = ids.toSet()
        if (selectedIds.isEmpty()) return
        perform {
            selectedIds.forEach { id ->
                repo.deleteById(id)?.let { deleted -> deletedEntries.update { it + deleted } }
            }
        }
    }

    fun importSelected(ids: Collection<Long>) {
        val selectedIds = ids.distinct()
        if (selectedIds.isEmpty()) return
        perform {
            val now = LocalTime.now()
            val minuteOfDay = now.hour * 60 + now.minute
            val mealType = mealTypeForMinuteOfDay(minuteOfDay)
            val today = LocalDate.now().toEpochDay()
            val createdAt = System.currentTimeMillis()
            val copies = selectedIds.mapNotNull { repo.getById(it) }.map { entry ->
                entry.copy(
                    id = 0L,
                    mealType = mealType,
                    dateEpochDay = today,
                    createdAtMillis = createdAt,
                    mealMinuteOfDay = minuteOfDay,
                )
            }
            if (copies.isNotEmpty()) {
                repo.insertAll(copies)
                eventChannel.trySend("已再记一次 ${copies.size} 项食物")
            }
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

@OptIn(ExperimentalCoroutinesApi::class)
class StatsViewModel(app: Application) : AndroidViewModel(app) {
    private val repo = (app as FoodApp).foodLogRepository
    val readError: StateFlow<String?> = repo.readError
    private val settingsRepo = (app as FoodApp).settingsRepository

    private val period = MutableStateFlow<Pair<LocalDate, LocalDate>?>(null)

    data class StatsUiState(
        val rangeDays: Int = 7,
        val elapsedDays: Int = 0,
        val daily: List<DayNutritionSummary> = emptyList(),
        val calendarDays: List<DayNutritionSummary> = emptyList(),
        val mealTiming: List<MealTimingDay> = emptyList(),
        val averageKcal: Double = 0.0,
        val averageNutrition: Nutrition = Nutrition(),
        val mealCalories: Map<MealType, Double> = emptyMap(),
        val maxKcal: Double = 0.0,
        val timedDays: Int = 0,
        val averageIntakesPerDay: Double = 0.0,
        val averageFirstMealMinute: Int? = null,
        val averageLastMealMinute: Int? = null,
        val averageEatingWindowMinutes: Int? = null,
        val regularityScore: Int? = null,
        val daysHitTarget: Int = 0,
        val daysOverTarget: Int = 0,
        val loggedDays: Int = 0,
        val target: Float = 1800f,
        val proteinTarget: Float = 120f,
        val carbsTarget: Float = 250f,
        val fatTarget: Float = 60f,
    ) {
        val hasData: Boolean get() = loggedDays > 0
    }

    private data class StatsWindow(
        val from: Long,
        val to: Long,
        val periodStart: LocalDate,
        val periodEnd: LocalDate,
        val today: LocalDate,
    )

    private val window = combine(period, currentDateFlow()) { selectedPeriod, today ->
        val start = selectedPeriod?.first ?: today.minusDays(today.dayOfWeek.value.toLong() - 1)
        val end = selectedPeriod?.second ?: start.plusDays(6)
        // Adjacent periods so calendar rings are populated while dragging.
        val previewFrom = start.minusMonths(1).minusWeeks(1)
        val previewTo = minOf(end.plusMonths(1).plusWeeks(1), today)
        StatsWindow(
            from = previewFrom.toEpochDay(),
            to = previewTo.toEpochDay(),
            periodStart = start,
            periodEnd = end,
            today = today,
        )
    }.distinctUntilChanged()

    val uiState: StateFlow<StatsUiState> = combine(
        window.flatMapLatest { w -> repo.logsInRange(w.from, w.to) },
        window,
        settingsRepo.settings,
    ) { logs, w, settings ->
        val start = w.periodStart
        val end = w.periodEnd
        val today = w.today
        val from = start.toEpochDay()
        val to = end.toEpochDay()
        val days = (to - from + 1).toInt()
        val filtered = logs.filter { it.dateEpochDay in from..minOf(to, today.toEpochDay()) }
        val grouped = filtered.groupBy { it.dateEpochDay }
        val mealCalories = MealType.entries.associateWith { mealType ->
            filtered.asSequence()
                .filter { it.mealType == mealType }
                .sumOf { it.nutrition.caloriesKcal }
        }
        val daily = (0 until days).map { offset ->
            val day = from + offset
            DayNutritionSummary(
                dateEpochDay = day,
                total = grouped[day].orEmpty().fold(Nutrition()) { acc, i -> acc + i.nutrition },
            )
        }
        val mealTiming = (0 until days).map { offset ->
            val dateEpochDay = from + offset
            val points = grouped[dateEpochDay].orEmpty()
                .mapNotNull { entry ->
                    entry.mealMinuteOfDay?.let { minute -> minute to entry.mealType }
                }
                // Recognition can save several dishes for one meal. Collapse those records
                // into one intake point so the regularity chart reflects meals, not dishes.
                .groupBy { it.first }
                .toSortedMap()
                .map { (minute, entriesAtSameTime) ->
                    IntakeTimePoint(minuteOfDay = minute, mealType = entriesAtSameTime.first().second)
                }
            MealTimingDay(dateEpochDay = dateEpochDay, points = points)
        }
        val daysWithTiming = mealTiming.filter { it.points.isNotEmpty() }
        val averageFirstMealMinute = daysWithTiming.mapNotNull { it.firstMinute }.averageOrNullInt()
        val averageLastMealMinute = daysWithTiming.mapNotNull { it.lastMinute }.averageOrNullInt()
        val averageEatingWindowMinutes = daysWithTiming.map { it.eatingWindowMinutes }.averageOrNullInt()
        val averageIntakesPerDay = daysWithTiming.map { it.points.size }.average().takeUnless { it.isNaN() } ?: 0.0
        val elapsedDays = (minOf(to, today.toEpochDay()) - from + 1).toInt().coerceIn(0, days)
        val calendarDays = logs.filter { it.dateEpochDay in w.from..w.to }
            .groupBy { it.dateEpochDay }
            .map { (epochDay, entries) ->
                DayNutritionSummary(epochDay, entries.fold(Nutrition()) { total, entry -> total + entry.nutrition })
            }
        val logged = daily.filter { grouped.containsKey(it.dateEpochDay) }
        val target = settings.dailyCalorieTarget.toDouble()
        StatsUiState(
            rangeDays = days,
            daily = daily,
            calendarDays = calendarDays,
            averageKcal = if (logged.isEmpty()) 0.0 else logged.map { it.total.caloriesKcal }.average(),
            averageNutrition = logged.fold(Nutrition()) { acc, day -> acc + day.total }
                .times(1.0 / logged.size.coerceAtLeast(1)),
            mealCalories = mealCalories,
            maxKcal = daily.maxOfOrNull { it.total.caloriesKcal } ?: 0.0,
            mealTiming = mealTiming,
            timedDays = daysWithTiming.size,
            averageIntakesPerDay = averageIntakesPerDay,
            averageFirstMealMinute = averageFirstMealMinute,
            averageLastMealMinute = averageLastMealMinute,
            averageEatingWindowMinutes = averageEatingWindowMinutes,
            regularityScore = calculateRegularityScore(daysWithTiming, elapsedDays),
            daysHitTarget = logged.count { it.total.caloriesKcal in 0.0..target },
            daysOverTarget = logged.count { it.total.caloriesKcal > target },
            loggedDays = logged.size,
            elapsedDays = elapsedDays,
            target = settings.dailyCalorieTarget,
            proteinTarget = settings.effectiveProteinG.takeIf { it > 0f } ?: 120f,
            carbsTarget = settings.effectiveCarbsG.takeIf { it > 0f } ?: 250f,
            fatTarget = settings.effectiveFatG.takeIf { it > 0f } ?: 60f,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StatsUiState())

    fun setPeriod(start: LocalDate, end: LocalDate) {
        require(!end.isBefore(start))
        period.value = start to end
    }
}

data class IntakeTimePoint(
    val minuteOfDay: Int,
    val mealType: MealType,
)

data class MealTimingDay(
    val dateEpochDay: Long,
    val points: List<IntakeTimePoint>,
) {
    val firstMinute: Int? get() = points.minOfOrNull { it.minuteOfDay }
    val lastMinute: Int? get() = points.maxOfOrNull { it.minuteOfDay }
    val eatingWindowMinutes: Int get() = (lastMinute ?: 0) - (firstMinute ?: 0)
}

private fun List<Int>.averageOrNullInt(): Int? = takeIf { isNotEmpty() }?.average()?.roundToInt()

/**
 * A descriptive score for timing consistency, not a health assessment. It combines time
 * variation, consistency of the number of daily intakes, and how many elapsed days have data.
 */
private fun calculateRegularityScore(days: List<MealTimingDay>, elapsedDays: Int): Int? {
    if (days.size < 2) return null
    val firstTimes = days.mapNotNull { it.firstMinute }
    val lastTimes = days.mapNotNull { it.lastMinute }
    val firstAverage = firstTimes.average()
    val lastAverage = lastTimes.average()
    val firstDeviation = firstTimes.map { abs(it - firstAverage) }.average()
    val lastDeviation = lastTimes.map { abs(it - lastAverage) }.average()
    val timingScore = (1.0 - ((firstDeviation + lastDeviation) / 2.0) / 180.0).coerceIn(0.0, 1.0)

    val intakeCounts = days.map { it.points.size.toDouble() }
    val countAverage = intakeCounts.average()
    val countDeviation = intakeCounts.map { abs(it - countAverage) }.average()
    val countScore = (1.0 - countDeviation / countAverage.coerceAtLeast(1.0)).coerceIn(0.0, 1.0)
    val coverageScore = (days.size.toDouble() / elapsedDays.coerceAtLeast(1)).coerceIn(0.0, 1.0)

    return ((timingScore * 0.55 + countScore * 0.25 + coverageScore * 0.20) * 100.0)
        .roundToInt()
        .coerceIn(0, 100)
}

private fun currentDateFlow() = flow {
    while (true) {
        emit(LocalDate.now())
        delay(30_000)
    }
}.distinctUntilChanged()
