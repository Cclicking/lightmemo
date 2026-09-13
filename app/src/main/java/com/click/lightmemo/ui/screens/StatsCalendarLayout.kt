package com.click.lightmemo.ui.screens

import java.time.LocalDate
import java.time.YearMonth

/** Monday-first month geometry shared by the collapsed and expanded calendar. */
internal class StatsCalendarLayout(anchor: LocalDate) {
    val month: YearMonth = YearMonth.from(anchor)
    private val first = month.atDay(1)
    val gridStart: LocalDate = first.minusDays(first.dayOfWeek.value.toLong() - 1)
    val anchorRow: Int = ((anchor.toEpochDay() - gridStart.toEpochDay()) / 7).toInt()
    val rowCount: Int = (first.dayOfWeek.value - 1 + month.lengthOfMonth() + 6) / 7

    fun rowOffset(row: Int, expansion: Float): Float = row - anchorRow * (1f - expansion)
}
