package com.click.lightmemo.domain

import java.time.LocalDate

/** Shared calendar rules for both Compose and launcher renderers. */
object RecordingHeatmap {
    fun intensity(value: Double, target: Float): Float {
        val ratio = (value / target.coerceAtLeast(1f)).coerceIn(0.0, 1.25)
        return when {
            ratio <= 0.0 -> 0.06f
            ratio < 0.2 -> 0.20f
            ratio < 0.4 -> 0.34f
            ratio < 0.6 -> 0.48f
            ratio < 0.8 -> 0.64f
            ratio < 1.0 -> 0.80f
            else -> 0.96f
        }
    }

    fun streak(recordedDays: Set<Long>, today: LocalDate): Int {
        var day = today.toEpochDay()
        if (day !in recordedDays) day--
        var count = 0
        while (day in recordedDays) { count++; day-- }
        return count
    }

    fun days(today: LocalDate): List<Long> {
        val monday = today.minusDays(today.dayOfWeek.value - 1L).minusWeeks(4)
        return List(35) { monday.plusDays(it.toLong()).toEpochDay() }
    }
}
