package com.click.lightmemo.domain

import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class RecordingHeatmapTest {
    private val today = LocalDate.of(2026, 1, 1)
    @Test fun unfinishedTodayPreservesYesterdayStreakAcrossYearBoundary() {
        val days = (1L..10L).map { today.minusDays(it).toEpochDay() }.toSet()
        assertEquals(10, RecordingHeatmap.streak(days, today))
        assertEquals(11, RecordingHeatmap.streak(days + today.toEpochDay(), today))
        assertEquals(0, RecordingHeatmap.streak(days - today.minusDays(1).toEpochDay(), today))
    }
    @Test fun futureRecordsDoNotCreateStreakAndEmptyHistoryIsZero() {
        assertEquals(0, RecordingHeatmap.streak(setOf(today.plusDays(1).toEpochDay()), today))
        assertEquals(0, RecordingHeatmap.streak(emptySet(), today))
    }
    @Test fun fiveWeekGridStartsMondayAndIncludesCurrentWeek() {
        val days = RecordingHeatmap.days(today)
        assertEquals(35, days.size)
        assertEquals(1, LocalDate.ofEpochDay(days.first()).dayOfWeek.value)
        assertTrue(today.toEpochDay() in days)
        assertEquals(7, LocalDate.ofEpochDay(days.last()).dayOfWeek.value)
    }
}
