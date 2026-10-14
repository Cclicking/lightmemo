package com.click.lightmemo.domain

data class CalorieBalanceDay(
    val dateEpochDay: Long,
    val intakeKcal: Double?,
    val expenditureKcal: Double?,
) {
    val balanceKcal: Double? get() = intakeKcal?.let { intake -> expenditureKcal?.let { it - intake } }
}

fun calorieBalanceDays(
    days: List<DayNutritionSummary>,
    loggedEpochDays: Set<Long>,
    expenditure: Map<Long, Double>,
): List<CalorieBalanceDay> = days.map { day ->
    CalorieBalanceDay(day.dateEpochDay,
        day.total.caloriesKcal.takeIf { day.dateEpochDay in loggedEpochDays },
        expenditure[day.dateEpochDay])
}
