package com.click.lightmemo.ui.screens

import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.*
import org.junit.Test

class StatsCalendarLayoutTest {
    @Test fun monthGridCoversEveryDayIncludingLeapDaysAndSixRowMonths() {
        for (year in 2024..2028) for (month in 1..12) {
            val value = YearMonth.of(year, month)
            val layout = StatsCalendarLayout(value.atDay(1))
            assertEquals(1, layout.gridStart.dayOfWeek.value)
            val dates = (0 until layout.rowCount * 7).map { layout.gridStart.plusDays(it.toLong()) }
            assertEquals(value.lengthOfMonth(), dates.count { YearMonth.from(it) == value })
            assertTrue(dates.contains(value.atEndOfMonth()))
            assertTrue(layout.rowCount in 4..6)
        }
        assertEquals(4, StatsCalendarLayout(LocalDate.of(2027, 2, 1)).rowCount)
        assertEquals(6, StatsCalendarLayout(LocalDate.of(2026, 8, 31)).rowCount)
    }

    @Test fun currentWeekMovesFromTopToItsActualMonthRow() {
        val layout = StatsCalendarLayout(LocalDate.of(2026, 9, 13))
        assertEquals(1, layout.anchorRow)
        assertEquals(0f, layout.rowOffset(layout.anchorRow, 0f), 0f)
        assertEquals(0.5f, layout.rowOffset(layout.anchorRow, 0.5f), 0f)
        assertEquals(1f, layout.rowOffset(layout.anchorRow, 1f), 0f)
        assertEquals(-1f, layout.rowOffset(0, 0f), 0f)
        assertEquals(0f, layout.rowOffset(0, 1f), 0f)
    }

    @Test fun firstWeekRemainsAnchoredAndCrossYearWeekStaysIntact() {
        val layout = StatsCalendarLayout(LocalDate.of(2027, 1, 1))
        assertEquals(LocalDate.of(2026, 12, 28), layout.gridStart)
        for (progress in listOf(0f, 0.5f, 1f)) assertEquals(0f, layout.rowOffset(0, progress), 0f)
    }
}
